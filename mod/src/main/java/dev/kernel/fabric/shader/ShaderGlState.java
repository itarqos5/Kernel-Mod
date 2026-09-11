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
    private final int[] textures;
    private final int[] samplers;
    private final int unpack = GL33C.glGetInteger(GL33C.GL_PIXEL_UNPACK_BUFFER_BINDING);
    private static final int[] UNPACK_STATES = { GL33C.GL_UNPACK_ROW_LENGTH, GL33C.GL_UNPACK_SKIP_ROWS,
        GL33C.GL_UNPACK_SKIP_PIXELS, GL33C.GL_UNPACK_ALIGNMENT };
    private final int[] unpackValues = new int[UNPACK_STATES.length];
    private final int[] viewport = new int[4];
    private final boolean[] color;
    private final int[] polygon = new int[2];
    private final boolean[] blend;
    private final boolean clipControl = GL.getCapabilities().OpenGL45 || GL.getCapabilities().GL_ARB_clip_control;
    private final int clipOrigin = clipControl ? GL33C.glGetInteger(GL45C.GL_CLIP_ORIGIN) : 0;
    private final int clipDepth = clipControl ? GL33C.glGetInteger(GL45C.GL_CLIP_DEPTH_MODE) : 0;

    ShaderGlState() { this(1, 1); }
    ShaderGlState(int textureUnits, int outputs) {
        textures = new int[textureUnits]; samplers = new int[textureUnits];
        color = new boolean[outputs * 4]; blend = new boolean[outputs];
        GL33C.glGetIntegerv(GL33C.GL_VIEWPORT, viewport);
        GL33C.glGetIntegerv(GL33C.GL_POLYGON_MODE, polygon);
        for (int index = 0; index < UNPACK_STATES.length; index++) unpackValues[index] = GL33C.glGetInteger(UNPACK_STATES[index]);
        try (var stack = MemoryStack.stackPush()) {
            var mask = stack.malloc(4);
            for (int output = 0; output < outputs; output++) {
                GL33C.glGetBooleani_v(GL33C.GL_COLOR_WRITEMASK, output, mask);
                for (int channel = 0; channel < 4; channel++) color[output * 4 + channel] = mask.get(channel) != 0;
                blend[output] = GL33C.glIsEnabledi(GL33C.GL_BLEND, output);
            }
        }
        for (int i = 0; i < CAPABILITIES.length; i++) enabled[i] = GL33C.glIsEnabled(CAPABILITIES[i]);
        for (int unit = 0; unit < textureUnits; unit++) {
            GL33C.glActiveTexture(GL33C.GL_TEXTURE0 + unit);
            textures[unit] = GL33C.glGetInteger(GL33C.GL_TEXTURE_BINDING_2D);
            samplers[unit] = GL33C.glGetInteger(GL33C.GL_SAMPLER_BINDING);
        }
        GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
    }
    void prepare() {
        for (int capability : CAPABILITIES) GL33C.glDisable(capability);
        for (int output = 0; output < blend.length; output++) {
            GL33C.glDisablei(GL33C.GL_BLEND, output);
            GL33C.glColorMaski(output, true, true, true, true);
        }
        GL33C.glPolygonMode(GL33C.GL_FRONT_AND_BACK, GL33C.GL_FILL);
        GL33C.glBindSampler(0, 0);
        GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER, 0);
        for (int index = 0; index < UNPACK_STATES.length; index++) GL33C.glPixelStorei(UNPACK_STATES[index], index == 3 ? 1 : 0);
        if (clipControl) GL45C.glClipControl(GL45C.GL_LOWER_LEFT, GL45C.GL_NEGATIVE_ONE_TO_ONE);
    }
    @Override public void close() {
        GL33C.glUseProgram(program); GL33C.glBindVertexArray(vao);
        GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, draw);
        GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, read);
        GL33C.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        for (int output = 0; output < blend.length; output++) {
            int index = output * 4;
            GL33C.glColorMaski(output, color[index], color[index + 1], color[index + 2], color[index + 3]);
            if (blend[output]) GL33C.glEnablei(GL33C.GL_BLEND, output); else GL33C.glDisablei(GL33C.GL_BLEND, output);
        }
        GL33C.glPolygonMode(GL33C.GL_FRONT_AND_BACK, polygon[0]);
        for (int i = 0; i < CAPABILITIES.length; i++) {
            if (enabled[i]) GL33C.glEnable(CAPABILITIES[i]); else GL33C.glDisable(CAPABILITIES[i]);
        }
        GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER, unpack);
        for (int index = 0; index < UNPACK_STATES.length; index++) GL33C.glPixelStorei(UNPACK_STATES[index], unpackValues[index]);
        for (int unit = 0; unit < textures.length; unit++) {
            GL33C.glActiveTexture(GL33C.GL_TEXTURE0 + unit);
            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, textures[unit]); GL33C.glBindSampler(unit, samplers[unit]);
        }
        GL33C.glActiveTexture(active);
        if (clipControl) GL45C.glClipControl(clipOrigin, clipDepth);
    }
}
