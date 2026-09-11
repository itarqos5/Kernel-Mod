package dev.kernel.fabric.shader;

import dev.kernel.fabric.shader.pack.PreparedShaderPack;
import dev.kernel.fabric.shader.pack.ShaderFragmentOutputs;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.system.MemoryStack;

/** Render-thread-owned fullscreen color pipeline. New packs compile completely before replacing a live one. */
public final class ShaderPipeline implements AutoCloseable {
    private static final Map<String, Integer> UNIFORMS = Map.ofEntries(
        Map.entry("viewWidth", GL33C.GL_FLOAT), Map.entry("viewHeight", GL33C.GL_FLOAT), Map.entry("aspectRatio", GL33C.GL_FLOAT),
        Map.entry("frameCounter", GL33C.GL_INT), Map.entry("frameTime", GL33C.GL_FLOAT), Map.entry("frameTimeCounter", GL33C.GL_FLOAT));
    private record Uniform(String name, int location, int type, int buffer) {}
    private record Program(int handle, List<Uniform> uniforms, int[] targets, int written) {}
    private final List<Program> programs = new ArrayList<>();
    private final int[] samplerBuffers = new int[16], unitForBuffer = new int[16];
    private ShaderColorTargets targets;
    private int vao, sampler, frame, textureUnits, outputSlots;
    private final long started = System.nanoTime();
    private long lastFrame = started;
    private boolean closed;

    public ShaderPipeline(PreparedShaderPack pack) throws IOException {
        try (var state = new ShaderGlState()) {
            state.prepare();
            try {
                for (var pass : pack.passes()) programs.add(compile(pass));
                int required = 1, sampled = 0;
                boolean legacyDepth = false;
                for (var program : programs) {
                    required |= program.written;
                    for (var uniform : program.uniforms) if (uniform.buffer >= 0) {
                        sampled |= 1 << uniform.buffer;
                        legacyDepth |= uniform.name.equals("gdepth");
                    }
                }
                required |= sampled;
                Arrays.fill(unitForBuffer, -1);
                for (int buffer = 0; buffer < 16; buffer++) if ((sampled & (1 << buffer)) != 0) {
                    samplerBuffers[textureUnits] = buffer; unitForBuffer[buffer] = textureUnits++;
                }
                outputSlots = 1;
                for (var program : programs) for (int slot = 0; slot < program.targets.length; slot++)
                    if ((required & (1 << program.targets[slot])) != 0) outputSlots = Math.max(outputSlots, slot + 1);
                if (textureUnits > GL33C.glGetInteger(GL33C.GL_MAX_TEXTURE_IMAGE_UNITS)
                    || outputSlots > GL33C.glGetInteger(GL33C.GL_MAX_DRAW_BUFFERS))
                    throw new IOException("This shader exceeds the graphics device's texture/output limits");
                targets = new ShaderColorTargets(required, legacyDepth ? pack.buffers().withLegacyDepth() : pack.buffers());
                vao = GL33C.glGenVertexArrays();
                sampler = GL33C.glGenSamplers();
                GL33C.glSamplerParameteri(sampler, GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_LINEAR);
                GL33C.glSamplerParameteri(sampler, GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_LINEAR);
                GL33C.glSamplerParameteri(sampler, GL33C.GL_TEXTURE_WRAP_S, GL33C.GL_CLAMP_TO_EDGE);
                GL33C.glSamplerParameteri(sampler, GL33C.GL_TEXTURE_WRAP_T, GL33C.GL_CLAMP_TO_EDGE);
            } catch (IOException | RuntimeException failure) { close(); throw failure; }
        }
    }
    public void render(int sourceTexture, int width, int height) throws IOException {
        if (closed) throw new IOException("Shader pipeline is closed");
        if (sourceTexture <= 0 || width <= 0 || height <= 0) return;
        try (var state = new ShaderGlState(Math.max(1, textureUnits), outputSlots)) {
            state.prepare(); targets.begin(sourceTexture, width, height);
            long now = System.nanoTime(); float delta = (now - lastFrame) * 1.0e-9f;
            float elapsed = ((now - started) * 1.0e-9f) % 3600.0f; lastFrame = now;
            GL33C.glBindVertexArray(vao); GL33C.glViewport(0, 0, width, height);
            for (var program : programs) {
                targets.outputs(program.targets);
                GL33C.glUseProgram(program.handle);
                for (int unit = 0; unit < textureUnits; unit++) {
                    GL33C.glActiveTexture(GL33C.GL_TEXTURE0 + unit);
                    GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, targets.texture(samplerBuffers[unit]));
                    GL33C.glBindSampler(unit, sampler);
                }
                for (var uniform : program.uniforms) {
                    if (uniform.buffer >= 0) GL33C.glUniform1i(uniform.location, unitForBuffer[uniform.buffer]);
                    else if (uniform.type == GL33C.GL_INT) GL33C.glUniform1i(uniform.location, frame);
                    else GL33C.glUniform1f(uniform.location, switch (uniform.name) {
                        case "viewWidth" -> width; case "viewHeight" -> height; case "aspectRatio" -> (float) width / height;
                        case "frameTime" -> delta; case "frameTimeCounter" -> elapsed; default -> throw new AssertionError(uniform.name);
                    });
                }
                GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, 3);
                targets.flip(program.targets);
            }
            targets.finish(sourceTexture);
            frame = (frame + 1) % 720720;
        }
    }
    /** Invalidates retained auxiliary images without changing the selected programs. */
    public void resetHistory() { if (targets != null) targets.resetHistory(); }
    private static Program compile(PreparedShaderPack.Pass pass) throws IOException {
        var names = ShaderFragmentOutputs.read(pass.fragment());
        int vertex = 0, fragment = 0, program = 0;
        try {
            vertex = shader(GL33C.GL_VERTEX_SHADER, pass.vertex(), pass.name() + ".vsh");
            fragment = shader(GL33C.GL_FRAGMENT_SHADER, pass.fragment(), pass.name() + ".fsh");
            program = GL33C.glCreateProgram(); GL33C.glAttachShader(program, vertex); GL33C.glAttachShader(program, fragment);
            for (String name : names) {
                var numbered = Pattern.compile("(?:outColor|kernel_fragColor)([0-7])").matcher(name);
                int location = numbered.matches() ? Integer.parseInt(numbered.group(1)) : 0;
                GL33C.glBindFragDataLocation(program, location, name);
            }
            GL33C.glLinkProgram(program);
            if (GL33C.glGetProgrami(program, GL33C.GL_LINK_STATUS) == 0) throw new IOException(pass.name() + ": " + GL33C.glGetProgramInfoLog(program, 8192));
            int written = 0, locations = 0;
            for (String name : names) {
                int location = GL33C.glGetFragDataLocation(program, name);
                if (location < 0) continue; // An inactive preprocessor branch need not declare an output.
                if (location >= pass.drawTargets().size() || location >= 8 || (locations & (1 << location)) != 0)
                    throw new IOException(pass.name() + " has an unmapped or overlapping output location: " + location);
                locations |= 1 << location; written |= 1 << pass.drawTargets().get(location);
            }
            if (written == 0) throw new IOException(pass.name() + " has no active color output");
            if (GL33C.glGetProgrami(program, GL33C.GL_ACTIVE_UNIFORM_BLOCKS) != 0
                || (GL.getCapabilities().OpenGL43 && GL43C.glGetProgramInterfacei(program, GL43C.GL_SHADER_STORAGE_BLOCK, GL43C.GL_ACTIVE_RESOURCES) != 0))
                throw new IOException(pass.name() + " requires unsupported shader buffer bindings");
            var uniforms = new ArrayList<Uniform>();
            try (var stack = MemoryStack.stackPush()) {
                var size = stack.mallocInt(1); var type = stack.mallocInt(1);
                for (int i = 0, n = GL33C.glGetProgrami(program, GL33C.GL_ACTIVE_ATTRIBUTES); i < n; i++) {
                    String name = GL33C.glGetActiveAttrib(program, i, size, type);
                    // Some drivers report shader-generated vertex IDs in this list; they consume no buffer binding.
                    if (!name.equals("gl_VertexID") && !name.equals("gl_InstanceID"))
                        throw new IOException(pass.name() + " requires unsupported vertex attribute: " + name);
                }
                for (int i = 0, n = GL33C.glGetProgrami(program, GL33C.GL_ACTIVE_UNIFORMS); i < n; i++) {
                    String name = GL33C.glGetActiveUniform(program, i, size, type);
                    int buffer = samplerBuffer(name);
                    int expectedType = buffer >= 0 ? GL33C.GL_SAMPLER_2D : UNIFORMS.getOrDefault(name, -1);
                    if (size.get(0) != 1 || type.get(0) != expectedType) {
                        throw new IOException(pass.name() + " requires unsupported uniform: " + name);
                    }
                    uniforms.add(new Uniform(name, GL33C.glGetUniformLocation(program, name), type.get(0), buffer));
                }
            }
            var result = new Program(program, List.copyOf(uniforms), pass.drawTargets().stream().mapToInt(Integer::intValue).toArray(), written); program = 0; return result;
        } finally {
            if (program != 0) GL33C.glDeleteProgram(program);
            if (vertex != 0) GL33C.glDeleteShader(vertex); if (fragment != 0) GL33C.glDeleteShader(fragment);
        }
    }
    private static int shader(int type, String source, String name) throws IOException {
        int shader = GL33C.glCreateShader(type);
        try {
            GL33C.glShaderSource(shader, source); GL33C.glCompileShader(shader);
            if (GL33C.glGetShaderi(shader, GL33C.GL_COMPILE_STATUS) == 0) throw new IOException(name + ": " + GL33C.glGetShaderInfoLog(shader, 8192));
            return shader;
        } catch (IOException | RuntimeException failure) { GL33C.glDeleteShader(shader); throw failure; }
    }
    private static int samplerBuffer(String name) {
        return switch (name) {
            case "gcolor", "texture" -> 0;
            case "gdepth" -> 1;
            case "gnormal" -> 2; case "composite" -> 3;
            case "gaux1" -> 4; case "gaux2" -> 5; case "gaux3" -> 6; case "gaux4" -> 7;
            default -> {
                if (!name.matches("colortex(?:[0-9]|1[0-5])")) yield -1;
                yield Integer.parseInt(name.substring(8));
            }
        };
    }
    @Override public void close() {
        if (closed) return; closed = true;
        if (targets != null) targets.close();
        for (var program : programs) GL33C.glDeleteProgram(program.handle);
        programs.clear();
        if (vao != 0) GL33C.glDeleteVertexArrays(vao);
        if (sampler != 0) GL33C.glDeleteSamplers(sampler);
        vao = sampler = 0;
    }
}
