package dev.kernel.fabric.shader;

import dev.kernel.fabric.shader.pack.ShaderTextureImage;
import java.io.IOException;
import java.util.List;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryUtil;

/** Render-thread ownership of only the custom images actually sampled by compiled programs. */
final class ShaderCustomTextures implements AutoCloseable {
    private final int[] textures, samplers;
    private long bytes;
    ShaderCustomTextures(List<ShaderTextureImage> images, long usedBindings) throws IOException {
        textures = new int[images.size()]; samplers = new int[images.size()];
        try {
            int limit = GL33C.glGetInteger(GL33C.GL_MAX_TEXTURE_SIZE);
            for (int index = 0; index < images.size(); index++) {
                if ((usedBindings & (1L << (index + 16))) == 0) continue;
                var image = images.get(index);
                if (image.width() > limit || image.height() > limit) throw new IOException("Custom texture exceeds the graphics device's size limit");
                bytes += image.bytes();
                if (bytes > 128L * 1024 * 1024) throw new IOException("Custom shader textures exceed the 128 MiB allocation budget");
                int texture = textures[index] = GL33C.glGenTextures();
                GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, texture);
                var upload = MemoryUtil.memAlloc(image.bytes());
                try {
                    upload.put(image.pixels()).flip();
                    GL33C.glTexImage2D(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA8, image.width(), image.height(), 0,
                        GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, upload);
                } finally { MemoryUtil.memFree(upload); }
                if (GL33C.glGetTexLevelParameteri(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_TEXTURE_WIDTH) != image.width()
                    || GL33C.glGetTexLevelParameteri(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_TEXTURE_HEIGHT) != image.height())
                    throw new IOException("The graphics device could not allocate a custom shader texture");
                GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MAX_LEVEL, 0);
                int sampler = samplers[index] = GL33C.glGenSamplers();
                GL33C.glSamplerParameteri(sampler, GL33C.GL_TEXTURE_MIN_FILTER, image.blur() ? GL33C.GL_LINEAR : GL33C.GL_NEAREST);
                GL33C.glSamplerParameteri(sampler, GL33C.GL_TEXTURE_MAG_FILTER, image.blur() ? GL33C.GL_LINEAR : GL33C.GL_NEAREST);
                GL33C.glSamplerParameteri(sampler, GL33C.GL_TEXTURE_WRAP_S, image.clamp() ? GL33C.GL_CLAMP_TO_EDGE : GL33C.GL_REPEAT);
                GL33C.glSamplerParameteri(sampler, GL33C.GL_TEXTURE_WRAP_T, image.clamp() ? GL33C.GL_CLAMP_TO_EDGE : GL33C.GL_REPEAT);
            }
        } catch (IOException | RuntimeException failure) { close(); throw failure; }
    }
    long bytes() { return bytes; }
    int texture(int index) { return textures[index]; }
    int sampler(int index) { return samplers[index]; }
    @Override public void close() {
        for (int index = 0; index < textures.length; index++) {
            if (textures[index] != 0) GL33C.glDeleteTextures(textures[index]);
            if (samplers[index] != 0) GL33C.glDeleteSamplers(samplers[index]);
            textures[index] = samplers[index] = 0;
        }
        bytes = 0;
    }
}
