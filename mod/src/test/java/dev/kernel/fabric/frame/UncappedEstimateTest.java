package dev.kernel.fabric.frame;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class UncappedEstimateTest {
    @Test void estimatesTheBottleneckInsteadOfAddingOverlappedCpuAndGpuWork() {
        var estimate = new UncappedEstimate();
        for (int i = 0; i < 64; i++) estimate.record(2_000_000, 4_000_000);
        assertEquals(250, estimate.fps()); assertTrue(estimate.includesGpu());
        for (int i = 0; i < 64; i++) estimate.record(5_000_000, 3_000_000);
        assertEquals(200, estimate.fps());
    }
    @Test void keepsBoundedHistoryAndDistinguishesCpuOnlySamples() {
        var estimate = new UncappedEstimate();
        for (int i = 0; i < 64; i++) estimate.record(2_000_000, 4_000_000);
        for (int i = 0; i < 64; i++) estimate.record(1_000_000, 0);
        assertEquals(1000, estimate.fps()); assertFalse(estimate.includesGpu());
        estimate.record(-1, 100); assertEquals(1000, estimate.fps());
        estimate.reset(); assertEquals(0, estimate.fps()); assertFalse(estimate.includesGpu());
    }
}
