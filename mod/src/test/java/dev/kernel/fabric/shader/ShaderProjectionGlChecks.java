package dev.kernel.fabric.shader;

import dev.kernel.fabric.shader.pack.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;

/** Native float framebuffer output validates mat4 column order, distinct bindings and frame history. */
public final class ShaderProjectionGlChecks {
    private ShaderProjectionGlChecks() {}
    public static void run() throws Exception {
        int[] names = {GL33C.GL_PACK_ALIGNMENT, GL33C.GL_PACK_ROW_LENGTH, GL33C.GL_PACK_SKIP_ROWS, GL33C.GL_PACK_SKIP_PIXELS};
        int[] previous = new int[names.length];
        for (int i = 0; i < names.length; i++) previous[i] = GL33C.glGetInteger(names[i]);
        int pbo = GL33C.glGetInteger(GL33C.GL_PIXEL_PACK_BUFFER_BINDING);
        int target = 0;
        try (var state = new ShaderGlState()) {
            state.prepare(); GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, 0);
            for (int name : names) GL33C.glPixelStorei(name, name == GL33C.GL_PACK_ALIGNMENT ? 1 : 0);
            target = GL33C.glGenTextures(); int texture = target;
            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, texture);
            GL33C.glTexImage2D(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA32F, 4, 3, 0, GL33C.GL_RGBA, GL33C.GL_FLOAT, 0L);
            var pass = new PreparedShaderPack.Pass("projection", ShaderSource.DEFAULT_VERTEX, ShaderSource.translate("""
                #version 330 core
                uniform mat4 gbufferProjection, gbufferProjectionInverse, gbufferPreviousProjection;
                out vec4 outColor;
                void main() {
                    int column=int(gl_FragCoord.x);
                    int row=int(gl_FragCoord.y);
                    if(row==0) outColor=gbufferProjection[column];
                    else if(row==1) outColor=gbufferProjectionInverse[column];
                    else outColor=gbufferPreviousProjection[column];
                }
                """, false));
            var buffers = new ArrayList<>(ShaderBufferSettings.defaults().buffers());
            buffers.set(0, new ShaderBufferSettings.Buffer(ShaderColorFormat.RGBA32F, true, true, new ShaderBufferSettings.Color(0,0,0,0)));
            try (var pipeline = new ShaderPipeline(new PreparedShaderPack("projection-test", List.of(pass), new ShaderBufferSettings(buffers)))) {
                if (!pipeline.needsProjection() || pipeline.needsDepth() || pipeline.needsWorldData()) throw new AssertionError("Projection requirements differ");
                missing(() -> pipeline.render(texture, 4, 3));
                Matrix4f last = null;
                for (int frame = 0; frame < 8; frame++) {
                    boolean reverse = frame >= 4, zero = (frame & 1) != 0;
                    Matrix4f nativeProjection = new Matrix4f().setPerspective(.7f + frame * .08f, 1.5f,
                        reverse ? 512 : .05f, reverse ? .05f : 512, zero).translate(.03f, -.07f, .01f).rotateX(.04f);
                    Matrix4f expected = new Matrix4f();
                    ShaderProjectionState.normalize(nativeProjection, reverse, zero, expected);
                    if (!pipeline.captureProjection(nativeProjection, reverse, zero)) throw new AssertionError("Valid projection rejected");
                    nativeProjection.zero();
                    pipeline.render(texture, 4, 3);
                    Matrix4f expectedLast = last == null ? expected : last;
                    var expectedRows = new Matrix4f[]{expected, expected.invert(new Matrix4f()), expectedLast};
                    GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, texture);
                    try (var stack = MemoryStack.stackPush()) {
                        var pixels = stack.mallocFloat(48);
                        GL33C.glGetTexImage(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA, GL33C.GL_FLOAT, pixels);
                        for (int row = 0; row < 3; row++) {
                            float[] values = expectedRows[row].get(new float[16]);
                            for (int index = 0; index < 16; index++) if (pixels.get(row * 16 + index) != values[index])
                                throw new AssertionError("Projection uniform differs at frame " + frame + ", row " + row + ", component " + index);
                        }
                    }
                    missing(() -> pipeline.render(texture, 4, 3));
                    if (pipeline.captureProjection(new Matrix4f().m00(Float.NaN), false, false)) throw new AssertionError("Nonfinite projection accepted");
                    missing(() -> pipeline.render(texture, 4, 3));
                    last = expected;
                    if (frame == 3) { pipeline.resetHistory(); last = null; }
                }
                pipeline.beginWorld(); missing(() -> pipeline.render(texture, 4, 3));
                if (pipeline.captureProjection(new Matrix4f().zero(), false, false)) throw new AssertionError("Singular projection accepted");
                missing(() -> pipeline.render(texture, 4, 3));
            }
            for (String invalid : new String[]{"uniform float gbufferProjection; void main(){outColor=vec4(gbufferProjection);}",
                "uniform mat3 gbufferProjectionInverse; void main(){outColor=vec4(gbufferProjectionInverse[0],1.0);}",
                "uniform mat4 gbufferPreviousProjection[2]; void main(){outColor=gbufferPreviousProjection[0][0]+gbufferPreviousProjection[1][0];}"}) {
                var bad = new PreparedShaderPack.Pass("invalid-projection", ShaderSource.DEFAULT_VERTEX,
                    ShaderSource.translate("#version 330 core\nout vec4 outColor;\n" + invalid, false));
                missing(() -> { try (var ignored = new ShaderPipeline(new PreparedShaderPack("invalid-projection", List.of(bad)))) { } });
            }
            if (GL33C.glGetError() != GL33C.GL_NO_ERROR) throw new AssertionError("Projection checks generated an OpenGL error");
        } finally {
            if (target != 0) GL33C.glDeleteTextures(target);
            GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, pbo);
            for (int i = 0; i < names.length; i++) GL33C.glPixelStorei(names[i], previous[i]);
        }
        System.out.println("Kernel projection shader checks passed: native matrix pixels, column order, current/inverse/previous bindings, conventions and history recovery");
    }
    @FunctionalInterface private interface Io { void run() throws IOException; }
    private static void missing(Io action) throws IOException {
        try { action.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Invalid projection inputs were accepted");
    }
}
