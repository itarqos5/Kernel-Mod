package dev.kernel.fabric.shader.pack;

import java.util.Map;
import java.util.regex.Pattern;
import org.lwjgl.opengl.GL33C;

/** Shared GLSL binding names for preparation and native program validation. */
public final class ShaderUniforms {
    private static final Pattern COLOR = Pattern.compile("colortex(?:[0-9]|1[0-5])");
    private static final Map<String, Integer> SCALARS = Map.ofEntries(
        Map.entry("viewWidth", GL33C.GL_FLOAT), Map.entry("viewHeight", GL33C.GL_FLOAT), Map.entry("aspectRatio", GL33C.GL_FLOAT),
        Map.entry("frameCounter", GL33C.GL_INT), Map.entry("frameTime", GL33C.GL_FLOAT), Map.entry("frameTimeCounter", GL33C.GL_FLOAT),
        Map.entry("worldTime", GL33C.GL_INT), Map.entry("worldDay", GL33C.GL_INT), Map.entry("moonPhase", GL33C.GL_INT),
        Map.entry("rainStrength", GL33C.GL_FLOAT), Map.entry("thunderStrength", GL33C.GL_FLOAT));
    private ShaderUniforms() {}
    public static boolean isWorldInput(String name) {
        return switch (name) {
            case "worldTime", "worldDay", "moonPhase", "rainStrength", "thunderStrength" -> true;
            default -> false;
        };
    }
    public static int scalarType(String name) { return SCALARS.getOrDefault(name, -1); }
    public static int colorBuffer(String name) {
        return switch (name) {
            case "gcolor", "texture" -> 0; case "gdepth" -> 1; case "gnormal" -> 2; case "composite" -> 3;
            case "gaux1" -> 4; case "gaux2" -> 5; case "gaux3" -> 6; case "gaux4" -> 7;
            default -> COLOR.matcher(name).matches() ? Integer.parseInt(name.substring(8)) : -1;
        };
    }
}
