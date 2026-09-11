package dev.kernel.fabric.verification;

import dev.kernel.fabric.shader.*;
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
    static void frame(Minecraft minecraft, long ready) {
        if (stage == 20 || advancing) return;
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
            ShaderGlChecks.run();
            ShaderMultipleTargetsChecks.run();
            GuiProbe.click(GuiProbe.find(GuiProbe.screen(minecraft), "kernel.settings.open"));
            GuiProbe.click(GuiProbe.find(GuiProbe.screen(minecraft), "kernel.video.tab.shaders"));
            if (!(GuiProbe.screen(minecraft) instanceof ShaderScreen)) throw new AssertionError("Shaders tab did not open");
            next(1); return;
        }
        if (stage == 1 && !KernelShaders.busy()) {
            Path source = minecraft.gameDirectory.toPath().resolve("original-probe.zip");
            zip(source, "#version 120\nvoid main() { gl_FragColor = vec4(0.25, 0.5, 0.75, 1.0); }");
            GuiProbe.screen(minecraft).onFilesDrop(List.of(source)); next(2); return;
        }
        if (stage == 2 && !KernelShaders.busy()) {
            if (KernelShaders.failed() || !KernelShaders.installed().contains("original-probe.zip")) throw new AssertionError("Drag/drop import failed: " + KernelShaders.message());
            KernelShaders.select("original-probe.zip"); next(3); return;
        }
        if (stage == 3 && !KernelShaders.busy()) {
            if (!KernelShaders.active().equals("original-probe.zip")) throw new AssertionError("Shader activation failed: " + KernelShaders.message());
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
            if (verifiedWorldFrames == 0) return;
            Path bad = KernelShaders.directory().resolve("unsupported-probe.zip");
            zip(bad, "#version 120\nuniform sampler2D shadowtex0; void main() { gl_FragColor = texture2D(shadowtex0,vec2(0.5)); }");
            KernelShaders.select("unsupported-probe.zip"); next(7); return;
        }
        if (stage == 7 && !KernelShaders.busy()) {
            if (!KernelShaders.failed() || !KernelShaders.active().equals("original-probe.zip")) throw new AssertionError("Failed compilation replaced the working shader");
            if (verifiedWorldFrames == 0) return;
            captured = false;
            GuiProbe.capture(minecraft, "kernel-shader-world.png", ignored -> captured = true); next(8); return;
        }
        if (stage == 8 && captured) { KernelShaders.disable(); next(9); return; }
        if (stage == 9 && !KernelShaders.busy()) {
            if (!KernelShaders.active().isEmpty() || !ShaderConfig.load(minecraft.gameDirectory.toPath().resolve("config/kernel-shaders.properties")).selected().isEmpty()) throw new AssertionError("Shaders off did not apply/save");
            Files.writeString(minecraft.gameDirectory.toPath().resolve("shader-probe-complete.json"), "{\"pixels\":true,\"resize\":true,\"glState\":true,\"dragDrop\":true,\"settings\":true,\"worldPass\":true,\"failureRecovery\":true}\n");
            System.out.println("Kernel shader probe passed: native GUI, import, selection, real world rendering, failed-pack recovery and disable.");
            stage = 20; minecraft.execute(minecraft::stop);
        }
    }
    private static void zip(Path path, String fragment) throws Exception {
        try (var output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("shaders/final.fsh")); output.write(fragment.getBytes(java.nio.charset.StandardCharsets.UTF_8)); output.closeEntry();
        }
    }
    private static void beginWorld(Minecraft minecraft) {
        GuiProbe.screen(minecraft).onClose(); GuiProbe.screen(minecraft).onClose();
        GuiProbe.click(GuiProbe.find(GuiProbe.screen(minecraft), "menu.singleplayer")); next(5);
    }
    private static void assertWorldPixel(Minecraft minecraft) {
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
        } finally {
            GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, read); GL33C.glDeleteFramebuffers(fbo);
            GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, packBuffer);
            for (int i = 0; i < 3; i++) GL33C.glPixelStorei(stores[i], previous[i]);
        }
    }
    public static void verifyWorldFrame() {
        if (!Boolean.getBoolean("kernel.guiProbe.shaders") || (stage != 6 && stage != 7)) return;
        // The native HUD vignette intentionally darkens the final image afterwards; inspect before that HUD pass.
        assertWorldPixel(Minecraft.getInstance()); verifiedWorldFrames++;
    }
    private static void next(int value) { stage = value; changedAt = System.nanoTime(); verifiedWorldFrames = 0; }
}
