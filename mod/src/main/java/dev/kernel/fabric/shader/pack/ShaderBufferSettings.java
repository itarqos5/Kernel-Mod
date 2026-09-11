package dev.kernel.fabric.shader.pack;

import java.util.ArrayList;
import java.util.List;

/** Immutable settings for sixteen logical color attachments. */
public record ShaderBufferSettings(List<Buffer> buffers) {
    public record Color(float red, float green, float blue, float alpha) {
        public Color {
            if (!Float.isFinite(red) || !Float.isFinite(green) || !Float.isFinite(blue) || !Float.isFinite(alpha))
                throw new IllegalArgumentException("Buffer clear colors must be finite");
        }
        public float[] array() { return new float[]{red, green, blue, alpha}; }
    }
    public record Buffer(ShaderColorFormat format, boolean explicitFormat, boolean clear, Color color) {
        public Buffer { java.util.Objects.requireNonNull(format); java.util.Objects.requireNonNull(color); }
    }
    public ShaderBufferSettings {
        buffers = List.copyOf(buffers);
        if (buffers.size() != 16) throw new IllegalArgumentException("Sixteen logical color attachments are required");
    }
    public static ShaderBufferSettings defaults() {
        var buffers = new ArrayList<Buffer>(16);
        for (int index = 0; index < 16; index++) buffers.add(new Buffer(ShaderColorFormat.RGBA8, false, true,
            index == 1 ? new Color(1,1,1,1) : new Color(0,0,0,0)));
        return new ShaderBufferSettings(buffers);
    }
    public ShaderBufferSettings withLegacyDepth() {
        Buffer previous = buffers.get(1);
        if (previous.explicitFormat()) return this;
        var upgraded = new ArrayList<>(buffers);
        upgraded.set(1, new Buffer(ShaderColorFormat.RGBA32F, false, previous.clear(), previous.color()));
        return new ShaderBufferSettings(upgraded);
    }
    public long bytesPerPixel(int required) {
        long bytes = 0;
        for (int buffer = 0; buffer < 16; buffer++) if ((required & (1 << buffer)) != 0)
            bytes += 2L * buffers.get(buffer).format().bytes();
        return bytes;
    }
}
