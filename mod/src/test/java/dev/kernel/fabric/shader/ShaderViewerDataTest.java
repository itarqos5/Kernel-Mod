package dev.kernel.fabric.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShaderViewerDataTest {
    private static ShaderViewerData sample() {
        return new ShaderViewerData(2, 0.25f, 0.5f, 0.75f, 0.6f, 9, 15, new float[]{0.1f, 0.2f, 0.3f});
    }

    @Test void namesTheThreeFluidsTheFormatKnowsAndNothingElse() {
        assertEquals(0, ShaderViewerData.eyeInWater("NONE"));
        assertEquals(1, ShaderViewerData.eyeInWater("WATER"));
        assertEquals(2, ShaderViewerData.eyeInWater("LAVA"));
        assertEquals(3, ShaderViewerData.eyeInWater("POWDER_SNOW"));
    }

    @Test void readsAFluidTheGameAddedLaterAsAir() {
        // 1.21.11 added ATMOSPHERIC in the middle of the game's own enum. A pack written against the
        // format knows three fluids, so anything else has to read as the absence of all three rather
        // than as whichever one happens to share an ordinal.
        assertEquals(0, ShaderViewerData.eyeInWater("ATMOSPHERIC"));
        assertEquals(0, ShaderViewerData.eyeInWater("SOMETHING_A_LATER_VERSION_ADDS"));
    }

    @Test void reportsLightOnTheFormatsScaleRatherThanTheGamesLevels() {
        assertArrayEquals(new int[]{144, 240}, sample().eyeBrightness());
        assertArrayEquals(new int[]{0, 0},
            new ShaderViewerData(0, 0, 0, 0, 0, 0, 0, new float[3]).eyeBrightness());
    }

    @Test void splitsAPackedColourIntoComponents() {
        assertArrayEquals(new float[]{1.0f, 0.0f, 0.0f}, ShaderViewerData.rgb(0xFF0000), 1.0e-6f);
        assertArrayEquals(new float[]{0.0f, 1.0f, 0.0f}, ShaderViewerData.rgb(0xFF00FF00), 1.0e-6f);
        assertArrayEquals(new float[]{0.0f, 0.0f, 1.0f}, ShaderViewerData.rgb(0x0000FF), 1.0e-6f);
    }

    @Test void answersEveryInputByTheNameTheFormatUses() {
        var data = sample();
        assertEquals(2, data.integer("isEyeInWater"));
        assertEquals(0.25f, data.scalar("blindness"));
        assertEquals(0.5f, data.scalar("darknessFactor"));
        assertEquals(0.75f, data.scalar("nightVision"));
        assertEquals(0.6f, data.scalar("screenBrightness"));
        assertArrayEquals(new int[]{144, 240}, data.integerVector("eyeBrightness"));
        assertArrayEquals(new float[]{0.1f, 0.2f, 0.3f}, data.vector("skyColor"), 1.0e-6f);
    }

    @Test void refusesANameThatIsNotOneOfTheseInputs() {
        var data = sample();
        assertThrows(IllegalArgumentException.class, () -> data.scalar("wetness"));
        assertThrows(IllegalArgumentException.class, () -> data.integer("blindness"));
        assertThrows(IllegalArgumentException.class, () -> data.vector("fogColor"));
        assertThrows(IllegalArgumentException.class, () -> data.integerVector("eyeBrightnessSmooth"));
    }

    @Test void keepsItsOwnCopyOfTheColourItWasGiven() {
        float[] supplied = {0.1f, 0.2f, 0.3f};
        var data = new ShaderViewerData(0, 0, 0, 0, 0, 0, 0, supplied);
        supplied[0] = 1.0f;
        assertNotSame(supplied, data.skyColor());
        assertEquals(0.1f, data.skyColor()[0]);
    }
}
