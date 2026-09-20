package dev.kernel.fabric.verification;

import dev.kernel.fabric.shader.*;
import dev.kernel.fabric.shader.KernelWorldShaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import java.nio.file.*;
import java.util.List;
import java.util.zip.*;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;
//? if >=1.21.5 {
import com.mojang.blaze3d.opengl.GlTexture;
//? }

public final class ShaderProbe {
    private static int stage;
    private static long changedAt, started = System.nanoTime();
    private static boolean advancing;
    private static volatile boolean captured;
    private static int verifiedWorldFrames;
    /** The last world-stage pixel sampled inside a frame, or null while no such frame has been drawn. */
    private static volatile int[][] worldStageSample;
    /** The samples the world-stage assertion is made about, kept across the capture they are paired with. */
    private static int[][] worldStageResult;
    /** The world stage is measured over a grid of this many points per axis, not at one pixel. */
    private static final int GRID = 5;
    private static Path probeSource;
    private static String fixtureName;
    static void frame(Minecraft minecraft, long ready) {
        if (stage == 20 || advancing) return;
        // Hold the camera on the ground for every frame the world stage is measured over, not just the
        // one that selected the pack.
        if (stage >= 10 && stage <= 12) aimAtTheGround(minecraft);
        advancing = true;
        try { advance(minecraft, ready); }
        catch (Exception failure) { stage = 20; throw new AssertionError("Shader probe failed", failure); }
        catch (Error failure) { stage = 20; throw failure; }
        finally { advancing = false; }
    }
    private static void advance(Minecraft minecraft, long ready) throws Exception {
        if (System.nanoTime() - started > 240_000_000_000L) throw new AssertionError("Shader probe timed out at " + stage);
        if (ready < 0) return;
        //? if >=26.2 {
        if (minecraft.gui.overlay() != null) return;
        //? } else {
        /*if (minecraft.getOverlay() != null) return;
        *///? }
        long elapsed = System.nanoTime() - changedAt;
        if (stage == 0 && !KernelShaders.busy()) {
            SectionBufferGlChecks.run();
            ShaderDepthGlChecks.run();
            ShaderProjectionGlChecks.run();
            ShaderCameraGlChecks.run();
            dev.kernel.fabric.shader.ShaderWorldGlChecks.run();
            dev.kernel.fabric.shader.ShaderTextureGlChecks.run();
            ShaderGlChecks.run();
            ShaderMultipleTargetsChecks.run();
            dev.kernel.fabric.shader.ShaderBufferGlChecks.run();
            GuiProbe.click(GuiProbe.find(GuiProbe.screen(minecraft), "kernel.settings.open"));
            GuiProbe.click(GuiProbe.find(GuiProbe.screen(minecraft), "kernel.video.tab.shaders"));
            if (!(GuiProbe.screen(minecraft) instanceof ShaderScreen)) throw new AssertionError("Shaders tab did not open");
            next(1); return;
        }
        if (stage == 1 && !KernelShaders.busy()) {
            Path source = Files.createTempFile(minecraft.gameDirectory.toPath(), "kernel-shader-probe-", ".zip");
            probeSource = source; fixtureName = source.getFileName().toString();
            dev.kernel.fabric.shader.ShaderTextureGlChecks.writeImportFixture(source);
            GuiProbe.screen(minecraft).onFilesDrop(List.of(source)); next(2); return;
        }
        if (stage == 2 && !KernelShaders.busy()) {
            if (KernelShaders.failed() || !KernelShaders.installed().contains(fixtureName)) throw new AssertionError("Drag/drop import failed: " + KernelShaders.message());
            if (Files.mismatch(probeSource, KernelShaders.directory().resolve(fixtureName)) != -1)
                throw new AssertionError("Imported shader differs from the current probe fixture");
            KernelShaders.select(fixtureName); next(3); return;
        }
        if (stage == 3 && !KernelShaders.busy()) {
            if (!KernelShaders.active().equals(fixtureName)) throw new AssertionError("Shader activation failed: " + KernelShaders.message());
            if (!ShaderConfig.load(minecraft.gameDirectory.toPath().resolve("config/kernel-shaders.properties")).selected().equals(KernelShaders.active())) throw new AssertionError("Shader selection did not persist");
            if (elapsed < 600_000_000L) return;
            GuiProbe.capture(minecraft, "kernel-shaders.png", ignored -> captured = true); next(4); return;
        }
        if (stage == 4 && captured) {
            if (Boolean.getBoolean("kernel.guiProbe.liveModrinth")) {
                GuiProbe.click(GuiProbe.find(GuiProbe.screen(minecraft), "kernel.shaders.modrinth")); next(40); return;
            }
            beginWorld(minecraft); return;
        }
        if (stage == 40) {
            boolean results = GuiProbe.screen(minecraft).children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .anyMatch(button -> button.getMessage().getString().equals(dev.kernel.fabric.config.KernelTranslations.text("kernel.shaders.install").getString()));
            if (!results) return;
            captured = false; GuiProbe.capture(minecraft, "kernel-modrinth.png", ignored -> captured = true); next(41); return;
        }
        if (stage == 41 && captured) {
            System.out.println("Kernel Modrinth browser live query passed for " + KernelShaders.gameVersion());
            beginWorld(minecraft); return;
        }
        if (stage == 5) {
            if (GuiProbe.screen(minecraft) instanceof SelectWorldScreen) { GuiProbe.click(GuiProbe.find(GuiProbe.screen(minecraft), "selectWorld.create")); return; }
            if (GuiProbe.screen(minecraft) instanceof CreateWorldScreen create) {
                var state = create.getUiState(); state.setName("Kernel Shader Probe"); state.setSeed("2718281828");
                state.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                state.setWorldType(state.getNormalPresetList().stream().filter(type -> type.preset() != null && type.preset().is(WorldPresets.FLAT)).findFirst().orElseThrow());
                GuiProbe.click(GuiProbe.find(create, "selectWorld.create")); next(6);
            }
            return;
        }
        if (stage == 6 && minecraft.level != null && minecraft.player != null && GuiProbe.screen(minecraft) == null && elapsed > 3_000_000_000L) {
            if (verifiedWorldFrames == 0 || !ShaderWorldGlChecks.weatherObserved()) return;
            if (!ShaderDepthWorldChecks.advance(minecraft)) return;
            if (!ShaderProjectionWorldChecks.advance(minecraft)) return;
            if (!ShaderCameraWorldChecks.advance(minecraft)) return;
            Path bad = KernelShaders.directory().resolve("unsupported-probe.zip");
            zip(bad, "#version 120\nuniform sampler2D shadowtex0; void main() { gl_FragColor = texture2D(shadowtex0,vec2(0.5)); }");
            KernelShaders.select("unsupported-probe.zip"); next(7); return;
        }
        if (stage == 7 && !KernelShaders.busy()) {
            if (!KernelShaders.failed() || !KernelShaders.active().equals(fixtureName)) throw new AssertionError("Failed compilation replaced the working shader");
            if (verifiedWorldFrames == 0) return;
            captured = false;
            GuiProbe.capture(minecraft, "kernel-shader-world.png", ignored -> captured = true); next(8); return;
        }
        if (stage == 8 && captured) {
            // The world stage is opt-in while it is incomplete, so by default this probe
            // finishes exactly as it did before it existed.
            if (!Boolean.getBoolean(dev.kernel.fabric.shader.pack.PreparedShaderPack.WORLD_STAGE_PROPERTY)) {
                KernelShaders.disable(); next(9); return;
            }
            // A world program that ignores every input and writes one colour, so the pixel read back is
            // evidence the pack's own program ran, not Minecraft's.
            Path world = KernelShaders.directory().resolve("world-stage-probe.zip");
            zip(world, java.util.Map.of(
                // Reads the identity the pack's own block.properties gave the block, and the offset to
                // that block's centre, so the pixel is evidence of the whole chain: the map was parsed,
                // resolved against the registries, recorded per block while meshing, written into an
                // extended vertex format, and bound to the name the pack declared.
                "shaders/gbuffers_terrain.vsh", """
                    #version 120
                    attribute vec2 mc_Entity;
                    attribute vec3 at_midBlock;
                    varying float identity;
                    varying vec3 midBlock;
                    void main() { gl_Position = ftransform(); identity = mc_Entity.x; midBlock = at_midBlock; }
                    """,
                "shaders/gbuffers_terrain.fsh", """
                    #version 120
                    varying float identity;
                    varying vec3 midBlock;
                    void main() {
                        // The superflat's surface is grass_block, which this pack's block.properties
                        // gives the identity 2; a vertex sits within its own block, so the offset to
                        // that block's centre cannot exceed a block in sixty-fourths.
                        bool named = identity > 1.5 && identity < 2.5;
                        bool inside = all(lessThan(abs(midBlock), vec3(64.5)));
                        gl_FragData[0] = named && inside ? vec4(0.0, 1.0, 0.0, 1.0) : vec4(1.0, 0.0, 0.0, 1.0);
                    }
                    """,
                "shaders/final.fsh", """
                    #version 120
                    uniform sampler2D colortex0;
                    varying vec2 texcoord;
                    void main() { gl_FragColor = texture2D(colortex0, texcoord); }
                    """,
                // Named against the real registries, so the identity table is resolved, not just parsed.
                "shaders/block.properties", """
                    block.1=minecraft:stone
                    block.2=minecraft:grass_block:snowy=false
                    block.3=somemod:absent_block
                    """));
            aimAtTheGround(minecraft);
            KernelShaders.select("world-stage-probe.zip"); next(10); return;
        }
        if (stage == 10 && !KernelShaders.busy()) {
            if (KernelShaders.failed()) throw new AssertionError("World-stage pack was refused: " + KernelShaders.message());
            if (!KernelWorldShaders.replaced().containsKey("terrain"))
                throw new AssertionError("Terrain was not substituted: " + KernelWorldShaders.replaced());
            verifyBlockIdentities();
            next(11); return;
        }
        if (stage == 11 && elapsed > 2_000_000_000L) {
            if (KernelWorldShaders.substituted() == 0)
                throw new AssertionError("No core shader stage was compiled from the pack: " + KernelWorldShaders.replaced());
            System.out.println("Kernel world stage: " + KernelWorldShaders.substituted() + " substituted stages, replacing " + KernelWorldShaders.replaced());
            worldStageResult = worldStageSample;
            if (worldStageResult == null) throw new AssertionError("No world frame was drawn while the pack was active");
            System.out.println("Kernel world-stage pixels: " + describe(worldStageResult));
            // Keep the frame the assertion is about. When the pixel is not the one the pack asked for,
            // the image says whether the pack's program drew the wrong thing or never drew at all.
            captured = false;
            GuiProbe.capture(minecraft, "kernel-world-stage.png", ignored -> captured = true); next(12); return;
        }
        if (stage == 12 && captured) {
            // The hand and anything else in view are drawn by programs this pack does not replace, so
            // require most of the frame rather than all of it.
            if (packDrawn(worldStageResult) * 5 < worldStageResult.length * 3)
                throw new AssertionError("Terrain was not drawn by the pack's own program: " + describe(worldStageResult));
            Files.deleteIfExists(KernelShaders.directory().resolve("world-stage-probe.zip"));
            KernelShaders.disable(); next(9); return;
        }
        if (stage == 9 && !KernelShaders.busy()) {
            ChunkUniformProbe.verifyComplete();
            SectionBufferGlChecks.verifyWorld(minecraft);
            if (!KernelShaders.active().isEmpty() || !ShaderConfig.load(minecraft.gameDirectory.toPath().resolve("config/kernel-shaders.properties")).selected().isEmpty()) throw new AssertionError("Shaders off did not apply/save");
            Files.writeString(minecraft.gameDirectory.toPath().resolve("shader-probe-complete.json"), "{\"pixels\":true,\"resize\":true,\"glState\":true,\"dragDrop\":true,\"settings\":true,\"worldPass\":true,\"failureRecovery\":true}\n");
            System.out.println("Kernel shader probe passed: native GUI, import, selection, real world rendering, failed-pack recovery and disable.");
            Files.deleteIfExists(probeSource);
            Files.deleteIfExists(KernelShaders.directory().resolve(fixtureName));
            stage = 20; minecraft.execute(minecraft::stop);
        }
    }
    /**
     * Points the camera straight down at the ground, for every frame the world stage is measured over.
     *
     * <p>Applied repeatedly rather than once. An earlier stage cycles the camera through all three of
     * its types, and the player's own rotation does not stay where a single assignment put it, so a
     * frame sampled seconds later was finding the sky or the inside of the player's model.
     */
    private static void aimAtTheGround(Minecraft minecraft) {
        minecraft.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        if (minecraft.player == null) return;
        minecraft.player.setXRot(90.0f); minecraft.player.xRotO = 90.0f;
    }

    /** Checks the pack's block.properties against the real registries, not against parsed rules. */
    private static void verifyBlockIdentities() {
        if (!dev.kernel.fabric.shader.KernelBlockIdentities.active())
            throw new AssertionError("The pack named blocks but no identity table was built");
        var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        var grass = net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState();
        var dirt = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
        int identity = dev.kernel.fabric.shader.KernelBlockIdentities.identity(stone);
        if (identity != 1) throw new AssertionError("Stone was not given the identity the pack declared: " + identity);
        // The default grass block is not snowy, which is the state the pack constrained its rule to.
        int grassIdentity = dev.kernel.fabric.shader.KernelBlockIdentities.identity(grass);
        if (grassIdentity != 2) throw new AssertionError("A state-constrained rule did not match: " + grassIdentity);
        int unnamed = dev.kernel.fabric.shader.KernelBlockIdentities.identity(dirt);
        if (unnamed != 0) throw new AssertionError("A block the pack never named was given an identity: " + unnamed);
        System.out.println("Kernel block identities: stone=1, grass_block[snowy=false]=2, unnamed=0, "
            + "and an entry naming an absent mod block was ignored");
        verifyExtendedFormat();
    }

    /** Checks that the attributes the pack declared were taken up and the terrain format carries them. */
    private static void verifyExtendedFormat() {
        var demanded = KernelWorldShaders.demanded();
        for (var attribute : new dev.kernel.fabric.shader.pack.ShaderWorldAttributes[]{
                dev.kernel.fabric.shader.pack.ShaderWorldAttributes.MC_ENTITY,
                dev.kernel.fabric.shader.pack.ShaderWorldAttributes.AT_MID_BLOCK})
            if (!demanded.contains(attribute))
                throw new AssertionError("The pack declared " + attribute.glslName() + " but it was not demanded: " + demanded);
        var vanilla = com.mojang.blaze3d.vertex.DefaultVertexFormat.BLOCK;
        var terrain = KernelWorldShaders.vertexFormat(vanilla);
        if (terrain == vanilla || terrain.getVertexSize() <= vanilla.getVertexSize())
            throw new AssertionError("The terrain format was not extended: " + terrain.getVertexSize() + " bytes");
        // A pipeline drawing with any other format must be left exactly as it was.
        var other = com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION;
        if (KernelWorldShaders.vertexFormat(other) != other)
            throw new AssertionError("A format that is not Minecraft's block format was changed");
        System.out.println("Kernel terrain vertex format: " + vanilla.getVertexSize() + " bytes extended to "
            + terrain.getVertexSize() + " for " + demanded);
    }

    /** How many sampled points the pack's own program wrote. */
    private static int packDrawn(int[][] samples) {
        int drawn = 0;
        for (int[] pixel : samples) if (pixel[1] >= 250 && pixel[0] <= 5 && pixel[2] <= 5) drawn++;
        return drawn;
    }
    private static String describe(int[][] samples) {
        var text = new StringBuilder(packDrawn(samples) + " of " + samples.length + " points:");
        for (int[] pixel : samples) text.append(' ').append(pixel[0]).append(',').append(pixel[1]).append(',').append(pixel[2]);
        return text.toString();
    }
    private static void zip(Path path, String fragment) throws Exception {
        zip(path, java.util.Map.of("shaders/final.fsh", fragment));
    }
    private static void zip(Path path, java.util.Map<String, String> files) throws Exception {
        try (var output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var entry : files.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }

    /**
     * Reads the pixel the pack's own terrain program should have written.
     *
     * <p>Sampled from the main render target's colour texture inside the frame, the way every other
     * pixel check here is. Once a frame has been presented the back buffer's contents are undefined, so
     * reading the default framebuffer from the tick path measures whatever the driver left behind rather
     * than the world that was drawn.
     */
    private static int[][] worldStagePixel(Minecraft minecraft) {
        //? if >=26.2 {
        var target = minecraft.gameRenderer.mainRenderTarget();
        //? } else {
        /*var target = minecraft.getMainRenderTarget();
        *///? }
        //? if >=1.21.5 {
        int texture = ((GlTexture) target.getColorTexture()).glId();
        //? } else {
        /*int texture = target.getColorTextureId();
        *///? }
        int read = GL33C.glGetInteger(GL33C.GL_READ_FRAMEBUFFER_BINDING), fbo = GL33C.glGenFramebuffers();
        int[] stores = {GL33C.GL_PACK_ROW_LENGTH, GL33C.GL_PACK_SKIP_ROWS, GL33C.GL_PACK_SKIP_PIXELS};
        int[] previous = new int[3];
        for (int i = 0; i < 3; i++) { previous[i] = GL33C.glGetInteger(stores[i]); GL33C.glPixelStorei(stores[i], 0); }
        int packBuffer = GL33C.glGetInteger(GL33C.GL_PIXEL_PACK_BUFFER_BINDING);
        GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, 0);
        try (var stack = MemoryStack.stackPush()) {
            GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, fbo);
            GL33C.glFramebufferTexture2D(GL33C.GL_READ_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0, GL33C.GL_TEXTURE_2D, texture, 0);
            GL33C.glReadBuffer(GL33C.GL_COLOR_ATTACHMENT0);
            var pixel = stack.malloc(4);
            // Measure the frame rather than a pixel. One pixel of the pack's colour could be a
            // coincidence, and one pixel of anything else could be the hand or an entity in view.
            int[][] samples = new int[GRID * GRID][];
            for (int point = 0; point < samples.length; point++) {
                int x = target.width * (point % GRID + 1) / (GRID + 1), y = target.height * (point / GRID + 1) / (GRID + 1);
                GL33C.glReadPixels(x, y, 1, 1, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, pixel);
                samples[point] = new int[]{pixel.get(0) & 255, pixel.get(1) & 255, pixel.get(2) & 255};
            }
            return samples;
        } finally {
            GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, read); GL33C.glDeleteFramebuffers(fbo);
            GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, packBuffer);
            for (int i = 0; i < 3; i++) GL33C.glPixelStorei(stores[i], previous[i]);
        }
    }
    private static void beginWorld(Minecraft minecraft) {
        ShaderDepthWorldChecks.prepare(minecraft);
        ShaderProjectionWorldChecks.prepare(minecraft);
        GuiProbe.screen(minecraft).onClose(); GuiProbe.screen(minecraft).onClose();
        GuiProbe.click(GuiProbe.find(GuiProbe.screen(minecraft), "menu.singleplayer")); next(5);
    }
    private static void assertWorldPixel(Minecraft minecraft, net.minecraft.client.DeltaTracker deltaTracker) {
        //? if >=26.2 {
        var target = minecraft.gameRenderer.mainRenderTarget();
        //? } else {
        /*var target = minecraft.getMainRenderTarget();
        *///? }
        //? if >=1.21.5 {
        int texture = ((GlTexture) target.getColorTexture()).glId();
        //? } else {
        /*int texture = target.getColorTextureId();
        *///? }
        int read = GL33C.glGetInteger(GL33C.GL_READ_FRAMEBUFFER_BINDING), fbo = GL33C.glGenFramebuffers();
        int[] stores = {GL33C.GL_PACK_ROW_LENGTH, GL33C.GL_PACK_SKIP_ROWS, GL33C.GL_PACK_SKIP_PIXELS};
        int[] previous = new int[3];
        for (int i = 0; i < 3; i++) { previous[i] = GL33C.glGetInteger(stores[i]); GL33C.glPixelStorei(stores[i], 0); }
        int packBuffer = GL33C.glGetInteger(GL33C.GL_PIXEL_PACK_BUFFER_BINDING);
        GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, 0);
        try (var stack = MemoryStack.stackPush()) {
            GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, fbo);
            GL33C.glFramebufferTexture2D(GL33C.GL_READ_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0, GL33C.GL_TEXTURE_2D, texture, 0);
            GL33C.glReadBuffer(GL33C.GL_COLOR_ATTACHMENT0);
            var pixel = stack.malloc(4); GL33C.glReadPixels(target.width / 4, target.height / 2, 1, 1, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, pixel);
            int[] expected = {64, 128, 191, 255};
            for (int i = 0; i < 4; i++) if (Math.abs((pixel.get(i) & 255) - expected[i]) > 1) throw new AssertionError("World shader pass did not produce expected pixel: channel " + i + "=" + (pixel.get(i) & 255));
            dev.kernel.fabric.shader.ShaderWorldGlChecks.verifyWorldPixels(minecraft, deltaTracker, target.height);
            ShaderDepthWorldChecks.verifyOutput(target.width, target.height);
            ShaderProjectionWorldChecks.verifyOutput(target.width, target.height);
            ShaderCameraWorldChecks.verifyOutput(target.width, target.height);
        } finally {
            GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, read); GL33C.glDeleteFramebuffers(fbo);
            GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, packBuffer);
            for (int i = 0; i < 3; i++) GL33C.glPixelStorei(stores[i], previous[i]);
        }
    }
    public static void verifyWorldFrame(net.minecraft.client.DeltaTracker deltaTracker) {
        if (!Boolean.getBoolean("kernel.guiProbe.shaders")) return;
        // The world stage is sampled from this same in-frame hook, because a pixel is only readable
        // while the frame that drew it is still the target being rendered into.
        if (stage == 11) { worldStageSample = worldStagePixel(Minecraft.getInstance()); verifiedWorldFrames++; return; }
        if (stage != 6 && stage != 7) return;
        if (ShaderProjectionWorldChecks.skipInvalidFrame() || ShaderCameraWorldChecks.skipInvalidFrame()) return;
        // The native HUD vignette intentionally darkens the final image afterwards; inspect before that HUD pass.
        assertWorldPixel(Minecraft.getInstance(), deltaTracker);
        if (stage == 6 && verifiedWorldFrames == 0) ShaderWorldGlChecks.beginLiveWeatherSample(Minecraft.getInstance());
        verifiedWorldFrames++;
    }
    public static boolean observingShaderWorld() { return Boolean.getBoolean("kernel.guiProbe.shaders") && stage == 6; }
    private static void next(int value) { stage = value; changedAt = System.nanoTime(); verifiedWorldFrames = 0; worldStageSample = null; }
}
