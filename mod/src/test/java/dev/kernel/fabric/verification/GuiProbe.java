package dev.kernel.fabric.verification;

import dev.kernel.fabric.config.KernelRendererSettings;
import dev.kernel.fabric.config.KernelSettingsScreen;
import dev.kernel.fabric.config.RendererConfig;
import dev.kernel.fabric.config.RendererFeature;
import dev.kernel.fabric.config.KernelTranslations;
import dev.kernel.fabric.config.KernelHardwareSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;

//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
//? }

/** Self-driving GUI verification in an isolated Loom game directory. Captures only the game framebuffer. */
public final class GuiProbe {
    private static final long START = System.nanoTime();
    private static long readyMillis = -1;
    private static int stage;
    private static long changedAt;
    private static volatile boolean captured;
    private static boolean waiting;
    private static Screen parent;
    private static boolean started;
    private static boolean loadingCaptureRequested;

    public static void ready() {
        if (readyMillis < 0) readyMillis = ManagementFactory.getRuntimeMXBean().getUptime();
    }

    public static void frame(Minecraft minecraft) {
        if (Boolean.getBoolean("kernel.guiProbe.frameSync")) { FrameSyncProbe.frame(minecraft, readyMillis); return; }
        if (stage == 4) return;
        try { advance(minecraft); }
        catch (RuntimeException | Error failure) { stage = 4; throw failure; }
    }

    private static void advance(Minecraft minecraft) {
        if (System.nanoTime() - START > 120_000_000_000L) throw new AssertionError("Kernel GUI probe timed out");
        //? if >=26.2 {
        boolean overlayVisible = minecraft.gui.overlay() != null;
        //? } else {
        /*boolean overlayVisible = minecraft.getOverlay() != null;
        *///? }
        if (Boolean.getBoolean("kernel.guiProbe.bootstrap") && !loadingCaptureRequested && overlayVisible) {
            loadingCaptureRequested = true;
            capture(minecraft, "kernel-loading-window.png", message -> {});
        }
        if (readyMillis < 0) {
            return;
        }
        if (stage == 4) return;
        if (stage == 0 && !started) {
            // The game-load callback precedes the final loading-overlay fade. Capture the real screen.
            //? if >=26.2 {
            if (minecraft.gui.overlay() != null) return;
            //? } else {
            /*if (minecraft.getOverlay() != null) return;
            *///? }
            parent = screen(minecraft);
            minecraft.options.guiScale().set(2);
            //? if >=26.1 {
            minecraft.resizeGui();
            //? } else {
            /*minecraft.resizeDisplay();
            *///? }
            if (parent instanceof KernelSettingsScreen) throw new AssertionError("Settings opened automatically at launch");
            if (Boolean.getBoolean("kernel.guiProbe.bootstrap")) {
                try {
                    Class<?> owner = Class.forName("dev.kernel.client.loading.EarlyLoadingWindow", false, ClassLoader.getSystemClassLoader());
                    long earlyHandle = (long) owner.getMethod("handle").invoke(null);
                    //? if >=1.21.9 {
                    long actualHandle = minecraft.getWindow().handle();
                    //? } else {
                    /*long actualHandle = minecraft.getWindow().getWindow();
                    *///? }
                    if (!(boolean) owner.getMethod("adopted").invoke(null) || earlyHandle == 0 || earlyHandle != actualHandle) {
                        throw new AssertionError("Minecraft did not adopt the original Kernel window");
                    }
                    Class<?> progress = Class.forName("dev.kernel.client.loading.StartupProgress", false, ClassLoader.getSystemClassLoader());
                    if (!(boolean) progress.getMethod("visibleBeforeFabric").invoke(null)) {
                        throw new AssertionError("The Kernel window was not visible before Fabric's KnotClient was defined");
                    }
                    System.out.println("Kernel bootstrap probe verified the original window handle: " + earlyHandle);
                } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
            }
            if (Boolean.getBoolean("kernel.guiProbe.preview")) {
                click(find(parent, "kernel.settings.open"));
                stage = 4;
                System.out.println("Kernel settings preview ready; leaving the game open for user inspection.");
                return;
            }
            Button icon = find(parent, "kernel.settings.open");
            Button options = find(parent, "menu.options");
            if (icon.getRight() > options.getX() || icon.getY() != options.getY()) throw new AssertionError("Kernel icon is not left of Options");
            int distance = minecraft.options.renderDistance().get();
            minecraft.options.renderDistance().set(15);
            KernelHardwareSettings.applyOnce(minecraft);
            if (minecraft.options.renderDistance().get() != 15) throw new AssertionError("One-time recommendation overwrote a manual change");
            minecraft.options.renderDistance().set(distance);
            started = true; changedAt = System.nanoTime();
            return;
        }
        if (waiting) {
            if (!captured) return;
            waiting = false; captured = false;
            if (stage == 0) {
                click(find(parent, "kernel.settings.open"));
                if (!(screen(minecraft) instanceof KernelSettingsScreen)) throw new AssertionError("Menu icon did not open Kernel settings");
                stage = 1; changedAt = System.nanoTime();
            } else if (stage == 1) {
                click(find(screen(minecraft), "kernel.video.tab.optimizations"));
                click(find(screen(minecraft), "kernel.settings.next"));
                String sorting = KernelTranslations.text(RendererFeature.QUAD_SORTING.translationKey()).getString();
                Button toggle = screen(minecraft).children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.getMessage().getString().startsWith(sorting)).findFirst().orElseThrow();
                screen(minecraft).setFocused(toggle);
                click(toggle);
                stage = 2; changedAt = System.nanoTime();
            } else {
                click(find(screen(minecraft), "gui.cancel"));
                if (screen(minecraft) != parent || !KernelRendererSettings.saved().equals(RendererConfig.defaults())) {
                    throw new AssertionError("Cancel saved a draft or lost the parent screen");
                }
                click(find(parent, "kernel.settings.open"));
                click(find(screen(minecraft), "kernel.video.recommended"));
                click(find(screen(minecraft), "kernel.settings.apply"));
                if (!(screen(minecraft) instanceof KernelSettingsScreen)) throw new AssertionError("Apply unexpectedly closed settings");
                click(find(screen(minecraft), "gui.done"));
                var settingsPath = minecraft.gameDirectory.toPath().resolve("config/kernel-renderer.properties");
                if (screen(minecraft) != parent || !Files.isRegularFile(settingsPath)
                    || !RendererConfig.load(settingsPath).config().equals(RendererConfig.defaults())) {
                    throw new AssertionError("Save failed to persist the settings or return to its parent");
                }
                stage = 4;
                try {
                    Files.writeString(minecraft.gameDirectory.toPath().resolve("probe-complete.json"),
                        "{\"readyMillis\":" + readyMillis + ",\"screenshots\":3,\"saveAndCancelVerified\":true,\"menuButtonVerified\":true,\"oneTimeRecommendationVerified\":true}\n");
                } catch (IOException exception) { throw new AssertionError(exception); }
                System.out.println("Kernel GUI probe passed; game-load callback at JVM uptime " + readyMillis + " ms.");
                minecraft.stop();
            }
            return;
        }
        if (System.nanoTime() - changedAt < 1_500_000_000L) return;
        waiting = true;
        String file = "kernel-settings-" + stage + ".png";
        try { Files.deleteIfExists(minecraft.gameDirectory.toPath().resolve("screenshots").resolve(file)); }
        catch (IOException exception) { throw new AssertionError("Cannot replace probe screenshot", exception); }
        capture(minecraft, file, message -> screenshotFinished(minecraft, file));
    }

    static void capture(Minecraft minecraft, String file, java.util.function.Consumer<Component> complete) {
        //? if >=26.2 {
        var target = minecraft.gameRenderer.mainRenderTarget();
        //? } else {
        /*var target = minecraft.getMainRenderTarget();
        *///? }
        //? if >=1.21.6 {
        Screenshot.grab(minecraft.gameDirectory, file, target, 1, complete);
        //? } else {
        /*Screenshot.grab(minecraft.gameDirectory, file, target, complete);
        *///? }
    }

    private static void screenshotFinished(Minecraft minecraft, String file) {
        if (!Files.isRegularFile(minecraft.gameDirectory.toPath().resolve("screenshots").resolve(file))) {
            throw new AssertionError("Screenshot capture failed: " + file);
        }
        captured = true;
    }

    static Button find(Screen screen, String key) {
        String label = (key.startsWith("kernel.") ? KernelTranslations.text(key) : Component.translatable(key)).getString();
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
            .filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow(() -> new AssertionError("Missing " + label + " in " + screen.getClass().getName() + ": " + screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).map(b -> b.getMessage().getString()).toList()));
    }

    static void click(Button button) {
        if (!button.active) throw new AssertionError("Inactive probe control: " + button.getMessage().getString());
        //? if >=1.21.9 {
        button.onPress(new KeyEvent(257, 0, 0));
        //? } else {
        /*button.onPress();
        *///? }
    }

    static Screen screen(Minecraft minecraft) {
        //? if >=26.2 {
        return minecraft.gui.screen();
        //? } else {
        /*return minecraft.screen;
        *///? }
    }

    private static void show(Minecraft minecraft, Screen screen) {
        //? if >=26.2 {
        minecraft.gui.setScreen(screen);
        //? } else {
        /*minecraft.setScreen(screen);
        *///? }
    }
}
