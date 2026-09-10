package dev.kernel.client.loading;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

final class StartupProgressTest {
    @AfterEach void stop() { StartupProgress.finish(); }

    @Test void countsActualDefinitionsAndStopsTouchingTheEventPumpAfterCompletion() {
        var polls = new AtomicInteger();
        StartupProgress.start(polls::incrementAndGet);
        long initial = StartupProgress.classes();
        StartupProgress.classLoaded("example/ActualClass");
        assertEquals(initial + 1, StartupProgress.classes());
        assertEquals("CLASS: example.ActualClass", StartupProgress.detail());
        StartupProgress.mixin("example.ActualMixin");
        assertEquals("MIXIN: example.ActualMixin", StartupProgress.detail());
        StartupProgress.mod("actual-mod");
        assertEquals("MOD: actual-mod", StartupProgress.detail());
        StartupProgress.resources(); StartupProgress.resource("minecraft:textures/block/stone.png");
        assertEquals("ASSET LOOKUP: minecraft:textures/block/stone.png", StartupProgress.detail());
        int beforeStop = polls.get();
        StartupProgress.finish(); StartupProgress.classLoaded("example/AfterStartup");
        assertEquals(beforeStop, polls.get()); assertEquals(initial + 1, StartupProgress.classes());
    }

    @Test void aFailedDisplayPumpDoesNotPreventFurtherClassLoading() {
        var polls = new AtomicInteger();
        StartupProgress.start(() -> { polls.incrementAndGet(); throw new IllegalStateException("test display failure"); });
        assertDoesNotThrow(() -> StartupProgress.classLoaded("example/First"));
        assertDoesNotThrow(() -> StartupProgress.classLoaded("example/Second"));
        assertEquals(1, polls.get());
        assertEquals("CLASS: example.Second", StartupProgress.detail());
    }
}
