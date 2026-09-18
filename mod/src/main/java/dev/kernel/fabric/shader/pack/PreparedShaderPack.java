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
                                 ShaderProperties properties, String dimension) {
    /** The ordered fullscreen stages Kernel runs, matching the Iris order of deferred before composite. */
    private static final Pattern PASS = Pattern.compile("(?:world-?[0-9]+/)?(?:deferred|composite)(?:[1-9]|[1-9][0-9])?\\.(?:vsh|fsh)"
        + "|(?:world-?[0-9]+/)?final\\.(?:vsh|fsh)");
    private static final Pattern UNSUPPORTED = Pattern.compile("\\b(?:superSamplingLevel|noiseTextureResolution|GAUX4FORMAT)\\b");
    /** Stages that draw or consume world geometry, which Kernel does not render yet. */
    private static final Pattern WORLD_STAGE = Pattern.compile("(?:world-?[0-9]+/)?(?:gbuffers_[A-Za-z_0-9]+|shadow(?:comp)?[0-9]*|prepare[0-9]*)\\.(?:vsh|fsh|gsh|csh|tcs|tes)");
    public static final int MAX_PASSES = 32;
    /** The dimension folders Iris-format packs use. */
    public static final String OVERWORLD = "world0", NETHER = "world-1", END = "world1";

    public PreparedShaderPack {
        passes = List.copyOf(passes);
        java.util.Objects.requireNonNull(buffers);
        textures = java.util.Map.copyOf(textures);
        options = List.copyOf(options);
        java.util.Objects.requireNonNull(properties);
        java.util.Objects.requireNonNull(dimension);
    }
    public PreparedShaderPack(String filename, List<Pass> passes) { this(filename, passes, ShaderBufferSettings.defaults()); }
    public PreparedShaderPack(String filename, List<Pass> passes, ShaderBufferSettings buffers) { this(filename, passes, buffers, java.util.Map.of()); }
    public PreparedShaderPack(String filename, List<Pass> passes, ShaderBufferSettings buffers, java.util.Map<String, ShaderTextureImage> textures) {
        this(filename, passes, buffers, textures, List.of(), ShaderProperties.empty(), "");
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

    /** Returns the program stages this pack ships that Kernel cannot render yet, in archive order. */
    public static List<String> worldStages(ShaderPackArchive archive) {
        var stages = new ArrayList<String>();
        for (String file : archive.files()) if (WORLD_STAGE.matcher(file).matches()) stages.add(file);
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
            var worldStages = worldStages(archive);
            if (!worldStages.isEmpty()) throw new IOException("This pack draws world geometry, which Kernel does not render yet: "
                + String.join(", ", worldStages.subList(0, Math.min(3, worldStages.size())))
                + (worldStages.size() > 3 ? " and " + (worldStages.size() - 3) + " more" : ""));
            for (String file : archive.files()) {
                if (file.matches(".*\\.(?:vsh|fsh|gsh|csh|tcs|tes)") && !PASS.matcher(file).matches()) {
                    throw new IOException("This pack requires an unsupported rendering stage: " + file);
                }
            }
            var properties = ShaderProperties.read(archive, dimension);
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
            var declared = new ArrayList<ShaderOption>();
            for (var option : options.values()) declared.add(option.asSlider(properties.sliders().contains(option.name())));
            return new PreparedShaderPack(path.getFileName().toString(), passes, buffers.build(), textures,
                order(declared, properties), properties, dimension);
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
