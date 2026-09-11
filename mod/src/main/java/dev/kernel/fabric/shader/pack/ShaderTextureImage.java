package dev.kernel.fabric.shader.pack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Immutable CPU pixels, decoded with Minecraft's existing STB runtime after bounded PNG preflight. */
public final class ShaderTextureImage {
    public static final int MAX_BYTES = 64 * 1024 * 1024;
    public static final int MAX_ENCODED_BYTES = 16 * 1024 * 1024;
    private final int width, height;
    private final byte[] rgba;
    private final boolean blur, clamp;
    private ShaderTextureImage(int width, int height, byte[] rgba, boolean blur, boolean clamp) {
        this.width = width; this.height = height; this.rgba = rgba; this.blur = blur; this.clamp = clamp;
    }
    public int width() { return width; }
    public int height() { return height; }
    public int bytes() { return rgba.length; }
    public boolean blur() { return blur; }
    public boolean clamp() { return clamp; }
    public ByteBuffer pixels() { return ByteBuffer.wrap(rgba).asReadOnlyBuffer(); }

    public static int decodedBytes(byte[] encoded) throws IOException {
        if (encoded.length > MAX_ENCODED_BYTES) throw new IOException("PNG texture exceeds the 16 MiB file limit");
        if (encoded.length < 33) throw new IOException("Incomplete PNG texture header");
        var header = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN);
        if (header.getLong(0) != 0x89504e470d0a1a0aL || header.getInt(8) != 13 || header.getInt(12) != 0x49484452)
            throw new IOException("Custom shader textures must be PNG images");
        var crc = new CRC32(); crc.update(encoded, 12, 17);
        if ((int) crc.getValue() != header.getInt(29)) throw new IOException("PNG texture header checksum differs");
        int width = header.getInt(16), height = header.getInt(20);
        long bytes = (long) width * height * 4;
        if (width <= 0 || height <= 0 || width > 16384 || height > 16384 || bytes > MAX_BYTES)
            throw new IOException("Custom texture exceeds the 64 MiB / 16384-pixel limits");
        return (int) bytes;
    }
    public static ShaderTextureImage decode(byte[] encoded, boolean blur, boolean clamp) throws IOException {
        int expected = decodedBytes(encoded);
        PngTextureBounds.validate(encoded);
        var header = ByteBuffer.wrap(encoded);
        ByteBuffer input = MemoryUtil.memAlloc(encoded.length), decoded = null;
        try (var stack = MemoryStack.stackPush()) {
            input.put(encoded).flip();
            var width = stack.mallocInt(1); var height = stack.mallocInt(1); var channels = stack.mallocInt(1);
            // The preparation worker is owned by Kernel. Do not change STB's process-wide flip setting.
            STBImage.stbi_set_flip_vertically_on_load_thread(0);
            decoded = STBImage.stbi_load_from_memory(input, width, height, channels, 4);
            if (decoded == null) throw new IOException("Could not decode PNG texture: " + STBImage.stbi_failure_reason());
            if (width.get(0) != header.getInt(16) || height.get(0) != header.getInt(20) || decoded.remaining() != expected)
                throw new IOException("Decoded PNG dimensions differ from its header");
            // STB's free binding uses the buffer's current address. Keep its position at the allocation base.
            byte[] pixels = new byte[expected]; decoded.get(0, pixels);
            return new ShaderTextureImage(width.get(0), height.get(0), pixels, blur, clamp);
        } finally {
            if (decoded != null) STBImage.stbi_image_free(decoded);
            MemoryUtil.memFree(input);
        }
    }
}
