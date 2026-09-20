package dev.kernel.fabric.shader.pack;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Immutable CPU-side preparation. Only complete, supported pipelines reach the GPU compiler. */
public record PreparedShaderPack(String filename, List<Pass> passes, ShaderBufferSettings buffers,
                                 java.util.Map<String, ShaderTextureImage> textures, List<ShaderOption> options,
                                 ShaderProperties properties, String dimension,
                                 java.util.Map<String, WorldProgram> worldPrograms,
                                 ShaderIdentifierMaps identifiers) {
    /** The ordered fullscreen stages Kernel runs, matching the Iris order of deferred before composite. */
    private static final Pattern PASS = Pattern.compile("(?:world-?[0-9]+/)?(?:deferred|composite)(?:[1-9]|[1-9][0-9])?\\.(?:vsh|fsh)"
        + "|(?:world-?[0-9]+/)?final\\.(?:vsh|fsh)");
    private static final Pattern UNSUPPORTED = Pattern.compile("\\b(?:superSamplingLevel|noiseTextureResolution|GAUX4FORMAT)\\b");
    /** Stages Kernel does not run at all: a pack needing them is refused rather than partly drawn. */
    private static final Pattern REFUSED_STAGE = Pattern.compile("(?:world-?[0-9]+/)?(?:shadow(?:comp)?[0-9]*|prepare[0-9]*)\\.(?:vsh|fsh|gsh|csh|tcs|tes)");
    /** World programs Kernel can substitute for a Minecraft core shader. */
    private static final Pattern GBUFFERS = Pattern.compile("(?:world-?[0-9]+/)?gbuffers_[A-Za-z_0-9]+\\.(?:vsh|fsh)");
    public static final int MAX_PASSES = 32;
    /** Opt-in for the incomplete world stage; see docs/SHADER_WORLD_STAGE.md. */
    public static final String WORLD_STAGE_PROPERTY = "kernel.worldShaders";
    /** The dimension folders Iris-format packs use. */
    public static final String OVERWORLD = "world0", NETHER = "world-1", END = "world1";

    public PreparedShaderPack {
        passes = List.copyOf(passes);
        java.util.Objects.requireNonNull(buffers);
        textures = java.util.Map.copyOf(textures);
        options = List.copyOf(options);
        java.util.Objects.requireNonNull(properties);
        java.util.Objects.requireNonNull(dimension);
        worldPrograms = Map.copyOf(worldPrograms);
        java.util.Objects.requireNonNull(identifiers);
    }

    /**
     * One world program a pack ships, expanded with its options applied but not yet translated.
     *
     * <p>Translation needs the environment of the Minecraft program being replaced, which is only known
     * once the game asks for that shader, so it happens at substitution time rather than here. Both
     * stages are always present: a program supplying only one would replace half of a Minecraft program
     * pair and leave the varyings of the two halves disagreeing.
     */
    public record WorldProgram(String name, String vertex, String fragment) {
        public WorldProgram {
            java.util.Objects.requireNonNull(name);
            java.util.Objects.requireNonNull(vertex);
            java.util.Objects.requireNonNull(fragment);
        }
    }
    public PreparedShaderPack(String filename, List<Pass> passes) { this(filename, passes, ShaderBufferSettings.defaults()); }
    public PreparedShaderPack(String filename, List<Pass> passes, ShaderBufferSettings buffers) { this(filename, passes, buffers, java.util.Map.of()); }
    public PreparedShaderPack(String filename, List<Pass> passes, ShaderBufferSettings buffers, java.util.Map<String, ShaderTextureImage> textures) {
        this(filename, passes, buffers, textures, List.of(), ShaderProperties.empty(), "", Map.of(), ShaderIdentifierMaps.empty());
    }
    public record Pass(String name, String vertex, String fragment, List<Integer> drawTargets, int mipmaps) {
        public Pass {
            if ((mipmaps & ~0xffff) != 0) throw new IllegalArgumentException("Mipmap mask exceeds sixteen buffers");
            drawTargets = List.copyOf(drawTargets);
            if (drawTargets.isEmpty() || drawTargets.size() > ShaderDrawTargets.OUTPUT_COUNT)
                throw new IllegalArgumentException("A pass requires one to eight color targets");
            int mask = 0;
            for (int target : drawTargets) {
                if (target < 0 || target >= ShaderDrawTargets.BUFFER_COUNT || (mask & (1 << target)) != 0)
                    throw new IllegalArgumentException("Color targets must be distinct indices from 0 to 15");
                mask |= 1 << target;
            }
            if (name.equals("final") && !drawTargets.equals(List.of(0)))
                throw new IllegalArgumentException("The final pass writes the displayed color image");
        }
        public Pass(String name, String vertex, String fragment) {
            this(name, vertex, fragment, ShaderDrawTargets.defaults(name.equals("final")));
        }
        public Pass(String name, String vertex, String fragment, List<Integer> drawTargets) {
            this(name, vertex, fragment, drawTargets, 0);
        }
    }

    /** Returns the dimension folder name Iris-format packs use for a Minecraft dimension key path. */
    public static String dimensionFolder(String dimensionPath) {
        return switch (dimensionPath) {
            case "the_nether" -> NETHER;
            case "the_end" -> END;
            default -> OVERWORLD;
        };
    }

    /** Returns the program stages this pack ships that Kernel cannot render at all, in archive order. */
    public static List<String> worldStages(ShaderPackArchive archive) {
        var stages = new ArrayList<String>();
        for (String file : archive.files()) if (REFUSED_STAGE.matcher(file).matches()) stages.add(file);
        return List.copyOf(stages);
    }

    public static PreparedShaderPack read(Path path) throws IOException {
        return read(path, OVERWORLD, Map.of());
    }

    /**
     * Prepares one pack for a dimension, applying the stored option values the pack declares.
     *
     * <p>Options are applied before buffer directives and draw targets are read, because a pack routinely
     * guards those declarations behind its own options.
     */
    public static PreparedShaderPack read(Path path, String dimension, Map<String, String> optionValues) throws IOException {
        try (var archive = new ShaderPackArchive(path)) {
            // World-stage substitution is incomplete: programs compile and run, but what they draw has
            // not been verified correct. Until it has, a pack shipping them is refused exactly as before,
            // because accepting one and drawing it wrongly is worse than declining it with a reason.
            boolean worldStage = Boolean.getBoolean(WORLD_STAGE_PROPERTY);
            var refused = new ArrayList<>(worldStages(archive));
            if (!worldStage) for (String file : archive.files()) if (GBUFFERS.matcher(file).matches()) refused.add(file);
            if (!refused.isEmpty()) throw new IOException("This pack needs rendering stages Kernel does not run: "
                + String.join(", ", refused.subList(0, Math.min(3, refused.size())))
                + (refused.size() > 3 ? " and " + (refused.size() - 3) + " more" : ""));
            for (String file : archive.files()) {
                if (file.matches(".*\\.(?:vsh|fsh|gsh|csh|tcs|tes)")
                    && !PASS.matcher(file).matches() && !GBUFFERS.matcher(file).matches()) {
                    throw new IOException("This pack requires an unsupported rendering stage: " + file);
                }
            }
            var properties = ShaderProperties.read(archive, dimension);
            var identifiers = ShaderIdentifierMaps.read(archive);
            var textures = PreparedShaderTextures.read(archive);
            var passes = new ArrayList<Pass>();
            var buffers = new ShaderBufferDirectives();
            var options = new LinkedHashMap<String, ShaderOption>();
            for (String name : stageNames()) {
                String fragmentPath = archive.resolve(dimension, name + ".fsh");
                String vertexPath = archive.resolve(dimension, name + ".vsh");
                if (fragmentPath == null && vertexPath == null) continue;
                if (fragmentPath == null) throw new IOException("Missing fragment shader: " + name + ".fsh");
                if (!properties.programEnabled(name, optionValues)) continue;
                if (passes.size() >= MAX_PASSES) throw new IOException("Kernel currently supports at most " + MAX_PASSES + " deferred, composite and final passes");
                String fragmentSource = archive.expand(fragmentPath).source();
                String vertexSource = vertexPath == null ? ShaderSource.DEFAULT_VERTEX : archive.expand(vertexPath).source();
                for (var option : ShaderOptions.discover(fragmentSource)) options.putIfAbsent(option.name(), option);
                if (vertexPath != null) for (var option : ShaderOptions.discover(vertexSource)) options.putIfAbsent(option.name(), option);
                fragmentSource = ShaderOptions.apply(fragmentSource, optionValues);
                if (vertexPath != null) vertexSource = ShaderOptions.apply(vertexSource, optionValues);
                if (UNSUPPORTED.matcher(fragmentSource).find() || UNSUPPORTED.matcher(vertexSource).find())
                    throw new IOException("This pack requires unsupported buffer configuration in " + name);
                int mipmaps = buffers.read(fragmentSource, name + ".fsh", true);
                buffers.read(vertexSource, name + ".vsh", false);
                var drawTargets = ShaderDrawTargets.read(fragmentSource, name.equals("final"));
                passes.add(new Pass(name, vertexPath != null ? ShaderSource.translate(vertexSource, true) : vertexSource,
                    ShaderSource.translate(fragmentSource, false), drawTargets, mipmaps));
            }
            if (passes.isEmpty()) throw new IOException("No supported deferred, composite or final shader programs were found");
            var world = new LinkedHashMap<String, WorldProgram>();
            for (String name : worldStage ? ShaderWorldPrograms.names() : java.util.Set.<String>of()) {
                String vertexPath = archive.resolve(dimension, name + ".vsh");
                String fragmentPath = archive.resolve(dimension, name + ".fsh");
                // Both stages are required. Replacing only one half of a Minecraft program pair would
                // leave the two halves disagreeing about their varyings.
                if (vertexPath == null || fragmentPath == null) continue;
                if (!properties.programEnabled(name, optionValues)) continue;
                String vertexSource = archive.expand(vertexPath).source();
                String fragmentSource = archive.expand(fragmentPath).source();
                for (var option : ShaderOptions.discover(vertexSource)) options.putIfAbsent(option.name(), option);
                for (var option : ShaderOptions.discover(fragmentSource)) options.putIfAbsent(option.name(), option);
                world.put(name, new WorldProgram(name, ShaderOptions.apply(vertexSource, optionValues),
                    ShaderOptions.apply(fragmentSource, optionValues)));
            }
            var declared = new ArrayList<ShaderOption>();
            for (var option : options.values()) declared.add(option.asSlider(properties.sliders().contains(option.name())));
            return new PreparedShaderPack(path.getFileName().toString(), passes, buffers.build(), textures,
                order(declared, properties), properties, dimension, world, identifiers);
        }
    }

    /** Lists every fullscreen stage in the order Kernel runs it. */
    private static List<String> stageNames() {
        var names = new ArrayList<String>();
        for (String stage : List.of("deferred", "composite")) {
            names.add(stage);
            for (int index = 1; index <= 99; index++) names.add(stage + index);
        }
        names.add("final");
        return names;
    }

    /**
     * Chooses which discovered options the pack actually offers, in the pack own order.
     *
     * <p>A pack that lays out its own option screens is taken at its word: only the options it lists are
     * offered. That matters because every include guard in the pack is also a bare {@code #define} and
     * would otherwise appear as a setting. A pack with no declared screens offers everything discovered,
     * minus names that read as include guards.
     */
    private static List<ShaderOption> order(List<ShaderOption> discovered, ShaderProperties properties) {
        var byName = new LinkedHashMap<String, ShaderOption>();
        for (var option : discovered) byName.put(option.name(), option);
        var layout = properties.layout();
        if (!layout.isEmpty()) {
            var ordered = new ArrayList<ShaderOption>();
            for (String name : layout) {
                var option = byName.get(name);
                if (option != null) ordered.add(option);
            }
            return List.copyOf(ordered);
        }
        var ordered = new ArrayList<ShaderOption>();
        for (var option : byName.values()) if (!includeGuard(option)) ordered.add(option);
        return List.copyOf(ordered);
    }

    /** Recognises the names packs conventionally use for include guards rather than for settings. */
    private static boolean includeGuard(ShaderOption option) {
        if (option.kind() != ShaderOption.Kind.BOOLEAN || !option.defaultValue().equals("true")) return false;
        String name = option.name();
        return name.endsWith("_GLSL") || name.endsWith("_INCLUDED") || name.endsWith("_FSH") || name.endsWith("_VSH")
            || name.endsWith("_H") || name.startsWith("INCLUDE_") || name.startsWith("_");
    }
}
