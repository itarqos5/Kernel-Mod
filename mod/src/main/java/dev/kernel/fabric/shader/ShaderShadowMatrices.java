package dev.kernel.fabric.shader;

import org.joml.Matrix4f;

/**
 * Where the shadow map is rendered from, and the projection it is rendered with.
 *
 * <p>Derived from the same reading of Minecraft's sky that {@link ShaderCelestialData} uses, so the two
 * cannot disagree about where the light is: the game turns the sky about Y by ninety degrees and then
 * about X by the day's fraction, which puts the sun along {@code (-sin, cos, 0)} in world space. That
 * direction always lies in the XZ-free plane, which is why positive Z is always a safe up vector and no
 * degenerate case has to be handled.
 *
 * <p>Both matrices are camera-relative, matching the terrain meshes, which are drawn at section origins
 * expressed relative to the camera. The view is therefore a pure rotation with no world translation in
 * it, and the projection is orthographic because a directional light has no perspective.
 */
public final class ShaderShadowMatrices {
    /** The format's own default shadow distance in blocks, used when a pack does not state one. */
    public static final float DEFAULT_DISTANCE = 160.0f;
    /** The format's own default shadow map edge in pixels. */
    public static final int DEFAULT_RESOLUTION = 1024;
    private static final float TURN = (float) (Math.PI * 2.0);
    private static final java.util.regex.Pattern RESOLUTION =
        java.util.regex.Pattern.compile("(?m)^[ \\t]*const[ \\t]+int[ \\t]+shadowMapResolution[ \\t]*=[ \\t]*([0-9]{1,5})[ \\t]*;");
    private static final java.util.regex.Pattern DISTANCE =
        java.util.regex.Pattern.compile("(?m)^[ \\t]*const[ \\t]+float[ \\t]+shadowDistance[ \\t]*=[ \\t]*([0-9]+(?:\\.[0-9]+)?)[fF]?[ \\t]*;");

    private ShaderShadowMatrices() {}

    /**
     * The shadow map edge a pack declares, in pixels, or the default when it declares none.
     *
     * <p>Bounded rather than trusted. The declaration is an ordinary GLSL constant in the pack's own
     * source, so a typo or a hostile pack could otherwise ask for a texture no device can allocate,
     * and the failure would land on the render thread mid-frame.
     */
    public static int resolutionIn(String source) {
        var found = RESOLUTION.matcher(source == null ? "" : source);
        if (!found.find()) return DEFAULT_RESOLUTION;
        return Math.clamp(Integer.parseInt(found.group(1)), 128, 4096);
    }

    /** The shadow distance a pack declares, in blocks, or the default when it declares none. */
    public static float distanceIn(String source) {
        var found = DISTANCE.matcher(source == null ? "" : source);
        if (!found.find()) return DEFAULT_DISTANCE;
        return Math.clamp(Float.parseFloat(found.group(1)), 16.0f, 512.0f);
    }

    /**
     * The world-space direction of whichever body is currently casting.
     *
     * <p>The sun casts for the first half of its own cycle and the moon for the second, which is the
     * same rule {@code shadowAngle} follows, so a pack's shadows and its shadow angle change bodies
     * together rather than a quarter of a day apart.
     *
     * @param celestialAngle the game's own time of day, zero at noon and running to one over a day
     */
    public static float[] lightDirection(float celestialAngle) {
        float wrapped = celestialAngle - (float) Math.floor(celestialAngle);
        float sunAngle = wrapped < 0.75f ? wrapped + 0.25f : wrapped - 0.75f;
        double turn = wrapped * TURN;
        float x = (float) -Math.sin(turn), y = (float) Math.cos(turn);
        return sunAngle < 0.5f ? new float[]{x, y, 0.0f} : new float[]{-x, -y, 0.0f};
    }

    /** The camera-relative view matrix looking from the casting body towards the camera. */
    public static Matrix4f modelView(float celestialAngle) {
        float[] light = lightDirection(celestialAngle);
        return new Matrix4f().lookAt(light[0], light[1], light[2], 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
    }

    /**
     * The orthographic projection covering {@code distance} blocks around the camera.
     *
     * <p>The depth range is deliberately generous rather than fitted: a fitted range would have to know
     * the height of everything that could cast into the view, and clipping a caster out of the map
     * produces light where there should be shade, which is more visible than the precision a tighter
     * range would buy.
     */
    public static Matrix4f projection(float distance) {
        float extent = Math.max(1.0f, distance);
        float depth = extent + 512.0f;
        return new Matrix4f().ortho(-extent, extent, -extent, extent, -depth, depth);
    }
}
