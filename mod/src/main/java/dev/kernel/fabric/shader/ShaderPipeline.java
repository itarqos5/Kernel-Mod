package dev.kernel.fabric.shader;

import dev.kernel.fabric.shader.pack.PreparedShaderPack;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;

/** Render-thread-owned fullscreen color pipeline. New packs compile completely before replacing a live one. */
public final class ShaderPipeline implements AutoCloseable {
    private static final Map<String, Integer> UNIFORMS = Map.ofEntries(
        Map.entry("colortex0", GL33C.GL_SAMPLER_2D), Map.entry("gcolor", GL33C.GL_SAMPLER_2D), Map.entry("texture", GL33C.GL_SAMPLER_2D),
        Map.entry("viewWidth", GL33C.GL_FLOAT), Map.entry("viewHeight", GL33C.GL_FLOAT), Map.entry("aspectRatio", GL33C.GL_FLOAT),
        Map.entry("frameCounter", GL33C.GL_INT), Map.entry("frameTime", GL33C.GL_FLOAT), Map.entry("frameTimeCounter", GL33C.GL_FLOAT));
    private record Uniform(String name, int location, int type) {}
    private record Program(int handle, List<Uniform> uniforms) {}
    private final List<Program> programs = new ArrayList<>();
    private final int[] textures = new int[2], framebuffers = new int[2];
    private int sourceFramebuffer, vao, sampler, width, height, frame;
    private final long started = System.nanoTime();
    private long lastFrame = started;
    private boolean closed;

    public ShaderPipeline(PreparedShaderPack pack) throws IOException {
        try (var state = new ShaderGlState()) {
            state.prepare();
            try {
                for (var pass : pack.passes()) programs.add(compile(pass));
                vao = GL33C.glGenVertexArrays(); sourceFramebuffer = GL33C.glGenFramebuffers();
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
        try (var state = new ShaderGlState()) {
            state.prepare(); resize(width, height);
            GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, sourceFramebuffer);
            GL33C.glFramebufferTexture2D(GL33C.GL_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0, GL33C.GL_TEXTURE_2D, sourceTexture, 0);
            GL33C.glDrawBuffer(GL33C.GL_COLOR_ATTACHMENT0); GL33C.glReadBuffer(GL33C.GL_COLOR_ATTACHMENT0);
            complete();
            long now = System.nanoTime(); float delta = (now - lastFrame) * 1.0e-9f;
            float elapsed = ((now - started) * 1.0e-9f) % 3600.0f; lastFrame = now;
            int input = sourceTexture, output = 0;
            GL33C.glBindVertexArray(vao); GL33C.glBindSampler(0, sampler); GL33C.glViewport(0, 0, width, height);
            for (var program : programs) {
                GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, framebuffers[output]);
                GL33C.glUseProgram(program.handle); GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, input);
                for (var uniform : program.uniforms) {
                    if (uniform.type == GL33C.GL_SAMPLER_2D) GL33C.glUniform1i(uniform.location, 0);
                    else if (uniform.type == GL33C.GL_INT) GL33C.glUniform1i(uniform.location, frame);
                    else GL33C.glUniform1f(uniform.location, switch (uniform.name) {
                        case "viewWidth" -> width; case "viewHeight" -> height; case "aspectRatio" -> (float) width / height;
                        case "frameTime" -> delta; case "frameTimeCounter" -> elapsed; default -> throw new AssertionError(uniform.name);
                    });
                }
                GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, 3);
                input = textures[output]; output ^= 1;
            }
            GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, framebuffers[output ^ 1]);
            GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, sourceFramebuffer);
            GL33C.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL33C.GL_COLOR_BUFFER_BIT, GL33C.GL_NEAREST);
            GL33C.glFramebufferTexture2D(GL33C.GL_DRAW_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0, GL33C.GL_TEXTURE_2D, 0, 0);
            frame = (frame + 1) % 720720;
        }
    }
    private void resize(int width, int height) throws IOException {
        if (this.width == width && this.height == height) return;
        releaseTargets();
        try {
            for (int i = 0; i < 2; i++) {
                textures[i] = GL33C.glGenTextures(); GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, textures[i]);
                GL33C.glTexImage2D(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA8, width, height, 0, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, 0L);
                GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_LINEAR);
                GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_LINEAR);
                GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_WRAP_S, GL33C.GL_CLAMP_TO_EDGE);
                GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_WRAP_T, GL33C.GL_CLAMP_TO_EDGE);
                framebuffers[i] = GL33C.glGenFramebuffers(); GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, framebuffers[i]);
                GL33C.glFramebufferTexture2D(GL33C.GL_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0, GL33C.GL_TEXTURE_2D, textures[i], 0);
                GL33C.glDrawBuffer(GL33C.GL_COLOR_ATTACHMENT0); GL33C.glReadBuffer(GL33C.GL_COLOR_ATTACHMENT0); complete();
            }
            this.width = width; this.height = height;
        } catch (IOException | RuntimeException failure) { releaseTargets(); throw failure; }
    }
    private static void complete() throws IOException {
        int status = GL33C.glCheckFramebufferStatus(GL33C.GL_FRAMEBUFFER);
        if (status != GL33C.GL_FRAMEBUFFER_COMPLETE) throw new IOException("Incomplete shader framebuffer: 0x" + Integer.toHexString(status));
    }
    private static Program compile(PreparedShaderPack.Pass pass) throws IOException {
        int vertex = 0, fragment = 0, program = 0;
        try {
            vertex = shader(GL33C.GL_VERTEX_SHADER, pass.vertex(), pass.name() + ".vsh");
            fragment = shader(GL33C.GL_FRAGMENT_SHADER, pass.fragment(), pass.name() + ".fsh");
            program = GL33C.glCreateProgram(); GL33C.glAttachShader(program, vertex); GL33C.glAttachShader(program, fragment);
            var outputs = Pattern.compile("(?m)\\bout\\s+(?:lowp\\s+|mediump\\s+|highp\\s+)?vec4\\s+(\\w+)\\s*;").matcher(pass.fragment());
            int count = 0; String outputName = "";
            while (outputs.find()) { outputName = outputs.group(1); GL33C.glBindFragDataLocation(program, 0, outputName); count++; }
            if (count != 1) throw new IOException(pass.name() + " must declare exactly one vec4 color output");
            GL33C.glLinkProgram(program);
            if (GL33C.glGetProgrami(program, GL33C.GL_LINK_STATUS) == 0) throw new IOException(pass.name() + ": " + GL33C.glGetProgramInfoLog(program, 8192));
            if (GL33C.glGetFragDataLocation(program, outputName) != 0) throw new IOException(pass.name() + " must write color output zero");
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
                    if (size.get(0) != 1 || !Integer.valueOf(type.get(0)).equals(UNIFORMS.get(name))) {
                        throw new IOException(pass.name() + " requires unsupported uniform: " + name);
                    }
                    uniforms.add(new Uniform(name, GL33C.glGetUniformLocation(program, name), type.get(0)));
                }
            }
            var result = new Program(program, List.copyOf(uniforms)); program = 0; return result;
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
    private void releaseTargets() {
        for (int i = 0; i < 2; i++) {
            if (framebuffers[i] != 0) GL33C.glDeleteFramebuffers(framebuffers[i]);
            if (textures[i] != 0) GL33C.glDeleteTextures(textures[i]);
            framebuffers[i] = textures[i] = 0;
        }
        width = height = 0;
    }
    @Override public void close() {
        if (closed) return; closed = true;
        releaseTargets();
        for (var program : programs) GL33C.glDeleteProgram(program.handle);
        programs.clear();
        if (sourceFramebuffer != 0) GL33C.glDeleteFramebuffers(sourceFramebuffer);
        if (vao != 0) GL33C.glDeleteVertexArrays(vao);
        if (sampler != 0) GL33C.glDeleteSamplers(sampler);
        sourceFramebuffer = vao = sampler = 0;
    }
}
