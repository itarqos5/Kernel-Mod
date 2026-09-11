package dev.kernel.fabric.render;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class AdaptiveUploadBudgetTest {
    @Test void reservesHeadroomDuringOverloadAndRecoversGradually() {
        var budget = new AdaptiveUploadBudget();
        budget.recordFrame(8_000_000, 2_000_000, 16_666_667);
        assertEquals(2_000_000, budget.nanos());
        budget.recordFrame(25_000_000, 2_000_000, 16_666_667);
        assertEquals(250_000, budget.nanos());
        long previous = budget.nanos();
        for (int frame = 0; frame < 120; frame++) {
            budget.recordFrame(6_000_000, 0, 16_666_667);
            assertTrue(budget.nanos() >= previous);
            assertTrue(budget.nanos() - previous <= 125_000);
            previous = budget.nanos();
        }
        assertEquals(2_000_000, budget.nanos());
    }
    @Test void ignoresInvalidSamplesAndDoesNotTreatUploadCostAsBaseRenderingCost() {
        var budget = new AdaptiveUploadBudget();
        budget.recordFrame(20_000_000, 19_000_000, 16_666_667);
        assertEquals(2_000_000, budget.nanos());
        budget.recordFrame(-1, 0, 16_666_667);
        budget.recordFrame(20_000_000, -1, 16_666_667);
        budget.recordFrame(20_000_000, 0, 0);
        assertEquals(2_000_000, budget.nanos());
        budget.recordFrame(20_000_000, 0, 16_666_667);
        assertEquals(250_000, budget.nanos());
        budget.reset(); assertEquals(2_000_000, budget.nanos());
    }
    @Test void nestedFramesAndWaitsAreCountedOnce() {
        var time = new AtomicLong(); var clock = new FrameWorkClock(time::get);
        clock.begin(16_666_667);
        time.addAndGet(1_000_000); clock.begin(100_000_000);
        time.addAndGet(2_000_000); clock.recordUploads(2_000_000);
        clock.beginWait(); time.addAndGet(4_000_000);
        clock.beginWait(); time.addAndGet(3_000_000); clock.endWait();
        time.addAndGet(2_000_000); clock.endWait();
        clock.end(); assertEquals(0, clock.frames());
        time.addAndGet(1_000_000); clock.end();
        assertEquals(4_000_000, clock.lastWorkNanos()); assertEquals(2_000_000, clock.lastUploadNanos());
        assertEquals(1, clock.frames());
        clock.begin(16_666_667); assertEquals(2_000_000, clock.budgetNanos());
        time.addAndGet(30_000_000); clock.end();
        clock.begin(16_666_667); assertEquals(250_000, clock.budgetNanos());
    }
    @Test void unmatchedLifecycleCallsAndResetDoNotRetainStaleAccounting() {
        var time = new AtomicLong(); var clock = new FrameWorkClock(time::get);
        clock.end(); clock.beginWait(); clock.endWait(); clock.recordUploads(20);
        assertEquals(0, clock.frames());
        clock.begin(16_666_667); time.addAndGet(3_000_000); clock.beginWait(); time.addAndGet(30_000_000); clock.end();
        assertEquals(3_000_000, clock.lastWorkNanos());
        clock.begin(16_666_667); time.addAndGet(100_000_000); clock.reset(); clock.end();
        assertEquals(0, clock.frames()); assertEquals(2_000_000, clock.budgetNanos());
    }
}
