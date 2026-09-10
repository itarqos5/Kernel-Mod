package dev.kernel.fabric.verification;

import dev.kernel.fabric.config.KernelSettingsScreen;
import dev.kernel.fabric.config.KernelTranslations;
import dev.kernel.fabric.frame.FrameSync;
import dev.kernel.fabric.frame.FrameSyncConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import java.nio.file.Files;

/** Uses an isolated, newly created flat world; never opens or changes a user's save. */
final class FrameSyncProbe {
    private static int stage;
    private static long changedAt, started = System.nanoTime();
    private static volatile boolean captured;
    private static boolean advancing;

    static void frame(Minecraft minecraft, long readyMillis) {
        if (stage == 9 || advancing) return;
        advancing = true;
        try { advance(minecraft, readyMillis); }
        catch (Exception exception) { stage = 9; throw new AssertionError("Frame Sync probe failed", exception); }
        catch (Error error) { stage = 9; throw error; }
        finally { advancing = false; }
    }

    private static void advance(Minecraft minecraft, long readyMillis) throws Exception {
        if (System.nanoTime() - started > 240_000_000_000L) throw new AssertionError("Frame Sync probe timed out at stage " + stage);
        if (readyMillis < 0) return;
        //? if >=26.2 {
        if (minecraft.gui.overlay() != null) return;
        //? } else {
        /*if (minecraft.getOverlay() != null) return;
        *///? }
        minecraft.getFramerateLimitTracker().onInputReceived();
        long elapsed = System.nanoTime() - changedAt;
        if (stage == 0) {
            minecraft.options.enableVsync().set(false);
            minecraft.options.framerateLimit().set(120);
            FrameSync.save(false);
            next(1); return;
        }
        if (stage == 1 && elapsed > 500_000_000L) {
            assertNativeSync(minecraft, false);
            FrameSync.save(true);
            next(2); return;
        }
        if (stage == 2 && elapsed > 500_000_000L) {
            assertNativeSync(minecraft, true);
            if (minecraft.options.enableVsync().get() || minecraft.options.framerateLimit().get() != 120) {
                throw new AssertionError("Frame Sync overwrote saved native display settings");
            }
            GuiProbe.click(GuiProbe.find(GuiProbe.screen(minecraft), "kernel.settings.open"));
            GuiProbe.click(frameButton(minecraft));
            GuiProbe.click(GuiProbe.find(GuiProbe.screen(minecraft), "gui.cancel"));
            if (!FrameSync.enabled()) throw new AssertionError("Cancel applied Frame Sync draft");
            GuiProbe.click(GuiProbe.find(GuiProbe.screen(minecraft), "kernel.settings.open"));
            GuiProbe.click(frameButton(minecraft));
            GuiProbe.click(GuiProbe.find(GuiProbe.screen(minecraft), "kernel.settings.apply"));
            if (FrameSync.enabled() || !(GuiProbe.screen(minecraft) instanceof KernelSettingsScreen)) throw new AssertionError("Apply did not keep the screen and apply Frame Sync");
            next(3); return;
        }
        if (stage == 3 && elapsed > 500_000_000L) {
            assertNativeSync(minecraft, false);
            GuiProbe.click(frameButton(minecraft));
            GuiProbe.click(GuiProbe.find(GuiProbe.screen(minecraft), "gui.done"));
            if (!FrameSyncConfig.load(minecraft.gameDirectory.toPath().resolve("config/kernel-display.properties")).enabled()) throw new AssertionError("Frame Sync did not persist");
            GuiProbe.click(GuiProbe.find(GuiProbe.screen(minecraft), "menu.singleplayer"));
            next(4); return;
        }
        if (stage == 4) {
            if (GuiProbe.screen(minecraft) instanceof SelectWorldScreen) {
                GuiProbe.click(GuiProbe.find(GuiProbe.screen(minecraft), "selectWorld.create")); return;
            }
            if (GuiProbe.screen(minecraft) instanceof CreateWorldScreen create) {
                var state = create.getUiState();
                state.setName("Kernel Frame Sync Probe"); state.setSeed("2718281828");
                state.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                state.setWorldType(state.getNormalPresetList().stream().filter(type -> type.preset() != null && type.preset().is(WorldPresets.FLAT)).findFirst().orElseThrow());
                GuiProbe.click(GuiProbe.find(create, "selectWorld.create")); next(5);
            }
            return;
        }
        if (stage == 5) {
            if (minecraft.level == null || minecraft.player == null || GuiProbe.screen(minecraft) != null) return;
            next(6); return;
        }
        if (stage == 6 && elapsed > 5_000_000_000L) {
            int refresh = minecraft.getWindow().getRefreshRate();
            if (refresh > 0 && minecraft.getFramerateLimitTracker().getFramerateLimit() != refresh) throw new AssertionError("Foreground native limiter is not set to the current monitor");
            System.out.println("Kernel Frame Sync display: " + refresh + " Hz; " + FrameSync.pacedLabel() + ", " + FrameSync.estimateLabel());
            if (!FrameSync.pacedLabel().startsWith("FPS-fs: ") || !FrameSync.estimateLabel().startsWith("FPS: ~")) throw new AssertionError("Frame counters did not produce samples");
            assertNativeSync(minecraft, true);
            captured = false;
            GuiProbe.capture(minecraft, "frame-sync-hud.png", message -> captured = true);
            next(7); return;
        }
        if (stage == 7 && captured) {
            //? if >=1.21.11 {
            minecraft.debugEntries.setOverlayVisible(true);
            //? } elif >=1.21.9 {
            /*minecraft.debugEntries.setF3Visible(true);
            *///? }
            //? if <1.21.9 {
            /*minecraft.gui.getDebugOverlay().toggleOverlay();
            *///? }
            captured = false; next(8); return;
        }
        if (stage == 8 && elapsed > 2_000_000_000L) {
            GuiProbe.capture(minecraft, "frame-sync-debug.png", message -> {
                System.out.println("Kernel Frame Sync probe passed: " + FrameSync.pacedLabel() + ", " + FrameSync.estimateLabel());
                captured = true;
            });
            captured = false; next(10); return;
        }
        if (stage == 10 && captured) {
            FrameSync.save(false);
            if (minecraft.getFramerateLimitTracker().getFramerateLimit() != 120) throw new AssertionError("Disabling Frame Sync did not restore the saved cap");
            minecraft.options.enableVsync().set(true);
            minecraft.options.framerateLimit().set(260);
            next(11); return;
        }
        if (stage == 11 && elapsed > 5_000_000_000L) {
            System.out.println("Kernel Frame Sync control, native VSync only: " + minecraft.getFps() + " FPS");
            minecraft.options.enableVsync().set(false);
            next(12); return;
        }
        if (stage == 12 && elapsed > 5_000_000_000L) {
            System.out.println("Kernel Frame Sync control, uncapped: " + minecraft.getFps() + " FPS");
            FrameSync.save(true);
            Files.writeString(minecraft.gameDirectory.toPath().resolve("frame-sync-complete.json"),
                "{\"nativeSync\":true,\"monitorLimit\":true,\"optionsPreserved\":true,\"drafts\":true,\"worldAndDebugFrames\":true}\n");
            stage = 9; minecraft.execute(minecraft::stop);
        }
    }

    private static void assertNativeSync(Minecraft minecraft, boolean expected) throws Exception {
        //? if >=26.2 {
        var surface = minecraft.windowSurface();
        var nativeMode = surface.currentConfiguration().orElseThrow().presentMode();
        var expectedMode = com.mojang.blaze3d.systems.GpuSurface.PresentMode.getSupportedVsyncMode(surface.supportedPresentModes(), expected);
        if (nativeMode != expectedMode) throw new AssertionError("Native presentation mode did not update: " + nativeMode + " != " + expectedMode);
        //? } else {
        /*var field = minecraft.getWindow().getClass().getDeclaredField("vsync");
        field.setAccessible(true);
        if (field.getBoolean(minecraft.getWindow()) != expected) throw new AssertionError("Native VSync did not update");
        *///? }
    }
    private static Button frameButton(Minecraft minecraft) {
        String label = KernelTranslations.text("kernel.frame.setting").getString();
        return GuiProbe.screen(minecraft).children().stream().filter(Button.class::isInstance).map(Button.class::cast)
            .filter(button -> button.getMessage().getString().startsWith(label + ":")).findFirst().orElseThrow();
    }
    private static void next(int value) { stage = value; changedAt = System.nanoTime(); }
}
