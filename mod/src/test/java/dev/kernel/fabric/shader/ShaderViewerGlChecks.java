package dev.kernel.fabric.shader;

import dev.kernel.fabric.shader.pack.PreparedShaderPack;
import dev.kernel.fabric.shader.pack.ShaderBufferSettings;
import dev.kernel.fabric.shader.pack.ShaderColorFormat;
import dev.kernel.fabric.shader.pack.ShaderSource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;

/**
 * Native pixel checks that the viewer inputs arrive with the types and values a pack expects.
 *
 * <p>Read back from a floating-point target rather than from bytes, because these carry an integer
 * light scale of up to 240 and an effect blend that a pack compares against small thresholds; rounding
 * either into eight bits would hide a defect in exactly the values that matter.
 */
public final class ShaderViewerGlChecks {
    private ShaderViewerGlChecks() {}

    public static void run() throws Exception {
        int[] packNames = {GL33C.GL_PACK_ALIGNMENT, GL33C.GL_PACK_ROW_LENGTH, GL33C.GL_PACK_SKIP_ROWS, GL33C.GL_PACK_SKIP_PIXELS};
        int[] packValues = new int[packNames.length];
        int packBuffer = GL33C.glGetInteger(GL33C.GL_PIXEL_PACK_BUFFER_BINDING);
        for (int i = 0; i < packNames.length; i++) packValues[i] = GL33C.glGetInteger(packNames[i]);
        int texture = 0;
        try (var state = new ShaderGlState()) {
            state.prepare();
            GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, 0);
            for (int name : packNames) GL33C.glPixelStorei(name, name == GL33C.GL_PACK_ALIGNMENT ? 1 : 0);
            texture = GL33C.glGenTextures();
            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, texture);
            GL33C.glTexImage2D(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA32F, 1, 3, 0, GL33C.GL_RGBA, GL33C.GL_FLOAT, 0L);
            String fragment = """
                #version 330 core
                uniform int isEyeInWater;
                uniform float blindness, darknessFactor, nightVision, screenBrightness;
                uniform ivec2 eyeBrightness;
                uniform vec3 skyColor;
                out vec4 outColor;
                void main() {
                    int row = int(gl_FragCoord.y);
                    if (row == 0) outColor = vec4(float(isEyeInWater), blindness, darknessFactor, nightVision);
                    else if (row == 1) outColor = vec4(screenBrightness, float(eyeBrightness.x), float(eyeBrightness.y), 0.0);
                    else outColor = vec4(skyColor, 1.0);
                }
                """;
            var pass = new PreparedShaderPack.Pass("viewer", ShaderSource.DEFAULT_VERTEX, ShaderSource.translate(fragment, false));
            var buffers = new ArrayList<>(ShaderBufferSettings.defaults().buffers());
            buffers.set(0, new ShaderBufferSettings.Buffer(ShaderColorFormat.RGBA32F, true, true, new ShaderBufferSettings.Color(0, 0, 0, 0)));
            try (var pipeline = new ShaderPipeline(new PreparedShaderPack("viewer-input-test", List.of(pass), new ShaderBufferSettings(buffers)))) {
                if (!pipeline.needsViewerData()) throw new AssertionError("Active viewer inputs were not detected");
                if (pipeline.needsWorldData()) throw new AssertionError("Viewer inputs asked for unrelated world data");
                try { pipeline.render(texture, 1, 3); throw new AssertionError("Missing viewer data was accepted"); }
                catch (IOException expected) { }
                var samples = new ShaderViewerData[]{
                    new ShaderViewerData(0, 0, 0, 0, 0.5f, 0, 15, new float[]{0.4f, 0.6f, 1.0f}),
                    new ShaderViewerData(1, 0.25f, 0.75f, 1.0f, 0.0f, 15, 0, new float[]{0, 0, 0}),
                    new ShaderViewerData(3, 1.0f, 0.125f, 0.5f, 1.0f, 7, 11, new float[]{1, 1, 1})};
                for (var data : samples) {
                    pipeline.render(texture, 1, 3, null, data);
                    GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, texture);
                    try (var stack = MemoryStack.stackPush()) {
                        var pixels = stack.mallocFloat(3 * 4);
                        GL33C.glGetTexImage(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA, GL33C.GL_FLOAT, pixels);
                        int[] brightness = data.eyeBrightness();
                        float[] expected = {data.eyeInWater(), data.blindness(), data.darkness(), data.nightVision(),
                            data.screenBrightness(), brightness[0], brightness[1], 0,
                            data.skyColor()[0], data.skyColor()[1], data.skyColor()[2], 1};
                        for (int index = 0; index < expected.length; index++)
                            if (Math.abs(pixels.get(index) - expected[index]) > 1.0e-4)
                                throw new AssertionError("Viewer uniform " + index + " arrived as " + pixels.get(index)
                                    + " rather than " + expected[index]);
                    }
                }
            }
            // A program declaring one of these with the wrong type is refused rather than fed a value it
            // would read as something else.
            for (String invalid : new String[]{
                "uniform float isEyeInWater; void main(){outColor=vec4(isEyeInWater);}",
                "uniform int blindness; void main(){outColor=vec4(float(blindness));}",
                "uniform ivec3 eyeBrightness; void main(){outColor=vec4(vec3(eyeBrightness),1);}",
                "uniform vec4 skyColor; void main(){outColor=skyColor;}",
                "uniform vec2 nightVision; void main(){outColor=vec4(nightVision,0,1);}"}) {
                String source = "#version 330 core\nout vec4 outColor;\n" + invalid;
                var bad = new PreparedShaderPack.Pass("bad-viewer-type", ShaderSource.DEFAULT_VERTEX, ShaderSource.translate(source, false));
                try (var ignored = new ShaderPipeline(new PreparedShaderPack("bad-viewer-type", List.of(bad)))) {
                    throw new AssertionError("Invalid viewer uniform type accepted: " + invalid);
                } catch (IOException expected) { }
            }
        } finally {
            if (texture != 0) GL33C.glDeleteTextures(texture);
            for (int i = 0; i < packNames.length; i++) GL33C.glPixelStorei(packNames[i], packValues[i]);
            GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, packBuffer);
        }
        System.out.println("Kernel shader probe verified the viewer inputs on the graphics device.");
    }
}
