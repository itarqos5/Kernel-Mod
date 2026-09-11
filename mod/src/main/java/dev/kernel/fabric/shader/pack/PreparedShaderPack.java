package dev.kernel.fabric.shader.pack;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Immutable CPU-side preparation. Only complete, supported pipelines reach the GPU compiler. */
public record PreparedShaderPack(String filename, List<Pass> passes) {
    private static final Pattern PASS = Pattern.compile("(?:composite(?:[1-9]|[1-9][0-9])?|final)\\.(?:vsh|fsh)");
    public record Pass(String name, String vertex, String fragment, List<Integer> drawTargets) {
        public Pass {
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
    }

    public static PreparedShaderPack read(Path path) throws IOException {
        try (var archive = new ShaderPackArchive(path)) {
            for (String file : archive.files()) {
                if (file.matches(".*\\.(?:vsh|fsh|gsh|csh|tcs|tes)") && !PASS.matcher(file).matches()) {
                    throw new IOException("This pack requires an unsupported rendering stage: " + file);
                }
            }
            if (archive.contains("shaders.properties") && !archive.source("shaders.properties").lines()
                .allMatch(line -> line.isBlank() || line.stripLeading().startsWith("#"))) {
                throw new IOException("This pack requires shader properties that Kernel does not support yet");
            }
            var passes = new ArrayList<Pass>();
            for (int index = 0; index <= 100; index++) {
                String name = index == 100 ? "final" : index == 0 ? "composite" : "composite" + index;
                boolean fragment = archive.contains(name + ".fsh"), vertex = archive.contains(name + ".vsh");
                if (!fragment && !vertex) continue;
                if (!fragment) throw new IOException("Missing fragment shader: " + name + ".fsh");
                if (passes.size() >= 16) throw new IOException("Kernel currently supports at most 16 composite/final passes");
                String fragmentSource = archive.expand(name + ".fsh").source();
                if (Pattern.compile("\\b(?:colortex[0-9]+|gcolor|gdepth|gnormal|composite|gaux[1-4])(?:Format|Clear|ClearColor|MipmapEnabled)\\b|\\b(?:superSamplingLevel|noiseTextureResolution|GAUX4FORMAT)\\b")
                    .matcher(fragmentSource).find()) throw new IOException("This pack requires unsupported buffer configuration in " + name);
                var drawTargets = ShaderDrawTargets.read(fragmentSource, name.equals("final"));
                passes.add(new Pass(name, vertex ? ShaderSource.translate(archive.expand(name + ".vsh").source(), true)
                    : ShaderSource.DEFAULT_VERTEX, ShaderSource.translate(fragmentSource, false), drawTargets));
            }
            if (passes.isEmpty()) throw new IOException("No supported composite or final shader programs were found");
            return new PreparedShaderPack(path.getFileName().toString(), List.copyOf(passes));
        }
    }
}
