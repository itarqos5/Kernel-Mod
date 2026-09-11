package dev.kernel.fabric.shader;

import dev.kernel.fabric.shader.pack.PreparedShaderPack;
import dev.kernel.fabric.shader.pack.ShaderFragmentOutputs;
import dev.kernel.fabric.shader.pack.ShaderTextureImage;
import dev.kernel.fabric.shader.pack.ShaderUniforms;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.system.MemoryStack;

/** Render-thread-owned fullscreen color pipeline. New packs compile completely before replacing a live one. */
public final class ShaderPipeline implements AutoCloseable {
    private record Uniform(String name, int location, int type, int buffer, int unit) {}
    private record Program(int handle, List<Uniform> uniforms, int[] targets, int written, int mipmaps, int[] inputs) {}
    private final List<Program> programs = new ArrayList<>();
    private ShaderColorTargets targets;
    private ShaderCustomTextures images;
    private int vao, sampler, mipmapSampler, frame, textureUnits, outputSlots;
    private final long started = System.nanoTime();
    private long lastFrame = started;
    private boolean closed, usesWorldData;

    public ShaderPipeline(PreparedShaderPack pack) throws IOException {
        try (var state = new ShaderGlState()) {
            state.prepare();
            try {
                if (pack.textures().size() > 32) throw new IOException("Too many custom shader texture bindings");
                var uniqueImages = new ArrayList<ShaderTextureImage>();
                var identity = new IdentityHashMap<ShaderTextureImage, Integer>();
                var imageBindings = new HashMap<String, Integer>();
                for (var binding : pack.textures().entrySet()) {
                    int index = identity.computeIfAbsent(binding.getValue(), image -> { uniqueImages.add(image); return uniqueImages.size() + 15; });
                    imageBindings.put(binding.getKey(), index);
                }
                for (var pass : pack.passes()) programs.add(compile(pass, imageBindings));
                int required = 1, mipmaps = 0;
                long sampled = 0;
                boolean legacyDepth = false;
                for (var program : programs) {
                    required |= program.written;
                    mipmaps |= program.mipmaps;
                    for (var uniform : program.uniforms) if (uniform.buffer < 0 && ShaderUniforms.isWorldInput(uniform.name)) usesWorldData = true;
                    for (var uniform : program.uniforms) if (uniform.buffer >= 0) {
                        sampled |= 1L << uniform.buffer;
                        legacyDepth |= uniform.buffer == 1 && uniform.name.equals("gdepth");
                    }
                }
                required |= (int) sampled & 0xffff;
                for (var program : programs) textureUnits = Math.max(textureUnits, program.inputs.length);
                outputSlots = 1;
                for (var program : programs) for (int slot = 0; slot < program.targets.length; slot++)
                    if ((required & (1 << program.targets[slot])) != 0) outputSlots = Math.max(outputSlots, slot + 1);
                if (textureUnits > GL33C.glGetInteger(GL33C.GL_MAX_TEXTURE_IMAGE_UNITS)
                    || outputSlots > GL33C.glGetInteger(GL33C.GL_MAX_DRAW_BUFFERS))
                    throw new IOException("This shader exceeds the graphics device's texture/output limits");
                images = new ShaderCustomTextures(uniqueImages, sampled);
                targets = new ShaderColorTargets(required, legacyDepth ? pack.buffers().withLegacyDepth() : pack.buffers(), mipmaps, images.bytes());
                vao = GL33C.glGenVertexArrays();
                sampler = sampler(false);
                if (mipmaps != 0) mipmapSampler = sampler(true);
            } catch (IOException | RuntimeException failure) { close(); throw failure; }
        }
    }
    public boolean needsWorldData() { return usesWorldData; }
    public void render(int sourceTexture, int width, int height) throws IOException {
        render(sourceTexture, width, height, null);
    }
    public void render(int sourceTexture, int width, int height, ShaderWorldData world) throws IOException {
        if (closed) throw new IOException("Shader pipeline is closed");
        if (sourceTexture <= 0 || width <= 0 || height <= 0) return;
        if (usesWorldData && world == null) throw new IOException("This shader requires current world inputs");
        try (var state = new ShaderGlState(Math.max(1, textureUnits), outputSlots)) {
            state.prepare(); targets.begin(sourceTexture, width, height);
            long now = System.nanoTime(); float delta = (now - lastFrame) * 1.0e-9f;
            float elapsed = ((now - started) * 1.0e-9f) % 3600.0f; lastFrame = now;
            GL33C.glBindVertexArray(vao); GL33C.glViewport(0, 0, width, height);
            for (var program : programs) {
                GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
                targets.mipmaps(program.mipmaps);
                targets.outputs(program.targets);
                GL33C.glUseProgram(program.handle);
                for (int unit = 0; unit < program.inputs.length; unit++) {
                    int input = program.inputs[unit];
                    GL33C.glActiveTexture(GL33C.GL_TEXTURE0 + unit);
                    GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, input < 16 ? targets.texture(input) : images.texture(input - 16));
                    GL33C.glBindSampler(unit, input >= 16 ? images.sampler(input - 16)
                        : (program.mipmaps & (1 << input)) == 0 ? sampler : mipmapSampler);
                }
                for (var uniform : program.uniforms) {
                    if (uniform.buffer >= 0) GL33C.glUniform1i(uniform.location, uniform.unit);
                    else if (uniform.type == GL33C.GL_INT) GL33C.glUniform1i(uniform.location, switch (uniform.name) {
                        case "frameCounter" -> frame; case "worldTime" -> world.worldTime(); case "worldDay" -> world.worldDay();
                        case "moonPhase" -> world.moonPhase(); default -> throw new AssertionError(uniform.name);
                    });
                    else GL33C.glUniform1f(uniform.location, switch (uniform.name) {
                        case "viewWidth" -> width; case "viewHeight" -> height; case "aspectRatio" -> (float) width / height;
                        case "frameTime" -> delta; case "frameTimeCounter" -> elapsed;
                        case "rainStrength" -> world.rainStrength(); case "thunderStrength" -> world.thunderStrength();
                        default -> throw new AssertionError(uniform.name);
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
    private static Program compile(PreparedShaderPack.Pass pass, Map<String, Integer> imageBindings) throws IOException {
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
            var inputUnits = new LinkedHashMap<Integer, Integer>();
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
                    int color = ShaderUniforms.colorBuffer(name);
                    int buffer = imageBindings.getOrDefault(color >= 0 ? "colortex" + color : name, color);
                    if (buffer >= 16 && color >= 0 && (pass.mipmaps() & (1 << color)) != 0)
                        throw new IOException(pass.name() + " requests color mipmaps for an overridden image: " + name);
                    int expectedType = buffer >= 0 ? GL33C.GL_SAMPLER_2D : ShaderUniforms.scalarType(name);
                    if (size.get(0) != 1 || type.get(0) != expectedType) {
                        throw new IOException(pass.name() + " requires unsupported uniform: " + name);
                    }
                    int unit = buffer < 0 ? -1 : inputUnits.computeIfAbsent(buffer, ignored -> inputUnits.size());
                    uniforms.add(new Uniform(name, GL33C.glGetUniformLocation(program, name), type.get(0), buffer, unit));
                }
            }
            int sampled = 0;
            for (var uniform : uniforms) if (uniform.buffer >= 0 && uniform.buffer < 16) sampled |= 1 << uniform.buffer;
            var result = new Program(program, List.copyOf(uniforms), pass.drawTargets().stream().mapToInt(Integer::intValue).toArray(), written,
                pass.mipmaps() & sampled, inputUnits.keySet().stream().mapToInt(Integer::intValue).toArray()); program = 0; return result;
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
    private static int sampler(boolean mipmaps) {
        int sampler = GL33C.glGenSamplers();
        GL33C.glSamplerParameteri(sampler, GL33C.GL_TEXTURE_MIN_FILTER, mipmaps ? GL33C.GL_LINEAR_MIPMAP_LINEAR : GL33C.GL_LINEAR);
        GL33C.glSamplerParameteri(sampler, GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_LINEAR);
        GL33C.glSamplerParameteri(sampler, GL33C.GL_TEXTURE_WRAP_S, GL33C.GL_CLAMP_TO_EDGE);
        GL33C.glSamplerParameteri(sampler, GL33C.GL_TEXTURE_WRAP_T, GL33C.GL_CLAMP_TO_EDGE);
        return sampler;
    }
    @Override public void close() {
        if (closed) return; closed = true;
        if (targets != null) targets.close();
        if (images != null) images.close();
        for (var program : programs) GL33C.glDeleteProgram(program.handle);
        programs.clear();
        if (vao != 0) GL33C.glDeleteVertexArrays(vao);
        if (sampler != 0) GL33C.glDeleteSamplers(sampler);
        if (mipmapSampler != 0) GL33C.glDeleteSamplers(mipmapSampler);
        vao = sampler = mipmapSampler = 0;
    }
}
