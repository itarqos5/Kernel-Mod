package dev.kernel.fabric.shader;

/**
 * Where the sun and moon are, in the eye space a shader pack's programs work in.
 *
 * <p>Derived from Minecraft's own sky rendering rather than from a convention Kernel invented. The game
 * draws the sun as a quad centred a hundred units along positive Y, after turning the sky by ninety
 * degrees about Y and then by the day's fraction of a full turn about X. Applying that same rotation to
 * that same point is therefore the sun's direction by construction, and a version that changes how it
 * hangs the sky changes this with it.
 *
 * <p>The angles follow the shader format rather than the game. Minecraft measures the day from noon;
 * the format measures it from sunrise, so the two differ by a quarter turn. {@code shadowAngle} is the
 * same angle for whichever body is currently casting, which is why it runs twice per day.
 *
 * <p>Positions are directions, not places: they are a hundred units long because that is where the game
 * puts the sun, and a program that wants a direction normalises them. They are transformed by the world
 * model-view, so they arrive in the space a program's own normals are in.
 */
public record ShaderCelestialData(float sunAngle, float shadowAngle,
                                  float[] sun, float[] moon, float[] shadowLight, float[] up) {
    /** The distance Minecraft hangs the sun and moon at, which the format reports unchanged. */
    public static final float DISTANCE = 100.0f;
    private static final float TURN = (float) (Math.PI * 2.0);

    public ShaderCelestialData {
        sun = sun.clone();
        moon = moon.clone();
        shadowLight = shadowLight.clone();
        up = up.clone();
    }

    /**
     * Computes the celestial inputs for one frame.
     *
     * @param celestialAngle the game's own time of day, zero at noon and running to one over a day
     * @param modelView the world model-view, column-major, as Minecraft uploaded it
     */
    public static ShaderCelestialData from(float celestialAngle, float[] modelView) {
        float wrapped = celestialAngle - (float) Math.floor(celestialAngle);
        // The game measures the day from noon and the shader format measures it from sunrise.
        float sunAngle = wrapped < 0.75f ? wrapped + 0.25f : wrapped - 0.75f;
        float turn = wrapped * TURN;
        // The sky is turned about Y by -90 degrees and then about X by the day's fraction, and the sun
        // sits at (0, 100, 0) in what that leaves. That reduces to this direction.
        float[] sun = transform(modelView, -Math.sin(turn), Math.cos(turn), 0.0);
        float[] moon = transform(modelView, Math.sin(turn), -Math.cos(turn), 0.0);
        float[] up = transform(modelView, 0.0, 1.0, 0.0);
        // The sun casts for the first half of its own cycle and the moon for the second.
        boolean sunCasts = sunAngle < 0.5f;
        return new ShaderCelestialData(sunAngle, sunCasts ? sunAngle : sunAngle - 0.5f,
            sun, moon, sunCasts ? sun : moon, up);
    }

    /** Rotates a direction into eye space by the model-view, ignoring its translation. */
    private static float[] transform(float[] matrix, double x, double y, double z) {
        float px = (float) (x * DISTANCE), py = (float) (y * DISTANCE), pz = (float) (z * DISTANCE);
        return new float[]{
            matrix[0] * px + matrix[4] * py + matrix[8] * pz,
            matrix[1] * px + matrix[5] * py + matrix[9] * pz,
            matrix[2] * px + matrix[6] * py + matrix[10] * pz};
    }

    /** The vector one of the celestial inputs asks for. */
    public float[] vector(String name) {
        return switch (name) {
            case "sunPosition" -> sun;
            case "moonPosition" -> moon;
            case "shadowLightPosition" -> shadowLight;
            case "upPosition" -> up;
            default -> throw new IllegalArgumentException("Unknown celestial vector: " + name);
        };
    }

    /** The angle one of the celestial inputs asks for. */
    public float angle(String name) {
        return switch (name) {
            case "sunAngle" -> sunAngle;
            case "shadowAngle" -> shadowAngle;
            default -> throw new IllegalArgumentException("Unknown celestial angle: " + name);
        };
    }
}
