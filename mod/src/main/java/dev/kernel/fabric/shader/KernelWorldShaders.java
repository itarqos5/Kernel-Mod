package dev.kernel.fabric.shader;

import dev.kernel.fabric.shader.pack.PreparedShaderPack;
import dev.kernel.fabric.shader.pack.ShaderWorldEnvironment;
import dev.kernel.fabric.shader.pack.ShaderWorldPrograms;
import dev.kernel.fabric.shader.pack.ShaderWorldTranslation;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Substitutes a pack's world programs for Minecraft's own core shaders.
 *
 * <p>Minecraft resolves a core shader to its GLSL source before compiling the pipeline that uses it, so
 * returning the pack's translated program there replaces the draw without Kernel owning or managing any
 * pipeline. The vertex format, colour targets and uniform environment stay Minecraft's, which is what
 * bounds this approach: see {@code docs/SHADER_WORLD_STAGE.md}.
 *
 * <p>Every decision is made for a whole program, never for one stage. A Minecraft core shader is a
 * vertex and fragment pair that agree about their varyings; replacing one half with a pack program and
 * leaving the other would not link. So the first request for either stage translates both, and the pair
 * is used only if both succeed.
 *
 * <p>Failure is always the unchanged Minecraft source. A pack that cannot be translated renders exactly
 * as the game would without it, rather than producing output the pack did not describe.
 */
public final class KernelWorldShaders {
    /** Looks up Minecraft's own source for one stage of the core shader being substituted. */
    @FunctionalInterface public interface SourceLookup { String source(boolean vertex); }

    private record Decision(String vertex, String fragment) {
        static final Decision REFUSED = new Decision(null, null);
        String stage(boolean vertex) { return vertex ? this.vertex : fragment; }
    }

    private static final String CORE = "core/";
    private static volatile Map<String, PreparedShaderPack.WorldProgram> programs = Map.of();
    private static volatile Map<String, String> coreShaders = Map.of();
    private static final Map<String, Decision> DECISIONS = new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicInteger SUBSTITUTED = new java.util.concurrent.atomic.AtomicInteger();
    private static volatile boolean warned;

    private KernelWorldShaders() {}

    /** True when a pack is active and replaces at least one core shader. */
    public static boolean active() { return !coreShaders.isEmpty(); }

    /** The core shaders the active pack replaces, for diagnostics and the settings screen. */
    public static Map<String, String> replaced() { return coreShaders; }

    /** How many core shader stages have actually been compiled from the pack since it was adopted. */
    public static int substituted() { return SUBSTITUTED.get(); }

    /**
     * Adopts the world programs of a newly compiled pack, or clears them when shaders are switched off.
     *
     * <p>Callers must clear the graphics backend's compiled-pipeline cache afterwards, because pipelines
     * built from the previous source are cached and would otherwise keep drawing.
     */
    public static void adopt(PreparedShaderPack pack) {
        DECISIONS.clear();
        SUBSTITUTED.set(0);
        warned = false;
        if (pack == null || pack.worldPrograms().isEmpty()) {
            programs = Map.of();
            coreShaders = Map.of();
            return;
        }
        programs = pack.worldPrograms();
        coreShaders = ShaderWorldPrograms.resolveAll(pack.worldPrograms().keySet());
    }

    /**
     * Returns the source Minecraft should compile for one core shader.
     *
     * @param path the shader identifier's path, such as {@code core/terrain}
     * @param vertex whether the vertex stage was asked for
     * @param original Minecraft's own source, returned unchanged whenever substitution cannot proceed
     * @param lookup supplies Minecraft's own source for the other stage of the same core shader
     */
    public static String substitute(String path, boolean vertex, String original, SourceLookup lookup) {
        if (original == null || path == null || !path.startsWith(CORE)) return original;
        var replacements = coreShaders;
        if (replacements.isEmpty()) return original;
        String core = path.substring(CORE.length());
        String program = replacements.get(core);
        if (program == null) return original;
        Decision decision = DECISIONS.computeIfAbsent(core, name -> decide(name, program, vertex, original, lookup));
        String translated = decision.stage(vertex);
        if (translated == null) return original;
        SUBSTITUTED.incrementAndGet();
        return translated;
    }

    private static Decision decide(String core, String programName, boolean vertex, String original, SourceLookup lookup) {
        var program = programs.get(programName);
        if (program == null) return Decision.REFUSED;
        try {
            String vertexSource = vertex ? original : lookup.source(true);
            String fragmentSource = vertex ? lookup.source(false) : original;
            if (vertexSource == null || fragmentSource == null) return Decision.REFUSED;
            String translatedVertex = ShaderWorldTranslation.translate(program.vertex(),
                ShaderWorldEnvironment.parse(vertexSource, true), true);
            String translatedFragment = ShaderWorldTranslation.translate(program.fragment(),
                ShaderWorldEnvironment.parse(fragmentSource, false), false);
            if (Boolean.getBoolean("kernel.worldShaders.dump")) try {
                var directory = java.nio.file.Path.of("kernel-world-shader-dump");
                java.nio.file.Files.createDirectories(directory);
                java.nio.file.Files.writeString(directory.resolve(core + ".vsh"), translatedVertex);
                java.nio.file.Files.writeString(directory.resolve(core + ".fsh"), translatedFragment);
            } catch (java.io.IOException ignored) { }
            return new Decision(translatedVertex, translatedFragment);
        } catch (Exception failure) {
            // One line per pack, not per shader: a pack that cannot be translated usually cannot be
            // translated for many draws, and the game keeps rendering normally regardless.
            if (!warned) {
                warned = true;
                LoggerFactory.getLogger("Kernel").info(
                    "Kernel is rendering {} with Minecraft's own programs: {}", core, failure.getMessage());
            }
            return Decision.REFUSED;
        }
    }
}
