package dev.kernel.fabric.shader.pack;

import org.lwjgl.opengl.GL33C;

/** Sized, noninteger color formats with explicit allocation accounting. */
public enum ShaderColorFormat {
    R8(GL33C.GL_R8, GL33C.GL_RED, GL33C.GL_UNSIGNED_BYTE, 1, 1, false),
    RG8(GL33C.GL_RG8, GL33C.GL_RG, GL33C.GL_UNSIGNED_BYTE, 2, 2, false),
    RGBA8(GL33C.GL_RGBA8, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, 4, 4, false),
    R16(GL33C.GL_R16, GL33C.GL_RED, GL33C.GL_UNSIGNED_SHORT, 2, 1, false),
    RG16(GL33C.GL_RG16, GL33C.GL_RG, GL33C.GL_UNSIGNED_SHORT, 4, 2, false),
    RGBA16(GL33C.GL_RGBA16, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_SHORT, 8, 4, false),
    R16F(GL33C.GL_R16F, GL33C.GL_RED, GL33C.GL_FLOAT, 2, 1, true),
    RG16F(GL33C.GL_RG16F, GL33C.GL_RG, GL33C.GL_FLOAT, 4, 2, true),
    RGBA16F(GL33C.GL_RGBA16F, GL33C.GL_RGBA, GL33C.GL_FLOAT, 8, 4, true),
    R32F(GL33C.GL_R32F, GL33C.GL_RED, GL33C.GL_FLOAT, 4, 1, true),
    RG32F(GL33C.GL_RG32F, GL33C.GL_RG, GL33C.GL_FLOAT, 8, 2, true),
    RGBA32F(GL33C.GL_RGBA32F, GL33C.GL_RGBA, GL33C.GL_FLOAT, 16, 4, true);

    private final int internal, external, type, bytes, channels;
    private final boolean floating;
    ShaderColorFormat(int internal, int external, int type, int bytes, int channels, boolean floating) {
        this.internal=internal; this.external=external; this.type=type; this.bytes=bytes; this.channels=channels; this.floating=floating;
    }
    public int internal() { return internal; }
    public int external() { return external; }
    public int type() { return type; }
    public int bytes() { return bytes; }
    public int channels() { return channels; }
    public boolean floating() { return floating; }
}
