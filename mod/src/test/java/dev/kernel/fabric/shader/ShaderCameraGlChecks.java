package dev.kernel.fabric.shader;

import dev.kernel.fabric.shader.pack.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;

/** Actual GPU output checks float/integer bindings, view matrices and shared teleport history reset. */
public final class ShaderCameraGlChecks {
    private ShaderCameraGlChecks() {}
    public static void run() throws Exception {
        int[] names = {GL33C.GL_PACK_ALIGNMENT, GL33C.GL_PACK_ROW_LENGTH, GL33C.GL_PACK_SKIP_ROWS, GL33C.GL_PACK_SKIP_PIXELS};
        int[] saved = new int[names.length];
        for (int i = 0; i < names.length; i++) saved[i] = GL33C.glGetInteger(names[i]);
        int pbo = GL33C.glGetInteger(GL33C.GL_PIXEL_PACK_BUFFER_BINDING), target = 0;
        try (var state = new ShaderGlState()) {
            state.prepare(); GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, 0);
            for (int name : names) GL33C.glPixelStorei(name, name == GL33C.GL_PACK_ALIGNMENT ? 1 : 0);
            target = GL33C.glGenTextures(); int texture = target;
            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, texture);
            GL33C.glTexImage2D(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA32F, 4, 13, 0, GL33C.GL_RGBA, GL33C.GL_FLOAT, 0L);
            var pass = new PreparedShaderPack.Pass("camera", ShaderSource.DEFAULT_VERTEX, ShaderSource.translate("""
                #version 330 core
                uniform mat4 gbufferModelView, gbufferModelViewInverse, gbufferPreviousModelView;
                uniform mat4 gbufferProjection, gbufferProjectionInverse, gbufferPreviousProjection;
                uniform vec3 cameraPosition, previousCameraPosition, cameraPositionFract, previousCameraPositionFract;
                uniform ivec3 cameraPositionInt, previousCameraPositionInt;
                uniform float eyeAltitude;
                out vec4 outColor;
                void main() {
                    int column=int(gl_FragCoord.x), row=int(gl_FragCoord.y);
                    if(row==0) outColor=gbufferModelView[column];
                    else if(row==1) outColor=gbufferModelViewInverse[column];
                    else if(row==2) outColor=gbufferPreviousModelView[column];
                    else if(row==3) outColor=gbufferProjection[column];
                    else if(row==4) outColor=gbufferProjectionInverse[column];
                    else if(row==5) outColor=gbufferPreviousProjection[column];
                    else if(row==6) outColor=vec4(cameraPosition,1);
                    else if(row==7) outColor=vec4(previousCameraPosition,1);
                    else if(row==8) outColor=vec4(cameraPositionFract,1);
                    else if(row==9) outColor=vec4(previousCameraPositionFract,1);
                    else if(row==10 || row==11) {
                        int value=column==3 ? 0 : row==10 ? cameraPositionInt[column] : previousCameraPositionInt[column];
                        outColor=vec4(float(value & 65535),float(value >> 16),0,1);
                    } else outColor=vec4(eyeAltitude,0,0,1);
                }
                """, false));
            var buffers = new ArrayList<>(ShaderBufferSettings.defaults().buffers());
            buffers.set(0, new ShaderBufferSettings.Buffer(ShaderColorFormat.RGBA32F, true, true, new ShaderBufferSettings.Color(0,0,0,0)));
            try (var pipeline = new ShaderPipeline(new PreparedShaderPack("camera-test", List.of(pass), new ShaderBufferSettings(buffers)))) {
                if (!pipeline.needsView() || !pipeline.needsProjection() || pipeline.needsDepth() || pipeline.needsWorldData())
                    throw new AssertionError("Camera shader requirements differ");
                var camera = new ShaderCameraState();
                Matrix4f lastView = null, lastProjection = null;
                double[] positions = {29999.5, 30000.5, 30001.5, 150000.25, 150000.75, -29999000.5, -29999000.25, -29998999.75};
                for (int frame = 0; frame < positions.length; frame++) {
                    double x = positions[frame], y = -13.75 + frame * .125, z = -x + .25;
                    Matrix4f view = new Matrix4f().rotateXYZ(.11f * frame, -.2f * frame, .03f);
                    Matrix4f projection = new Matrix4f().perspective(.7f + .1f * frame, 1.3f, .05f, 512);
                    if (!pipeline.captureProjection(projection, false, false) || !pipeline.captureView(view, x, y, z)) throw new AssertionError("Camera capture rejected");
                    camera.capture(x, y, z);
                    if (camera.prepare(4, 13)) { lastView = null; lastProjection = null; }
                    Matrix4f[] expectedMatrices = {new Matrix4f(view), view.invert(new Matrix4f()), lastView == null ? new Matrix4f(view) : lastView,
                        new Matrix4f(projection), projection.invert(new Matrix4f()), lastProjection == null ? new Matrix4f(projection) : lastProjection};
                    lastView = new Matrix4f(view); lastProjection = new Matrix4f(projection);
                    view.zero(); projection.zero(); // The pipeline must own its samples.
                    pipeline.render(texture, 4, 13);
                    GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, texture);
                    try (var stack = MemoryStack.stackPush()) {
                        var pixels = stack.mallocFloat(4 * 13 * 4);
                        GL33C.glGetTexImage(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA, GL33C.GL_FLOAT, pixels);
                        for (int row = 0; row < 6; row++) {
                            float[] expected = expectedMatrices[row].get(new float[16]);
                            for (int index = 0; index < 16; index++) equal(expected[index], pixels.get(row * 16 + index), "matrix " + row);
                        }
                        String[] vectors = {"cameraPosition", "previousCameraPosition", "cameraPositionFract", "previousCameraPositionFract"};
                        for (int row = 0; row < 4; row++) for (int column = 0; column < 4; column++) for (int axis = 0; axis < 4; axis++)
                            equal(axis == 3 ? 1 : camera.vector(vectors[row])[axis], pixels.get((row + 6) * 16 + column * 4 + axis), vectors[row]);
                        for (int row = 0; row < 2; row++) for (int axis = 0; axis < 3; axis++) {
                            int offset = (row + 10) * 16 + axis * 4;
                            int actual = (int) pixels.get(offset) | ((int) pixels.get(offset + 1) << 16);
                            int expected = camera.integer(row == 0 ? "cameraPositionInt" : "previousCameraPositionInt")[axis];
                            if (actual != expected) throw new AssertionError("GPU integer camera bits differ: " + actual + " != " + expected);
                        }
                        equal(camera.altitude(), pixels.get(12 * 16), "eyeAltitude");
                    }
                    camera.complete();
                    missing(() -> pipeline.render(texture, 4, 13));
                    if (pipeline.captureView(new Matrix4f().m00(Float.NaN), x, y, z)) throw new AssertionError("Invalid view accepted");
                    missing(() -> pipeline.render(texture, 4, 13));
                    if (frame == 4) { pipeline.resetHistory(); camera.resetHistory(); lastView = lastProjection = null; }
                }
            }
            history(texture);
            for (String invalid : new String[]{"uniform float gbufferModelView; void main(){outColor=vec4(gbufferModelView);}",
                "uniform mat3 gbufferModelViewInverse; void main(){outColor=vec4(gbufferModelViewInverse[0],1);}",
                "uniform mat4 gbufferPreviousModelView[2]; void main(){outColor=gbufferPreviousModelView[0][0]+gbufferPreviousModelView[1][0];}",
                "uniform vec4 cameraPosition; void main(){outColor=cameraPosition;}",
                "uniform vec3 cameraPositionInt; void main(){outColor=vec4(cameraPositionInt,1);}",
                "uniform vec3 previousCameraPosition[2]; void main(){outColor=vec4(previousCameraPosition[0]+previousCameraPosition[1],1);}"}) {
                var bad = new PreparedShaderPack.Pass("invalid-camera", ShaderSource.DEFAULT_VERTEX,
                    ShaderSource.translate("#version 330 core\nout vec4 outColor;\n" + invalid, false));
                missing(() -> { try (var ignored = new ShaderPipeline(new PreparedShaderPack("invalid-camera", List.of(bad)))) {} });
            }
            if (GL33C.glGetError() != GL33C.GL_NO_ERROR) throw new AssertionError("Camera checks generated an OpenGL error");
        } finally {
            if (target != 0) GL33C.glDeleteTextures(target);
            GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, pbo);
            for (int i = 0; i < names.length; i++) GL33C.glPixelStorei(names[i], saved[i]);
        }
        System.out.println("Kernel camera GPU inputs passed: view/inverse/history, rebased positions, exact integer bits, teleport reset and failure recovery");
    }
    private static void equal(float expected, float actual, String label) {
        if (Float.floatToIntBits(expected) != Float.floatToIntBits(actual)) throw new AssertionError(label + ": " + actual + " != " + expected);
    }
    private static void history(int texture) throws Exception {
        var composite = new PreparedShaderPack.Pass("camera-history", ShaderSource.DEFAULT_VERTEX, ShaderSource.translate("""
            #version 330 core
            uniform sampler2D colortex7; uniform vec3 cameraPosition; out vec4 outColor;
            void main(){outColor=texture(colortex7,vec2(.5))+vec4(cameraPosition.y);}
            """, false), List.of(7), 0);
        var finish = new PreparedShaderPack.Pass("final", ShaderSource.DEFAULT_VERTEX, ShaderSource.translate("""
            #version 330 core
            uniform sampler2D colortex7; out vec4 outColor;
            void main(){outColor=texture(colortex7,vec2(.5));}
            """, false));
        var settings = new ArrayList<>(ShaderBufferSettings.defaults().buffers());
        var zero = new ShaderBufferSettings.Color(0, 0, 0, 0);
        settings.set(0, new ShaderBufferSettings.Buffer(ShaderColorFormat.RGBA32F, true, true, zero));
        settings.set(7, new ShaderBufferSettings.Buffer(ShaderColorFormat.RGBA32F, true, false, zero));
        try (var pipeline = new ShaderPipeline(new PreparedShaderPack("camera-history", List.of(composite, finish), new ShaderBufferSettings(settings)))) {
            if (!pipeline.needsView() || pipeline.needsProjection()) throw new AssertionError("Position-only pack requirements differ");
            double[] x = {29999.5, 30000.5, 30001.5, 40002, 40003};
            float[] expected = {.125f, .25f, .375f, .125f, .25f};
            for (int frame = 0; frame < x.length; frame++) {
                if (pipeline.captureView(null, Double.NaN, 0, 0)) throw new AssertionError("Nonfinite camera accepted");
                missing(() -> pipeline.render(texture, 4, 13));
                pipeline.captureView(null, x[frame], .125, 0); pipeline.render(texture, 4, 13);
                GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, texture);
                try (var stack = MemoryStack.stackPush()) {
                    var pixels = stack.mallocFloat(4 * 13 * 4);
                    GL33C.glGetTexImage(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA, GL33C.GL_FLOAT, pixels);
                    for (int i = 0; i < pixels.capacity(); i++) equal(expected[frame], pixels.get(i), "camera color history");
                }
            }
        }
    }
    @FunctionalInterface private interface Io { void run() throws IOException; }
    private static void missing(Io action) throws IOException {
        try { action.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Invalid camera input was accepted");
    }
}
