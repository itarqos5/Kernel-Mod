package dev.kernel.fabric.shader;

/** One immutable set of world inputs, shared by every pass in a rendered frame. */
public record ShaderWorldData(int worldTime, int worldDay, int moonPhase, float rainStrength, float thunderStrength) {
    public static ShaderWorldData from(long dayTime, int moonPhase, float rainStrength, float thunderStrength) {
        // Preserve the legacy time-query integer semantics, including signed custom clock values.
        return new ShaderWorldData((int) (dayTime % 24000L), (int) ((dayTime / 24000L) % Integer.MAX_VALUE),
            moonPhase, rainStrength, thunderStrength);
    }
}
