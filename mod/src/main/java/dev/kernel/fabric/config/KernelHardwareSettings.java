package dev.kernel.fabric.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.CloudStatus;
import net.minecraft.server.level.ParticleStatus;
//? if >=26.1 {
import com.mojang.blaze3d.systems.RenderSystem;
//? } else {
/*import org.lwjgl.opengl.GL11;
*///? }
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Runs once after options and the GPU exist. It never opens a screen or changes JVM/launcher options. */
public final class KernelHardwareSettings {
    private static HardwareRecommendation recommendation;
    private static String renderer = "Unknown GPU";
    private KernelHardwareSettings() {}

    public static HardwareRecommendation recommendation() {
        if (recommendation == null) {
            //? if >=26.2 {
            String detected = RenderSystem.getDevice().getDeviceInfo().name();
            //? } elif >=26.1 {
            /*String detected = RenderSystem.getDevice().getRenderer();
            *///? } else {
            /*String detected = GL11.glGetString(GL11.GL_RENDERER);
            *///? }
            if (detected != null) renderer = detected;
            recommendation = HardwareRecommendation.choose(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory(), renderer);
        }
        return recommendation;
    }

    public static String renderer() { recommendation(); return renderer; }

    public static void applyOnce(Minecraft minecraft) {
        Path config = minecraft.gameDirectory.toPath().resolve("config");
        Path marker = config.resolve("kernel-hardware.properties");
        if (Files.exists(marker)) return;
        try {
            var preset = recommendation();
            Files.createDirectories(config);
            Path options = minecraft.gameDirectory.toPath().resolve("options.txt");
            Path backup = config.resolve("kernel-options.before-recommendations.txt");
            if (Files.isRegularFile(options) && Files.notExists(backup)) Files.copy(options, backup);
            // Claim the one-time operation before changing options. A failed launch must not apply it repeatedly.
            Files.writeString(marker, "schema=1\ntier=" + preset.tier() + "\nrenderDistance=" + preset.renderDistance()
                + "\nsimulationDistance=" + preset.simulationDistance() + "\n", StandardOpenOption.CREATE_NEW);
            minecraft.options.renderDistance().set(preset.renderDistance());
            minecraft.options.simulationDistance().set(preset.simulationDistance());
            minecraft.options.cloudStatus().set(preset.detailedClouds() ? CloudStatus.FANCY : CloudStatus.FAST);
            minecraft.options.particles().set(preset.detailedClouds() ? ParticleStatus.ALL : ParticleStatus.DECREASED);
            minecraft.options.save();
            LoggerFactory.getLogger("Kernel").info("Applied one-time {} hardware starting point ({} logical CPUs, {} MiB JVM heap, {}). Manual settings are preserved on subsequent launches.",
                preset.tier(), Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory() / (1024 * 1024), renderer);
        } catch (IOException | RuntimeException exception) {
            LoggerFactory.getLogger("Kernel").warn("Could not apply Kernel hardware recommendations; continuing with the available settings", exception);
        }
    }
}
