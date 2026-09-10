package dev.kernel.fabric.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HardwareRecommendationTest {
    private static final long GIB = 1024L * 1024 * 1024;
    @Test void softwareAndResourceLimitsTakePriorityOverGpuNames() {
        assertEquals("light", HardwareRecommendation.choose(32, 16 * GIB, "GeForce via llvmpipe").tier());
        assertEquals("light", HardwareRecommendation.choose(2, 16 * GIB, "GeForce RTX 4090").tier());
        assertEquals("light", HardwareRecommendation.choose(16, GIB, "Radeon RX 7900").tier());
    }
    @Test void UnknownOrIntegratedGpuNeverInfersDedicatedMemory() {
        for (String gpu : new String[]{null, "", "Intel UHD Graphics", "AMD Radeon Graphics", "Apple M1"}) {
            var result = HardwareRecommendation.choose(16, 8 * GIB, gpu);
            assertEquals(8, result.renderDistance()); assertEquals(5, result.simulationDistance());
            assertFalse(result.detailedClouds());
        }
    }
    @Test void BalancedPresetRequiresBothCpuAndHeapCapacity() {
        assertEquals(12, HardwareRecommendation.choose(12, 4 * GIB, "AMD Radeon RX 580 2048SP").renderDistance());
        assertEquals(8, HardwareRecommendation.choose(12, 3 * GIB, "AMD Radeon RX 580 2048SP").renderDistance());
        assertEquals(8, HardwareRecommendation.choose(4, 8 * GIB, "NVIDIA GeForce RTX 3080").renderDistance());
    }
}
