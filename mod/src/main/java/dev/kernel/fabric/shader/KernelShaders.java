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
    private static boolean handDepth, invalidProjection;
    private KernelShaders() {}

    public static Path directory() { return DIRECTORY; }
    public static List<String> installed() { return installed; }
    public static String active() { return active; }
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
            else prepare(generation, saved, false);
        });
    }
    public static void refresh() { start("Reading installed packs", generation -> { refreshFiles(); finish(generation, "Drop a shader ZIP here or browse Modrinth"); }); }
    public static void select(String filename) { start("Preparing " + filename, generation -> prepare(generation, filename, true)); }
    public static void disable() { start("Disabling shaders", generation -> PENDING.set(new Request(generation, null, true))); }
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
    private static void prepare(long generation, String filename, boolean persist) throws IOException {
        new ShaderConfig(filename);
        Path path = DIRECTORY.resolve(filename);
        if (!Files.isRegularFile(path)) throw new IOException("Selected shader pack is missing: " + filename);
        PreparedShaderPack prepared = PreparedShaderPack.read(path);
        if (generation == GENERATION.get()) {
            message = "Compiling " + filename; PENDING.set(new Request(generation, prepared, persist)); REVISION.incrementAndGet();
        }
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
        Object world = Minecraft.getInstance().level;
        if (historyWorld != world) {
            if (pipeline != null) pipeline.resetHistory();
            historyWorld = world;
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
        if (pipeline == null || closed || invalidProjection) return;
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
