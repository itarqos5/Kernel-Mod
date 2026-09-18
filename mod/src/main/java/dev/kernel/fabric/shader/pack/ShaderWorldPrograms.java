package dev.kernel.fabric.shader.pack;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Iris world programs and the vanilla core shaders they replace.
 *
 * <p>This is the naming and resolution layer for the world stage. It decides, for a core shader
 * Minecraft is about to compile, which program of a pack takes its place, and which program a pack
 * actually ships when it does not ship that one. It does not compile, translate or draw anything.
 *
 * <p>Only core shader names that exist across the targets Kernel can substitute on are mapped. A name
 * this class does not know is never substituted, so a version that renames or removes a core shader
 * quietly keeps vanilla rendering instead of drawing something the pack did not describe.
 */
public final class ShaderWorldPrograms {
    /**
     * The fallback each program resolves to when a pack does not ship it, after the Iris format.
     * {@code gbuffers_basic} is the root and falls back to nothing.
     */
    private static final Map<String, String> FALLBACK = Map.ofEntries(
        Map.entry("gbuffers_textured", "gbuffers_basic"),
        Map.entry("gbuffers_textured_lit", "gbuffers_textured"),
        Map.entry("gbuffers_skybasic", "gbuffers_basic"),
        Map.entry("gbuffers_skytextured", "gbuffers_textured"),
        Map.entry("gbuffers_clouds", "gbuffers_textured"),
        Map.entry("gbuffers_beaconbeam", "gbuffers_textured"),
        Map.entry("gbuffers_terrain", "gbuffers_textured_lit"),
        Map.entry("gbuffers_entities", "gbuffers_textured_lit"),
        Map.entry("gbuffers_hand", "gbuffers_textured_lit"),
        Map.entry("gbuffers_weather", "gbuffers_textured_lit"),
        Map.entry("gbuffers_block", "gbuffers_terrain"),
        Map.entry("gbuffers_damagedblock", "gbuffers_terrain"),
        Map.entry("gbuffers_water", "gbuffers_terrain"));

    /**
     * The program that replaces each vanilla core shader.
     *
     * <p>Every name here exists on 1.21.5 and on 26.2, so the mapping does not depend on a version that
     * renamed a core shader. Terrain, entity and particle rendering carry the bulk of a world pass;
     * the position-only programs are the untextured and simple textured draws.
     */
    private static final Map<String, String> CORE_SHADERS = Map.ofEntries(
        Map.entry("terrain", "gbuffers_terrain"),
        Map.entry("entity", "gbuffers_entities"),
        Map.entry("particle", "gbuffers_textured_lit"),
        Map.entry("rendertype_clouds", "gbuffers_clouds"),
        Map.entry("rendertype_beacon_beam", "gbuffers_beaconbeam"),
        Map.entry("rendertype_lightning", "gbuffers_basic"),
        Map.entry("rendertype_crumbling", "gbuffers_damagedblock"),
        Map.entry("position", "gbuffers_basic"),
        Map.entry("position_color", "gbuffers_basic"),
        Map.entry("position_tex", "gbuffers_textured"),
        Map.entry("position_tex_color", "gbuffers_textured"));

    private ShaderWorldPrograms() {}

    /** Every world program name Kernel recognises, including the root. */
    public static Set<String> names() {
        var names = new LinkedHashSet<>(CORE_SHADERS.values());
        names.addAll(FALLBACK.keySet());
        names.addAll(FALLBACK.values());
        return Set.copyOf(names);
    }

    /** The vanilla core shaders Kernel substitutes, keyed by the name under {@code minecraft:core/}. */
    public static Map<String, String> coreShaders() { return CORE_SHADERS; }

    /** The program replacing a vanilla core shader, or null when Kernel leaves that shader alone. */
    public static String forCoreShader(String coreShaderName) { return CORE_SHADERS.get(coreShaderName); }

    /**
     * Walks the fallback chain from a program to the root, starting with the program itself.
     *
     * <p>The chain is finite by construction, but it is also guarded, so a future edit that introduced a
     * cycle would produce a short chain rather than hang the render thread.
     */
    public static List<String> chain(String program) {
        var chain = new LinkedHashSet<String>();
        String current = program;
        while (current != null && chain.add(current)) current = FALLBACK.get(current);
        return List.copyOf(chain);
    }

    /**
     * The first program in {@code program}'s fallback chain that {@code available} contains, or null.
     *
     * <p>A pack that ships none of them keeps vanilla rendering for that draw.
     */
    public static String resolve(String program, Set<String> available) {
        for (String candidate : chain(program)) if (available.contains(candidate)) return candidate;
        return null;
    }

    /**
     * Maps each substitutable core shader to the program a pack supplies for it.
     *
     * <p>Core shaders the pack cannot supply are left out rather than mapped to nothing, so a caller
     * iterating the result only sees draws the pack actually replaces.
     */
    public static Map<String, String> resolveAll(Set<String> available) {
        var resolved = new LinkedHashMap<String, String>();
        for (var entry : CORE_SHADERS.entrySet()) {
            String program = resolve(entry.getValue(), available);
            if (program != null) resolved.put(entry.getKey(), program);
        }
        return Map.copyOf(resolved);
    }
}
