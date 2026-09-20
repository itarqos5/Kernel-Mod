package dev.kernel.fabric.shader;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The expected directions come from Minecraft's own sky transform, not from a table: the game hangs the
 * sun at (0, 100, 0) after turning the sky about Y by -90 degrees and about X by the day's fraction.
 */
class ShaderCelestialDataTest {
    /** The identity model-view, so a direction comes out in world space and can be read directly. */
    private static final float[] IDENTITY = {
        1, 0, 0, 0,
        0, 1, 0, 0,
        0, 0, 1, 0,
        0, 0, 0, 1};

    private static void assertDirection(float[] actual, double x, double y, double z) {
        assertEquals(x * ShaderCelestialData.DISTANCE, actual[0], 0.01, "x");
        assertEquals(y * ShaderCelestialData.DISTANCE, actual[1], 0.01, "y");
        assertEquals(z * ShaderCelestialData.DISTANCE, actual[2], 0.01, "z");
    }

    @Test void atNoonTheSunIsOverheadAndTheMoonIsBelow() {
        var celestial = ShaderCelestialData.from(0.0f, IDENTITY);
        assertDirection(celestial.sun(), 0.0, 1.0, 0.0);
        assertDirection(celestial.moon(), 0.0, -1.0, 0.0);
    }

    @Test void theSunSetsAndRisesOnOppositeHorizons() {
        // A quarter of a day past noon the sun is on the horizon, and three quarters the other side.
        assertDirection(ShaderCelestialData.from(0.25f, IDENTITY).sun(), -1.0, 0.0, 0.0);
        assertDirection(ShaderCelestialData.from(0.75f, IDENTITY).sun(), 1.0, 0.0, 0.0);
        assertDirection(ShaderCelestialData.from(0.5f, IDENTITY).sun(), 0.0, -1.0, 0.0);
    }

    @Test void theMoonIsAlwaysOppositeTheSun() {
        for (float angle = 0.0f; angle < 1.0f; angle += 0.05f) {
            var celestial = ShaderCelestialData.from(angle, IDENTITY);
            for (int axis = 0; axis < 3; axis++)
                assertEquals(-celestial.sun()[axis], celestial.moon()[axis], 0.01, "axis " + axis);
        }
    }

    @Test void theDayIsMeasuredFromSunriseRatherThanFromNoon() {
        // The game's angle is zero at noon; the shader format's is zero at sunrise, a quarter earlier.
        assertEquals(0.25f, ShaderCelestialData.from(0.0f, IDENTITY).sunAngle(), 0.0001);
        assertEquals(0.0f, ShaderCelestialData.from(0.75f, IDENTITY).sunAngle(), 0.0001);
        assertEquals(0.5f, ShaderCelestialData.from(0.25f, IDENTITY).sunAngle(), 0.0001);
    }

    @Test void theShadowAngleRunsTwiceADayAsTheCasterChanges() {
        // sunAngle runs from sunrise: the sun casts for its first half, the moon for the second. A
        // game angle of 0.1 is mid-morning and 0.6 is well into the night.
        var sunCasting = ShaderCelestialData.from(0.1f, IDENTITY);
        assertTrue(sunCasting.sunAngle() < 0.5f);
        assertEquals(sunCasting.sunAngle(), sunCasting.shadowAngle(), 0.0001);
        assertArrayEquals(sunCasting.sun(), sunCasting.shadowLight(), 0.0001f);

        var moonCasting = ShaderCelestialData.from(0.6f, IDENTITY);
        assertTrue(moonCasting.sunAngle() >= 0.5f);
        assertEquals(moonCasting.sunAngle() - 0.5f, moonCasting.shadowAngle(), 0.0001);
        assertArrayEquals(moonCasting.moon(), moonCasting.shadowLight(), 0.0001f);
    }

    @Test void theShadowAngleStaysWithinHalfATurn() {
        for (float angle = 0.0f; angle < 1.0f; angle += 0.01f) {
            float shadow = ShaderCelestialData.from(angle, IDENTITY).shadowAngle();
            assertTrue(shadow >= 0.0f && shadow < 0.5f, "shadowAngle " + shadow + " at " + angle);
        }
    }

    @Test void directionsAreRotatedIntoTheSpaceTheModelViewDescribes() {
        // A quarter turn about Y takes world up to itself but swings the horizon onto another axis.
        float[] turned = {
            0, 0, -1, 0,
            0, 1, 0, 0,
            1, 0, 0, 0,
            0, 0, 0, 1};
        assertDirection(ShaderCelestialData.from(0.0f, turned).sun(), 0.0, 1.0, 0.0);
        assertDirection(ShaderCelestialData.from(0.0f, turned).up(), 0.0, 1.0, 0.0);
        assertDirection(ShaderCelestialData.from(0.25f, turned).sun(), 0.0, 0.0, 1.0);
    }

    @Test void upIsWorldUpWhicheverWayTheDayHasTurned() {
        for (float angle = 0.0f; angle < 1.0f; angle += 0.1f)
            assertDirection(ShaderCelestialData.from(angle, IDENTITY).up(), 0.0, 1.0, 0.0);
    }

    @Test void anAngleOutsideOneDayIsWrappedRatherThanRejected() {
        assertEquals(ShaderCelestialData.from(0.3f, IDENTITY).sunAngle(),
            ShaderCelestialData.from(3.3f, IDENTITY).sunAngle(), 0.0001);
        assertDirection(ShaderCelestialData.from(2.0f, IDENTITY).sun(), 0.0, 1.0, 0.0);
    }

    @Test void theRecordKeepsItsOwnCopyOfWhatItWasGiven() {
        // Built directly rather than through the factory, so the caller still holds the array it passed.
        float[] supplied = {1.0f, 2.0f, 3.0f};
        var celestial = new ShaderCelestialData(0.0f, 0.0f, supplied, supplied, supplied, supplied);
        supplied[1] = 1234.0f;
        assertEquals(2.0f, celestial.sun()[1], 0.0001, "a later change to the caller's array must not leak in");
    }

    @Test void anUnknownInputIsRefusedRatherThanGuessedAt() {
        var celestial = ShaderCelestialData.from(0.0f, IDENTITY);
        assertThrows(IllegalArgumentException.class, () -> celestial.vector("cameraPosition"));
        assertThrows(IllegalArgumentException.class, () -> celestial.angle("sunPosition"));
    }
}
