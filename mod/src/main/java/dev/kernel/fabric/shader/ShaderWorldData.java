package dev.kernel.fabric.shader;

/**
 * One immutable set of world inputs, shared by every pass in a rendered frame.
 *
 * <p>{@code celestialAngle} is the game's own time of day, zero at noon, which is what the sun and moon
 * positions are derived from. It is kept here rather than recomputed per pass so every pass in a frame
 * agrees about where the sun is.
 */
public record ShaderWorldData(int worldTime, int worldDay, int moonPhase, float rainStrength,
                              float thunderStrength, float celestialAngle) {
    public static ShaderWorldData from(long dayTime, int moonPhase, float rainStrength, float thunderStrength) {
        return from(dayTime, moonPhase, rainStrength, thunderStrength, 0.0f);
    }
    public static ShaderWorldData from(long dayTime, int moonPhase, float rainStrength, float thunderStrength, float celestialAngle) {
        // Preserve the legacy time-query integer semantics, including signed custom clock values.
        return new ShaderWorldData((int) (dayTime % 24000L), (int) ((dayTime / 24000L) % Integer.MAX_VALUE),
            moonPhase, rainStrength, thunderStrength, celestialAngle);
    }
}
