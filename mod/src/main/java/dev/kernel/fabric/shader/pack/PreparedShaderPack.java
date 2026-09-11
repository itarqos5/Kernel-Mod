package dev.kernel.fabric.shader.pack;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Immutable CPU-side preparation. Only complete, supported pipelines reach the GPU compiler. */
public record PreparedShaderPack(String filename, List<Pass> passes, ShaderBufferSettings buffers) {
    private static final Pattern PASS = Pattern.compile("(?:composite(?:[1-9]|[1-9][0-9])?|final)\\.(?:vsh|fsh)");
    private static final Pattern UNSUPPORTED = Pattern.compile("\\b(?:superSamplingLevel|noiseTextureResolution|GAUX4FORMAT)\\b");
    public PreparedShaderPack {
        passes = List.copyOf(passes);
        java.util.Objects.requireNonNull(buffers);
    }
    public PreparedShaderPack(String filename, List<Pass> passes) { this(filename, passes, ShaderBufferSettings.defaults()); }
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
            var buffers = new ShaderBufferDirectives();
            for (int index = 0; index <= 100; index++) {
                String name = index == 100 ? "final" : index == 0 ? "composite" : "composite" + index;
                boolean fragment = archive.contains(name + ".fsh"), vertex = archive.contains(name + ".vsh");
                if (!fragment && !vertex) continue;
                if (!fragment) throw new IOException("Missing fragment shader: " + name + ".fsh");
                if (passes.size() >= 16) throw new IOException("Kernel currently supports at most 16 composite/final passes");
                String fragmentSource = archive.expand(name + ".fsh").source();
                String vertexSource = vertex ? archive.expand(name + ".vsh").source() : ShaderSource.DEFAULT_VERTEX;
                if (UNSUPPORTED.matcher(fragmentSource).find() || UNSUPPORTED.matcher(vertexSource).find())
                    throw new IOException("This pack requires unsupported buffer configuration in " + name);
                int mipmaps = buffers.read(fragmentSource, name + ".fsh", true);
                buffers.read(vertexSource, name + ".vsh", false);
                var drawTargets = ShaderDrawTargets.read(fragmentSource, name.equals("final"));
                passes.add(new Pass(name, vertex ? ShaderSource.translate(vertexSource, true) : vertexSource,
                    ShaderSource.translate(fragmentSource, false), drawTargets, mipmaps));
            }
            if (passes.isEmpty()) throw new IOException("No supported composite or final shader programs were found");
            return new PreparedShaderPack(path.getFileName().toString(), passes, buffers.build());
        }
    }
}
