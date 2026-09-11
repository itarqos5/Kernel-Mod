package dev.kernel.fabric.shader;

import dev.kernel.fabric.shader.pack.ShaderSource;
import java.io.IOException;
import org.lwjgl.opengl.GL33C;

/** A frame-local, forward-depth snapshot. Native depth images are only ever sampled. */
final class ShaderDepthTarget implements AutoCloseable {
    private int texture, framebuffer, program, vao, sampler, width, height;
    private int countLocation, reverseLocation, handLocation;
    private final int[] sourceLocations = new int[6];
    private boolean ready;

    ShaderDepthTarget() throws IOException { initialize(); }
    void invalidate() { ready = false; }
    int texture(int width, int height) throws IOException {
        if (!ready || this.width != width || this.height != height)
            throw new IOException("This shader requires depth captured during the current world frame");
        return texture;
    }
    int sampler() { return sampler; }

    void capture(int[] sources, int width, int height, boolean reverse, boolean hand) throws IOException {
        if (program == 0) throw new IOException("Shader depth capture is closed");
        if (sources.length < 1 || sources.length > 6 || (hand && sources.length != 1))
            throw new IOException("Invalid native shader depth inputs");
        if (hand) texture(width, height);
        else invalidate();
        try (var state = new ShaderGlState(sources.length, 1)) {
            state.prepare();
            resize(width, height);
            GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, framebuffer);
            GL33C.glDrawBuffer(GL33C.GL_COLOR_ATTACHMENT0);
            if (GL33C.glCheckFramebufferStatus(GL33C.GL_DRAW_FRAMEBUFFER) != GL33C.GL_FRAMEBUFFER_COMPLETE)
                throw new IOException("The graphics device could not attach the shader depth image");
            GL33C.glUseProgram(program); GL33C.glBindVertexArray(vao);
            GL33C.glViewport(0, 0, width, height);
            GL33C.glUniform1i(countLocation, sources.length);
            GL33C.glUniform1i(reverseLocation, reverse ? 1 : 0);
            GL33C.glUniform1i(handLocation, hand ? 1 : 0);
            for (int index = 0; index < sources.length; index++) {
                if (sources[index] <= 0 || sources[index] == texture || !GL33C.glIsTexture(sources[index]))
                    throw new IOException("Native shader depth input is unavailable");
                GL33C.glActiveTexture(GL33C.GL_TEXTURE0 + index);
                GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, sources[index]);
                if (GL33C.glGetTexLevelParameteri(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_TEXTURE_WIDTH) != width
                    || GL33C.glGetTexLevelParameteri(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_TEXTURE_HEIGHT) != height)
                    throw new IOException("Native shader depth dimensions do not match the world image");
                GL33C.glBindSampler(index, sampler);
            }
            // All unused sampler uniforms point at unit zero, whose texture remains valid.
            for (int index = 0; index < 6; index++)
                GL33C.glUniform1i(sourceLocations[index], index < sources.length ? index : 0);
            GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, 3);
            ready = true;
        } catch (IOException | RuntimeException failure) { ready = false; throw failure; }
    }

    private void initialize() throws IOException {
        if (program != 0) return;
        int vertex = 0, fragment = 0;
        try {
            vertex = ShaderPipeline.shader(GL33C.GL_VERTEX_SHADER, ShaderSource.DEFAULT_VERTEX, "Kernel depth vertex");
            StringBuilder source = new StringBuilder("#version 330 core\nuniform int count, reversed, hand;\n");
            for (int index = 0; index < 6; index++) source.append("uniform sampler2D source").append(index).append(";\n");
            source.append("""
                layout(location=0) out float depth;
                float forwardDepth(float d) { return reversed != 0 ? 1.0-d : d; }
                void main() {
                    ivec2 pixel = ivec2(gl_FragCoord.xy);
                    float first = texelFetch(source0, pixel, 0).r;
                    // Compare the native clear value before reversing, preserving tiny reverse-Z depths.
                    if (hand != 0 && first == (reversed != 0 ? 0.0 : 1.0)) discard;
                    depth = forwardDepth(first);
                """);
            for (int index = 1; index < 6; index++) source.append("if (count > ").append(index)
                .append(") depth=min(depth,forwardDepth(texelFetch(source").append(index).append(",pixel,0).r));\n");
            source.append("}\n");
            fragment = ShaderPipeline.shader(GL33C.GL_FRAGMENT_SHADER, source.toString(), "Kernel depth capture");
            program = GL33C.glCreateProgram();
            GL33C.glAttachShader(program, vertex); GL33C.glAttachShader(program, fragment); GL33C.glLinkProgram(program);
            if (GL33C.glGetProgrami(program, GL33C.GL_LINK_STATUS) == 0)
                throw new IOException("Kernel depth capture: " + GL33C.glGetProgramInfoLog(program, 8192));
            countLocation = GL33C.glGetUniformLocation(program, "count");
            reverseLocation = GL33C.glGetUniformLocation(program, "reversed");
            handLocation = GL33C.glGetUniformLocation(program, "hand");
            for (int index = 0; index < 6; index++) sourceLocations[index] = GL33C.glGetUniformLocation(program, "source" + index);
            vao = GL33C.glGenVertexArrays(); sampler = GL33C.glGenSamplers();
            GL33C.glSamplerParameteri(sampler, GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_NEAREST);
            GL33C.glSamplerParameteri(sampler, GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_NEAREST);
            GL33C.glSamplerParameteri(sampler, GL33C.GL_TEXTURE_WRAP_S, GL33C.GL_CLAMP_TO_EDGE);
            GL33C.glSamplerParameteri(sampler, GL33C.GL_TEXTURE_WRAP_T, GL33C.GL_CLAMP_TO_EDGE);
            GL33C.glSamplerParameteri(sampler, GL33C.GL_TEXTURE_COMPARE_MODE, GL33C.GL_NONE);
        } catch (IOException | RuntimeException failure) { close(); throw failure; }
        finally {
            if (vertex != 0) GL33C.glDeleteShader(vertex);
            if (fragment != 0) GL33C.glDeleteShader(fragment);
        }
    }

    private void resize(int width, int height) throws IOException {
        if (texture != 0 && this.width == width && this.height == height) return;
        if (width <= 0 || height <= 0 || (long) width * height > 512L * 1024 * 1024 / 4)
            throw new IOException("Shader depth dimensions exceed the allocation budget");
        int limit = GL33C.glGetInteger(GL33C.GL_MAX_TEXTURE_SIZE);
        if (width > limit || height > limit) throw new IOException("Shader depth dimensions exceed the graphics device's texture limit");
        releaseImage();
        try {
            texture = GL33C.glGenTextures(); framebuffer = GL33C.glGenFramebuffers();
            GL33C.glActiveTexture(GL33C.GL_TEXTURE0); GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, texture);
            GL33C.glTexImage2D(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_R32F, width, height, 0, GL33C.GL_RED, GL33C.GL_FLOAT, 0L);
            if (GL33C.glGetTexLevelParameteri(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_TEXTURE_WIDTH) != width
                || GL33C.glGetTexLevelParameteri(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_TEXTURE_HEIGHT) != height)
                throw new IOException("The graphics device could not allocate the shader depth image");
            GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MAX_LEVEL, 0);
            GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, framebuffer);
            GL33C.glFramebufferTexture2D(GL33C.GL_DRAW_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0, GL33C.GL_TEXTURE_2D, texture, 0);
            this.width = width; this.height = height;
        } catch (IOException | RuntimeException failure) { releaseImage(); throw failure; }
    }
    private void releaseImage() {
        if (framebuffer != 0) GL33C.glDeleteFramebuffers(framebuffer);
        if (texture != 0) GL33C.glDeleteTextures(texture);
        framebuffer = texture = width = height = 0; ready = false;
    }
    @Override public void close() {
        releaseImage();
        if (program != 0) GL33C.glDeleteProgram(program);
        if (vao != 0) GL33C.glDeleteVertexArrays(vao);
        if (sampler != 0) GL33C.glDeleteSamplers(sampler);
        program = vao = sampler = 0;
    }
}
