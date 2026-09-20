package dev.kernel.fabric.shader;

import dev.kernel.fabric.shader.pack.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.LoggerFactory;
//? if >=1.21.5 {
import com.mojang.blaze3d.opengl.GlTexture;
//? }

/** Disk/network work is separate from render-thread compilation and GPU ownership. Settings never auto-open. */
public final class KernelShaders {
    private record Request(long generation, PreparedShaderPack pack, boolean persist) {}
    private static final Path DIRECTORY = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("kernel-shaders.properties");
    private static final Path OPTIONS = FabricLoader.getInstance().getConfigDir().resolve("kernel-shaders");
    private static final ShaderHttp HTTP = new ShaderHttp("0.1.0");
    private static final ModrinthShaders MODRINTH = new ModrinthShaders(HTTP::json);
    private static final ShaderPackInstaller INSTALLER = new ShaderPackInstaller(DIRECTORY, HTTP);
    private static final java.util.concurrent.ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Kernel shader preparation"); thread.setDaemon(true); return thread;
    });
    private static final AtomicReference<Request> PENDING = new AtomicReference<>();
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final AtomicLong REVISION = new AtomicLong();
    private static volatile List<String> installed = List.of();
    private static volatile String active = "", message = "", error = "";
    private static volatile boolean busy, closed;
    private static boolean initialized;
    private static volatile Future<?> operation;
    private static ShaderPipeline pipeline;
    private static Object historyWorld;
    private static boolean handDepth, invalidProjection, invalidView;
    private static volatile List<ShaderOption> options = List.of();
    private static volatile ShaderOptionConfig optionValues = ShaderOptionConfig.empty();
    private static volatile ShaderProperties properties = ShaderProperties.empty();
    /** The dimension folder the active pipeline was prepared for, so a dimension change can rebuild it. */
    private static volatile String preparedDimension = PreparedShaderPack.OVERWORLD;
    private KernelShaders() {}

    public static Path directory() { return DIRECTORY; }
    public static List<String> installed() { return installed; }
    public static String active() { return active; }
    /** The options the active pack declares, ordered the way the pack lays them out. */
    public static List<ShaderOption> options() { return options; }
    public static ShaderProperties properties() { return properties; }
    /** The value in force for one option: the stored value when the pack still accepts it, else its default. */
    public static String optionValue(ShaderOption option) {
        String stored = optionValues.values().get(option.name());
        return stored != null && option.accepts(stored) ? stored : option.defaultValue();
    }
    public static boolean optionChanged(ShaderOption option) { return !optionValue(option).equals(option.defaultValue()); }
    public static String message() { return error.isEmpty() ? message : error; }
    public static boolean failed() { return !error.isEmpty(); }
    public static boolean busy() { return busy; }
    public static long revision() { return REVISION.get(); }
    public static String gameVersion() { return FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString(); }
    public static ModrinthShaders api() { return MODRINTH; }

    public static synchronized void initialize() {
        if (initialized) return; initialized = true;
        start("Reading shader settings", generation -> {
            refreshFiles();
            String saved = ShaderConfig.load(CONFIG).selected();
            if (saved.isEmpty()) finish(generation, "Shaders are off");
            else prepare(generation, saved, PreparedShaderPack.OVERWORLD, false);
        });
    }
    public static void refresh() { start("Reading installed packs", generation -> { refreshFiles(); finish(generation, "Drop a shader ZIP here or browse Modrinth"); }); }
    public static void select(String filename) {
        String dimension = currentDimension();
        start("Preparing " + filename, generation -> prepare(generation, filename, dimension, true));
    }
    public static void disable() { start("Disabling shaders", generation -> PENDING.set(new Request(generation, null, true))); }

    /**
     * Changes one option of the active pack and rebuilds it.
     *
     * <p>The value is saved first: a pack that no longer compiles with a chosen value must still remember
     * the choice, so the player can change it again instead of losing the whole selection.
     */
    public static void setOption(String name, String value) {
        String pack = active;
        if (pack.isEmpty()) return;
        String dimension = preparedDimension;
        ShaderOptionConfig updated = optionValues.with(name, value);
        start("Applying " + name, generation -> {
            optionValues = updated;
            updated.save(ShaderOptionConfig.file(OPTIONS, pack));
            prepare(generation, pack, dimension, false);
        });
    }

    /** Restores every option of the active pack to the value the pack itself ships. */
    public static void resetOptions() {
        String pack = active;
        if (pack.isEmpty() || optionValues.values().isEmpty()) return;
        String dimension = preparedDimension;
        start("Restoring pack defaults", generation -> {
            optionValues = ShaderOptionConfig.empty();
            optionValues.save(ShaderOptionConfig.file(OPTIONS, pack));
            prepare(generation, pack, dimension, false);
        });
    }
    public static void importPacks(List<Path> files) {
        if (files.isEmpty()) return;
        if (files.size() > 32) { fail("Drop at most 32 shader ZIP files at once"); return; }
        start("Importing shader packs", generation -> {
            for (Path path : List.copyOf(files)) INSTALLER.importZip(path, bytes -> progress(generation, "Importing " + path.getFileName() + " · " + bytes / 1024 + " KiB"));
            refreshFiles(); finish(generation, "Packs imported. Select one to enable it.");
        });
    }
    public static void install(ModrinthShaders.Project project) {
        start("Finding a release for " + project.title(), generation -> {
            var download = MODRINTH.latest(project.id(), gameVersion());
            Path path = INSTALLER.download(download, bytes -> progress(generation, "Downloading " + project.title() + " · " + bytes * 100 / download.bytes() + "%"));
            refreshFiles(); finish(generation, "Installed " + path.getFileName() + ". Select it to enable shaders.");
        });
    }
    @FunctionalInterface private interface Work { void run(long generation) throws Exception; }
    private static synchronized void start(String text, Work work) {
        if (closed || busy) return;
        long generation = GENERATION.incrementAndGet(); busy = true; message = text; error = ""; REVISION.incrementAndGet();
        operation = WORKER.submit(() -> {
            try { work.run(generation); }
            catch (Exception failure) {
                if (generation == GENERATION.get() && !closed) {
                    try { refreshFiles(); } catch (IOException ignored) { }
                    fail(failure.getMessage());
                    LoggerFactory.getLogger("Kernel").warn("Shader operation failed", failure);
                }
            }
        });
    }
    public static synchronized void cancel() {
        GENERATION.incrementAndGet(); PENDING.set(null);
        if (operation != null) operation.cancel(true);
        busy = false; message = "Shader operation cancelled"; error = ""; REVISION.incrementAndGet();
    }
    private static void prepare(long generation, String filename, String dimension, boolean persist) throws IOException {
        new ShaderConfig(filename);
        Path path = DIRECTORY.resolve(filename);
        if (!Files.isRegularFile(path)) throw new IOException("Selected shader pack is missing: " + filename);
        if (!filename.equals(active) || options.isEmpty()) optionValues = ShaderOptionConfig.load(ShaderOptionConfig.file(OPTIONS, filename));
        PreparedShaderPack prepared = PreparedShaderPack.read(path, dimension, optionValues.values());
        if (generation == GENERATION.get()) {
            message = "Compiling " + filename; PENDING.set(new Request(generation, prepared, persist)); REVISION.incrementAndGet();
        }
    }

    /**
     * Returns the pack folder for the dimension being rendered.
     *
     * <p>Only the render thread may call this, because it reads the live client world.
     */
    private static String currentDimension() {
        var level = Minecraft.getInstance().level;
        if (level == null) return PreparedShaderPack.OVERWORLD;
        //? if >=1.21.11 {
        String path = level.dimension().identifier().getPath();
        //? } else {
        /*String path = level.dimension().location().getPath();
        *///? }
        return PreparedShaderPack.dimensionFolder(path);
    }
    private static void refreshFiles() throws IOException {
        if (!Files.isDirectory(DIRECTORY)) { installed = List.of(); return; }
        try (var files = Files.list(DIRECTORY)) {
            installed = files.filter(Files::isRegularFile).map(path -> path.getFileName().toString())
                .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".zip")).sorted(String.CASE_INSENSITIVE_ORDER).limit(4096).toList();
        }
    }
    private static void progress(long generation, String text) { if (generation == GENERATION.get()) message = text; }
    private static void finish(long generation, String text) {
        if (generation == GENERATION.get()) { message = text; busy = false; REVISION.incrementAndGet(); }
    }
    private static void fail(String text) { error = text == null ? "Shader operation failed; see the game log" : text; busy = false; REVISION.incrementAndGet(); }

    /** Invoked from the native rendering thread before drawing. */
    public static void beginFrame() {
        // The backend is only safe to ask about on this thread. A backend that cannot host shader packs
        // releases any pipeline built before the device was known and then stays out of the frame.
        if (!ShaderBackend.supported()) {
            if (pipeline != null) { pipeline.close(); pipeline = null; active = ""; options = List.of(); }
            if (KernelWorldShaders.active()) {
                boolean extended = !KernelWorldShaders.demanded().isEmpty();
                KernelWorldShaders.adopt(null);
                KernelBlockIdentities.adopt(null);
                clearPipelineCache();
                if (extended) rebuildSections();
            }
            PENDING.set(null);
            return;
        }
        Object world = Minecraft.getInstance().level;
        if (historyWorld != world) {
            if (pipeline != null) pipeline.resetHistory();
            historyWorld = world;
            // A world loaded with different data packs rebuilds the block registry, and identities
            // resolved against the previous one would name whatever now occupies those ids.
            KernelBlockIdentities.refresh();
        }
        // A pack may replace whole programs per dimension, so entering one rebuilds the active pipeline.
        if (pipeline != null && !active.isEmpty() && !busy && !currentDimension().equals(preparedDimension)) {
            String pack = active, dimension = currentDimension();
            start("Preparing " + pack + " for this dimension", generation -> prepare(generation, pack, dimension, false));
        }
        Request request = PENDING.getAndSet(null);
        if (request == null || closed || request.generation != GENERATION.get()) return;
        try {
            if (request.pack != null) colorTexture(Minecraft.getInstance());
            ShaderPipeline replacement = request.pack == null ? null : new ShaderPipeline(request.pack);
            synchronized (KernelShaders.class) {
                if (request.generation != GENERATION.get() || closed) { if (replacement != null) replacement.close(); return; }
                ShaderPipeline old = pipeline; pipeline = replacement;
                active = request.pack == null ? "" : request.pack.filename();
                // Publish the pack description only once its programs have compiled, so a failed
                // compilation never leaves the settings screen describing a pipeline that is not running.
                options = request.pack == null ? List.of() : request.pack.options();
                properties = request.pack == null ? ShaderProperties.empty() : request.pack.properties();
                preparedDimension = request.pack == null ? PreparedShaderPack.OVERWORLD : request.pack.dimension();
                if (request.pack == null) optionValues = ShaderOptionConfig.empty();
                // Pipelines compiled from the previous source are cached by the backend, so adopting new
                // world programs without discarding them would keep drawing with the old ones.
                var previousAttributes = KernelWorldShaders.demanded();
                KernelWorldShaders.adopt(request.pack);
                // Resolve the pack's block identities against the registries now, so the chunk builder
                // never resolves anything while meshing.
                KernelBlockIdentities.adopt(request.pack == null ? null : request.pack.identifiers().blocks());
                clearPipelineCache();
                if (!previousAttributes.equals(KernelWorldShaders.demanded())) rebuildSections();
                if (old != null) old.close();
                String selected = active;
                if (request.persist) {
                    operation = WORKER.submit(() -> {
                        if (request.generation != GENERATION.get()) return;
                        try { new ShaderConfig(selected).save(CONFIG); finish(request.generation, selected.isEmpty() ? "Shaders are off" : "Active: " + selected); }
                        catch (IOException failure) { fail("Could not save the shader selection: " + failure.getMessage()); }
                    });
                } else finish(request.generation, "Active: " + selected);
            }
        } catch (IOException | RuntimeException failure) { fail(failure.getMessage()); LoggerFactory.getLogger("Kernel").warn("Shader compilation failed; retaining the previous pipeline", failure); }
    }
    public static void beginWorld() {
        handDepth = false;
        invalidProjection = false;
        invalidView = false;
        if (pipeline != null) pipeline.beginWorld();
    }
    public static void handPass() { handDepth = true; }
    public static void captureProjection(org.joml.Matrix4fc matrix) {
        if (pipeline == null || closed || !pipeline.needsProjection()) return;
        try {
            //? if >=26.2 {
            boolean zeroToOne = com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
            //? } elif >=26.1 {
            /*boolean zeroToOne = com.mojang.blaze3d.systems.RenderSystem.getDevice().isZZeroToOne();
            *///? } else {
            /*boolean zeroToOne = false;
            *///? }
            // Minecraft can briefly upload a nonfinite projection while entering a world.
            invalidProjection = !pipeline.captureProjection(matrix, reverseDepth(), zeroToOne);
        } catch (IOException | RuntimeException failure) { renderingFailed(failure); }
    }
    public static void captureView(org.joml.Matrix4fc matrix, net.minecraft.world.phys.Vec3 position) {
        if (pipeline == null || closed || !pipeline.needsView()) return;
        try { invalidView = !pipeline.captureView(matrix, position.x, position.y, position.z); }
        catch (IOException | RuntimeException failure) { renderingFailed(failure); }
    }
    public static void scheduleDepth(com.mojang.blaze3d.framegraph.FrameGraphBuilder graph,
        net.minecraft.client.renderer.LevelTargetBundle targets, boolean clouds) {
        ShaderPipeline selected = pipeline;
        if (selected == null || closed || !selected.needsDepth()) return;
        ShaderDepthPass.add(graph, targets, clouds, inputs -> {
            if (pipeline != selected || closed) return;
            try {
                var first = inputs.getFirst();
                int[] textures = new int[inputs.size()];
                for (int index = 0; index < inputs.size(); index++) {
                    var target = inputs.get(index);
                    if (target.width != first.width || target.height != first.height)
                        throw new IOException("Native world depth targets have different dimensions");
                    textures[index] = depthTexture(target);
                }
                selected.captureDepth(textures, first.width, first.height, reverseDepth(), false);
            } catch (IOException | RuntimeException failure) { renderingFailed(failure); }
        });
    }
    private static boolean reverseDepth() {
        //? if >=26.2 {
        return true;
        //? } else {
        /*return false;
        *///? }
    }
    private static int depthTexture(com.mojang.blaze3d.pipeline.RenderTarget target) throws IOException {
        //? if >=1.21.5 {
        if (target.getDepthTexture() instanceof GlTexture texture) return texture.glId();
        throw new IOException("Kernel shader depth requires a native OpenGL depth texture");
        //? } else {
        /*return target.getDepthTextureId();
        *///? }
    }
    public static void renderWorld(net.minecraft.client.DeltaTracker deltaTracker) {
        if (pipeline == null || closed || invalidProjection || invalidView) return;
        try {
            var minecraft = Minecraft.getInstance();
            //? if >=26.2 {
            var target = minecraft.gameRenderer.mainRenderTarget();
            //? } else {
            /*var target = minecraft.getMainRenderTarget();
            *///? }
            ShaderWorldData world = pipeline.needsWorldData() ? ShaderWorldCapture.capture(minecraft, deltaTracker) : null;
            if (pipeline.needsDepth() && handDepth)
                pipeline.captureDepth(new int[]{depthTexture(target)}, target.width, target.height, reverseDepth(), true);
            pipeline.render(colorTexture(minecraft), target.width, target.height, world);
        } catch (IOException | RuntimeException failure) { renderingFailed(failure); }
    }
    /**
     * Discards the backend's compiled pipelines so substituted world programs are recompiled.
     *
     * <p>Only the render thread may call this. A backend that cannot discard them is not a reason to
     * abandon the pack: the post-processing passes are unaffected, and the world simply keeps the
     * programs it already compiled.
     */
    /**
     * Rebuilds every chunk section after the vertex attributes a pack needs have changed.
     *
     * <p>A section is drawn with the vertex format it was built with, not with the one its pipeline
     * declares: the mesh carries its own layout and sets up the attribute pointers from that. So a
     * section meshed before a pack was adopted supplies nothing for the pack's extra attributes however
     * the pipeline is bound, and the program reads the zeroes GL substitutes for an attribute no buffer
     * provides. Discarding those meshes is what makes the pack's own inputs arrive.
     *
     * <p>This is expensive and deliberately rare: it runs when the set of attributes changes, which is
     * when a pack is adopted or dropped, not when one is merely recompiled for a dimension.
     */
    private static void rebuildSections() {
        //? if <=1.21.10 {
        /*var minecraft = Minecraft.getInstance();
        if (minecraft.levelRenderer != null) minecraft.levelRenderer.allChanged();
        *///? }
    }

    private static void clearPipelineCache() {
        //? if >=1.21.5 {
        try { com.mojang.blaze3d.systems.RenderSystem.getDevice().clearPipelineCache(); }
        catch (RuntimeException unavailable) {
            LoggerFactory.getLogger("Kernel").debug("Could not discard compiled pipelines", unavailable);
        }
        //? }
    }

    private static void renderingFailed(Exception failure) {
        if (pipeline != null) pipeline.close(); pipeline = null; active = "";
        fail("Shaders disabled after a rendering error: " + failure.getMessage());
        LoggerFactory.getLogger("Kernel").warn("Shader rendering failed; native rendering continues", failure);
    }
    private static int colorTexture(Minecraft minecraft) throws IOException {
        //? if >=26.2 {
        var target = minecraft.gameRenderer.mainRenderTarget();
        //? } else {
        /*var target = minecraft.getMainRenderTarget();
        *///? }
        //? if >=1.21.5 {
        if (target.getColorTexture() instanceof GlTexture texture) return texture.glId();
        throw new IOException("Kernel shader rendering currently requires the OpenGL graphics backend");
        //? } else {
        /*return target.getColorTextureId();
        *///? }
    }
    public static synchronized void close() {
        closed = true; cancel(); WORKER.shutdownNow();
        historyWorld = null;
        if (pipeline != null) { pipeline.close(); pipeline = null; }
    }
}
