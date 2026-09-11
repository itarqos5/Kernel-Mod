package dev.kernel.fabric.shader;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ShaderWorldDataTest {
    @Test void keepsTimeWithinTheCurrentDayAcrossMidnightAndTimeSet() {
        var before = ShaderWorldData.from(23999, 7, .25f, .125f);
        var after = ShaderWorldData.from(24000, 0, .8f, .4f);
        var reset = ShaderWorldData.from(6000, 2, 0, 0);
        assertEquals(23999, before.worldTime()); assertEquals(0, before.worldDay());
        assertEquals(0, after.worldTime()); assertEquals(1, after.worldDay());
        assertEquals(6000, reset.worldTime()); assertEquals(0, reset.worldDay());
        assertEquals(7, before.moonPhase()); assertEquals(.25f, before.rainStrength()); assertEquals(.125f, before.thunderStrength());
    }

    @Test void preservesLegacySignedClockAndDayCounterWrap() {
        var negative = ShaderWorldData.from(-24001, 0, 0, 0);
        assertEquals(-1, negative.worldTime()); assertEquals(-1, negative.worldDay());
        var wrap = ShaderWorldData.from(24000L * Integer.MAX_VALUE + 12000L, 0, 0, 0);
        assertEquals(12000, wrap.worldTime()); assertEquals(0, wrap.worldDay());
        var next = ShaderWorldData.from(24000L * (Integer.MAX_VALUE + 1L) + 23L, 0, 0, 0);
        assertEquals(23, next.worldTime()); assertEquals(1, next.worldDay());
    }
}
