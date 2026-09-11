package dev.kernel.fabric.shader;

import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

/** Restores every GL binding/state changed by Kernel, keeping Minecraft's own cached state valid. */
final class ShaderGlState implements AutoCloseable {
    private static final int[] CAPABILITIES = { GL33C.GL_DEPTH_TEST, GL33C.GL_CULL_FACE,
        GL33C.GL_SCISSOR_TEST, GL33C.GL_STENCIL_TEST, GL33C.GL_FRAMEBUFFER_SRGB, GL33C.GL_RASTERIZER_DISCARD,
        GL33C.GL_COLOR_LOGIC_OP, GL33C.GL_DITHER, GL33C.GL_SAMPLE_ALPHA_TO_COVERAGE,
        GL33C.GL_SAMPLE_ALPHA_TO_ONE, GL33C.GL_SAMPLE_COVERAGE, GL33C.GL_SAMPLE_MASK,
        GL33C.GL_CLIP_DISTANCE0, GL33C.GL_CLIP_DISTANCE1, GL33C.GL_CLIP_DISTANCE2, GL33C.GL_CLIP_DISTANCE3,
        GL33C.GL_CLIP_DISTANCE4, GL33C.GL_CLIP_DISTANCE5, GL33C.GL_CLIP_DISTANCE6, GL33C.GL_CLIP_DISTANCE7 };
    private final boolean[] enabled = new boolean[CAPABILITIES.length];
    private final int program = GL33C.glGetInteger(GL33C.GL_CURRENT_PROGRAM);
    private final int vao = GL33C.glGetInteger(GL33C.GL_VERTEX_ARRAY_BINDING);
    private final int draw = GL33C.glGetInteger(GL33C.GL_DRAW_FRAMEBUFFER_BINDING);
    private final int read = GL33C.glGetInteger(GL33C.GL_READ_FRAMEBUFFER_BINDING);
    private final int active = GL33C.glGetInteger(GL33C.GL_ACTIVE_TEXTURE);
    private final int texture;
    private final int sampler;
    private final int unpack = GL33C.glGetInteger(GL33C.GL_PIXEL_UNPACK_BUFFER_BINDING);
    private final int[] viewport = new int[4];
    private final boolean[] color = new boolean[4];
    private final int[] polygon = new int[2];
    private final boolean blend = GL33C.glIsEnabledi(GL33C.GL_BLEND, 0);
    private final boolean clipControl = GL.getCapabilities().OpenGL45 || GL.getCapabilities().GL_ARB_clip_control;
    private final int clipOrigin = clipControl ? GL33C.glGetInteger(GL45C.GL_CLIP_ORIGIN) : 0;
    private final int clipDepth = clipControl ? GL33C.glGetInteger(GL45C.GL_CLIP_DEPTH_MODE) : 0;

    ShaderGlState() {
        GL33C.glGetIntegerv(GL33C.GL_VIEWPORT, viewport);
        GL33C.glGetIntegerv(GL33C.GL_POLYGON_MODE, polygon);
        try (var stack = MemoryStack.stackPush()) {
            var mask = stack.malloc(4); GL33C.glGetBooleani_v(GL33C.GL_COLOR_WRITEMASK, 0, mask);
            for (int i = 0; i < 4; i++) color[i] = mask.get(i) != 0;
        }
        for (int i = 0; i < CAPABILITIES.length; i++) enabled[i] = GL33C.glIsEnabled(CAPABILITIES[i]);
        GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
        texture = GL33C.glGetInteger(GL33C.GL_TEXTURE_BINDING_2D);
        sampler = GL33C.glGetInteger(GL33C.GL_SAMPLER_BINDING);
    }
    void prepare() {
        for (int capability : CAPABILITIES) GL33C.glDisable(capability);
        GL33C.glDisablei(GL33C.GL_BLEND, 0);
        GL33C.glColorMaski(0, true, true, true, true);
        GL33C.glPolygonMode(GL33C.GL_FRONT_AND_BACK, GL33C.GL_FILL);
        GL33C.glBindSampler(0, 0);
        GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER, 0);
        if (clipControl) GL45C.glClipControl(GL45C.GL_LOWER_LEFT, GL45C.GL_NEGATIVE_ONE_TO_ONE);
    }
    @Override public void close() {
        GL33C.glUseProgram(program); GL33C.glBindVertexArray(vao);
        GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, draw);
        GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, read);
        GL33C.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        GL33C.glColorMaski(0, color[0], color[1], color[2], color[3]);
        if (blend) GL33C.glEnablei(GL33C.GL_BLEND, 0); else GL33C.glDisablei(GL33C.GL_BLEND, 0);
        GL33C.glPolygonMode(GL33C.GL_FRONT_AND_BACK, polygon[0]);
        for (int i = 0; i < CAPABILITIES.length; i++) {
            if (enabled[i]) GL33C.glEnable(CAPABILITIES[i]); else GL33C.glDisable(CAPABILITIES[i]);
        }
        GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER, unpack);
        GL33C.glActiveTexture(GL33C.GL_TEXTURE0); GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, texture);
        GL33C.glBindSampler(0, sampler); GL33C.glActiveTexture(active);
        if (clipControl) GL45C.glClipControl(clipOrigin, clipDepth);
    }
}
