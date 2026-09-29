package dev.kernel.fabric.shader;

/**
 * What the world is doing to the viewer, for the frame being post-processed.
 *
 * <p>These are the inputs a pack reads to tint the screen for the fluid the eye is in, to dim it under
 * blindness or darkness, to lift it under night vision, and to know how lit the block the camera stands
 * in is. Each one is taken from the rule Minecraft itself applies rather than from a convention Kernel
 * invented, so a version that changes the rule changes these with it — the same principle the celestial
 * inputs follow.
 *
 * <p>Captured once per world render and shared by every pass in that frame, so two passes cannot
 * disagree about whether the player is underwater.
 */
public record ShaderViewerData(int eyeInWater, float blindness, float darkness, float nightVision,
                               float screenBrightness, int eyeBlockLight, int eyeSkyLight, float[] skyColor) {
    /**
     * The light levels the game keeps run 0 to 15; the format reports them on the game's own packed
     * scale, which is the same levels multiplied by sixteen.
     */
    private static final int LIGHT_SCALE = 16;

    public ShaderViewerData {
        skyColor = skyColor.clone();
    }

    /**
     * The format's number for the fluid the eye is in.
     *
     * <p>Named rather than ordinal, because the game's own enum is not a stable numbering: 1.21.11 added
     * a member in the middle of it. The format names three fluids and nothing else, so anything the game
     * grows beyond them reads as air, which is what a pack written against the format already assumes.
     *
     * @param fogType the name of Minecraft's own fog type for the camera
     */
    public static int eyeInWater(String fogType) {
        return switch (fogType) {
            case "WATER" -> 1;
            case "LAVA" -> 2;
            case "POWDER_SNOW" -> 3;
            default -> 0;
        };
    }

    /** Splits one of the game's packed colours into the three components a program reads. */
    public static float[] rgb(int packed) {
        return new float[]{(packed >> 16 & 0xFF) / 255.0f, (packed >> 8 & 0xFF) / 255.0f, (packed & 0xFF) / 255.0f};
    }

    /** The block and sky light at the camera, on the format's 0 to 240 scale. */
    public int[] eyeBrightness() { return new int[]{eyeBlockLight * LIGHT_SCALE, eyeSkyLight * LIGHT_SCALE}; }

    /** The scalar one of the viewer inputs asks for. */
    public float scalar(String name) {
        return switch (name) {
            case "blindness" -> blindness;
            case "darknessFactor" -> darkness;
            case "nightVision" -> nightVision;
            case "screenBrightness" -> screenBrightness;
            default -> throw new IllegalArgumentException("Unknown viewer scalar: " + name);
        };
    }

    /** The integer one of the viewer inputs asks for. */
    public int integer(String name) {
        if (name.equals("isEyeInWater")) return eyeInWater;
        throw new IllegalArgumentException("Unknown viewer integer: " + name);
    }

    /** The vector one of the viewer inputs asks for. */
    public float[] vector(String name) {
        if (name.equals("skyColor")) return skyColor;
        throw new IllegalArgumentException("Unknown viewer vector: " + name);
    }

    /** The integer vector one of the viewer inputs asks for. */
    public int[] integerVector(String name) {
        if (name.equals("eyeBrightness")) return eyeBrightness();
        throw new IllegalArgumentException("Unknown viewer integer vector: " + name);
    }
}
