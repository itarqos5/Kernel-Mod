package dev.kernel.fabric.shader;

import java.io.IOException;
import java.util.Arrays;
import dev.kernel.fabric.shader.pack.ShaderBufferSettings;
import dev.kernel.fabric.shader.pack.ShaderColorFormat;
import org.lwjgl.opengl.GL33C;

/** Owns bounded color-buffer pairs; a pass always reads one image and writes the other. */
final class ShaderColorTargets implements AutoCloseable {
    private static final long MAX_BYTES = 512L * 1024 * 1024;
    private final int required;
    private final int mipmaps;
    private final long availableBytes;
    private final ShaderBufferSettings settings;
    private final float[][] clearColors = new float[16][];
    private final int[] textures = new int[32], front = new int[16], current = new int[16];
    private final int[] drawBuffers = new int[8];
    private int drawFramebuffer, readFramebuffer, sourceFramebuffer, width, height;
    private boolean resetHistory = true;

    ShaderColorTargets(int required, ShaderBufferSettings settings) {
        this(required, settings, 0);
    }
    ShaderColorTargets(int required, ShaderBufferSettings settings, int mipmaps) {
        this(required, settings, mipmaps, 0);
    }
    ShaderColorTargets(int required, ShaderBufferSettings settings, int mipmaps, long reservedBytes) {
        if (reservedBytes < 0 || reservedBytes > MAX_BYTES) throw new IllegalArgumentException("Invalid reserved shader memory");
        availableBytes = MAX_BYTES - reservedBytes;
        this.required = required | 1; this.settings = settings;
        this.mipmaps = mipmaps & this.required;
        for (int buffer = 0; buffer < 16; buffer++) clearColors[buffer] = settings.buffers().get(buffer).color().array();
    }
    int texture(int buffer) { return current[buffer]; }
    void resetHistory() { resetHistory = true; }

    void begin(int source, int width, int height) throws IOException {
        resize(width, height);
        GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        for (int slot = 0; slot < 8; slot++) attach(GL33C.GL_DRAW_FRAMEBUFFER, slot, 0);
        GL33C.glDrawBuffer(GL33C.GL_COLOR_ATTACHMENT0);
        for (int buffer = 0; buffer < 16; buffer++) {
            if ((required & (1 << buffer)) == 0) continue;
            if (!resetHistory && !settings.buffers().get(buffer).clear()) continue;
            front[buffer] = 0;
            current[buffer] = textures[buffer * 2];
            for (int side = 0; side < 2; side++) {
                attach(GL33C.GL_DRAW_FRAMEBUFFER, 0, textures[buffer * 2 + side]);
                complete(GL33C.GL_DRAW_FRAMEBUFFER);
                GL33C.glClearBufferfv(GL33C.GL_COLOR, 0, clearColors[buffer]);
            }
        }
        if (settings.buffers().getFirst().format() == ShaderColorFormat.RGBA8 && (mipmaps & 1) == 0) current[0] = source;
        else blit(source, current[0]); // Apply declared channel/precision semantics before the first read.
        resetHistory = false;
    }

    void mipmaps(int requested) {
        for (int buffer = 0; buffer < 16; buffer++) if ((requested & mipmaps & (1 << buffer)) != 0) {
            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, current[buffer]);
            GL33C.glGenerateMipmap(GL33C.GL_TEXTURE_2D);
        }
    }

    void outputs(int[] buffers) throws IOException {
        GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        int count = 0;
        Arrays.fill(drawBuffers, GL33C.GL_NONE);
        for (int slot = 0; slot < 8; slot++) {
            int buffer = slot < buffers.length ? buffers[slot] : -1;
            boolean allocated = buffer >= 0 && (required & (1 << buffer)) != 0;
            attach(GL33C.GL_DRAW_FRAMEBUFFER, slot, allocated ? textures[buffer * 2 + (front[buffer] ^ 1)] : 0);
            if (allocated) { drawBuffers[slot] = GL33C.GL_COLOR_ATTACHMENT0 + slot; count = slot + 1; }
        }
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var selected = stack.mallocInt(count);
            for (int slot = 0; slot < count; slot++) selected.put(slot, drawBuffers[slot]);
            GL33C.glDrawBuffers(selected);
        }
        complete(GL33C.GL_DRAW_FRAMEBUFFER);
    }

    void flip(int[] buffers) {
        for (int buffer : buffers) if ((required & (1 << buffer)) != 0) {
            front[buffer] ^= 1;
            current[buffer] = textures[buffer * 2 + front[buffer]];
        }
    }

    void finish(int source) throws IOException {
        if (current[0] == source) return;
        blit(current[0], source);
    }

    private void blit(int from, int to) throws IOException {
        try {
            GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, readFramebuffer);
            attach(GL33C.GL_READ_FRAMEBUFFER, 0, from);
            GL33C.glReadBuffer(GL33C.GL_COLOR_ATTACHMENT0);
            complete(GL33C.GL_READ_FRAMEBUFFER);
            GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, sourceFramebuffer);
            attach(GL33C.GL_DRAW_FRAMEBUFFER, 0, to);
            GL33C.glDrawBuffer(GL33C.GL_COLOR_ATTACHMENT0);
            complete(GL33C.GL_DRAW_FRAMEBUFFER);
            GL33C.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL33C.GL_COLOR_BUFFER_BIT, GL33C.GL_NEAREST);
        } finally {
            GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, readFramebuffer);
            attach(GL33C.GL_READ_FRAMEBUFFER, 0, 0);
            GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, sourceFramebuffer);
            attach(GL33C.GL_DRAW_FRAMEBUFFER, 0, 0);
        }
    }

    private void resize(int width, int height) throws IOException {
        if (this.width == width && this.height == height) return;
        if (width <= 0 || height <= 0 || settings.allocationBytes(required, mipmaps, width, height) > availableBytes)
            throw new IOException("Shader color buffers exceed the 512 MiB allocation budget at this resolution");
        int limit = GL33C.glGetInteger(GL33C.GL_MAX_TEXTURE_SIZE);
        if (width > limit || height > limit) throw new IOException("Shader dimensions exceed the graphics device's texture limit");
        close();
        try {
            drawFramebuffer = GL33C.glGenFramebuffers(); readFramebuffer = GL33C.glGenFramebuffers();
            sourceFramebuffer = GL33C.glGenFramebuffers();
            for (int buffer = 0; buffer < 16; buffer++) if ((required & (1 << buffer)) != 0) {
                var format = settings.buffers().get(buffer).format();
                for (int side = 0; side < 2; side++) {
                    int texture = GL33C.glGenTextures(); textures[buffer * 2 + side] = texture;
                    GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, texture);
                    GL33C.glTexImage2D(GL33C.GL_TEXTURE_2D, 0, format.internal(), width, height, 0, format.external(), format.type(), 0L);
                    allocated(0, width, height);
                    GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_LINEAR);
                    GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_LINEAR);
                    GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_WRAP_S, GL33C.GL_CLAMP_TO_EDGE);
                    GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_WRAP_T, GL33C.GL_CLAMP_TO_EDGE);
                    int levels = (mipmaps & (1 << buffer)) == 0 ? 0 : 31 - Integer.numberOfLeadingZeros(Math.max(width, height));
                    GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MAX_LEVEL, levels);
                    // Bound and allocate complete chains before any pass starts sampling them.
                    for (int level = 1, w = width, h = height; level <= levels; level++) {
                        w = Math.max(1, w / 2); h = Math.max(1, h / 2);
                        GL33C.glTexImage2D(GL33C.GL_TEXTURE_2D, level, format.internal(), w, h, 0, format.external(), format.type(), 0L);
                        allocated(level, w, h);
                    }
                }
            }
            this.width = width; this.height = height;
        } catch (IOException | RuntimeException failure) { close(); throw failure; }
    }

    private static void allocated(int level, int width, int height) throws IOException {
        if (GL33C.glGetTexLevelParameteri(GL33C.GL_TEXTURE_2D, level, GL33C.GL_TEXTURE_WIDTH) != width
            || GL33C.glGetTexLevelParameteri(GL33C.GL_TEXTURE_2D, level, GL33C.GL_TEXTURE_HEIGHT) != height)
            throw new IOException("The graphics device could not allocate a shader color image");
    }

    private static void attach(int target, int slot, int texture) {
        GL33C.glFramebufferTexture2D(target, GL33C.GL_COLOR_ATTACHMENT0 + slot, GL33C.GL_TEXTURE_2D, texture, 0);
    }
    private static void complete(int target) throws IOException {
        int status = GL33C.glCheckFramebufferStatus(target);
        if (status != GL33C.GL_FRAMEBUFFER_COMPLETE) throw new IOException("Incomplete shader framebuffer: 0x" + Integer.toHexString(status));
    }
    @Override public void close() {
        if (drawFramebuffer != 0) GL33C.glDeleteFramebuffers(drawFramebuffer);
        if (readFramebuffer != 0) GL33C.glDeleteFramebuffers(readFramebuffer);
        if (sourceFramebuffer != 0) GL33C.glDeleteFramebuffers(sourceFramebuffer);
        for (int texture : textures) if (texture != 0) GL33C.glDeleteTextures(texture);
        Arrays.fill(textures, 0); Arrays.fill(current, 0);
        Arrays.fill(front, 0); resetHistory = true;
        drawFramebuffer = readFramebuffer = sourceFramebuffer = width = height = 0;
    }
}
