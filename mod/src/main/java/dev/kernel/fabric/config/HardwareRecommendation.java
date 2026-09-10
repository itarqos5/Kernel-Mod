package dev.kernel.fabric.config;

import java.util.Locale;

/** Conservative starting points, not a benchmark or an estimate of available VRAM. */
public record HardwareRecommendation(String tier, int renderDistance, int simulationDistance, boolean detailedClouds) {
    public static HardwareRecommendation choose(int processors, long heapBytes, String renderer) {
        String gpu = renderer == null ? "" : renderer.toLowerCase(Locale.ROOT);
        long gib = heapBytes / (1024L * 1024 * 1024);
        boolean software = gpu.contains("llvmpipe") || gpu.contains("softpipe") || gpu.contains("software") || gpu.contains("gdi generic");
        boolean discrete = gpu.contains("geforce") || gpu.contains("quadro") || gpu.contains("radeon rx") || gpu.contains("arc a") || gpu.contains("arc b");
        if (software || processors < 4 || gib < 2) return new HardwareRecommendation("light", 6, 5, false);
        if (discrete && processors >= 8 && gib >= 4) return new HardwareRecommendation("balanced", 12, 8, true);
        return new HardwareRecommendation("modest", 8, 5, false);
    }
}
