package dev.kernel.fabric.shader;

import dev.kernel.fabric.shader.pack.PreparedShaderPack;
import dev.kernel.fabric.shader.pack.ShaderSource;
import java.io.IOException;
import java.util.List;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;

/** Native pixel checks for frame-coherent world inputs and their render-thread integration. */
public final class ShaderWorldGlChecks {
    private static boolean weatherObserved;
    private ShaderWorldGlChecks() {}

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
            GL33C.glTexImage2D(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA8, 8, 2, 0, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, 0L);
            String fragment = """
                #version 330 core
                uniform int worldTime, worldDay, moonPhase, frameCounter;
                uniform float rainStrength, thunderStrength;
                out vec4 outColor;
                void main() {
                    if (int(gl_FragCoord.x) % 2 == 0)
                        outColor = vec4(float(worldTime) / 24000.0, float(worldDay % 256) / 255.0, float(moonPhase) / 7.0, 1.0);
                    else outColor = vec4(rainStrength, thunderStrength, float(frameCounter % 256) / 255.0, 1.0);
                }
                """;
            var pass = new PreparedShaderPack.Pass("world", ShaderSource.DEFAULT_VERTEX, ShaderSource.translate(fragment, false));
            try (var pipeline = new ShaderPipeline(new PreparedShaderPack("world-input-test", List.of(pass)))) {
                if (!pipeline.needsWorldData()) throw new AssertionError("Active world inputs were not detected");
                try { pipeline.render(texture, 8, 2); throw new AssertionError("Missing world data was accepted"); }
                catch (IOException expected) { }
                var samples = new ShaderWorldData[]{ShaderWorldData.from(23999L, 7, .25f, .125f),
                    ShaderWorldData.from(24000L * 513 + 12000, 2, .8f, .6f), ShaderWorldData.from(0, 0, 0, 0)};
                for (int frame = 0; frame < samples.length; frame++) {
                    var data = samples[frame];
                    pipeline.render(texture, 8, 2, data);
                    GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, texture);
                    try (var stack = MemoryStack.stackPush()) {
                        var pixels = stack.malloc(8 * 2 * 4);
                        GL33C.glGetTexImage(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, pixels);
                        for (int pixel = 0; pixel < 16; pixel++) {
                            float[] expected = (pixel & 1) == 0
                                ? new float[]{data.worldTime() / 24000f, (data.worldDay() % 256) / 255f, data.moonPhase() / 7f, 1}
                                : new float[]{data.rainStrength(), data.thunderStrength(), frame / 255f, 1};
                            for (int channel = 0; channel < 4; channel++)
                                if (Math.abs((pixels.get(pixel * 4 + channel) & 255) - expected[channel] * 255) > 1.1)
                                    throw new AssertionError("World uniform pixel differs at frame " + frame + ", pixel " + pixel + ", channel " + channel);
                        }
                    }
                }
            }
            for (String invalid : new String[]{
                "uniform float worldTime; void main(){outColor=vec4(worldTime);}",
                "uniform int rainStrength; void main(){outColor=vec4(float(rainStrength));}",
                "uniform int worldDay[2]; void main(){outColor=vec4(float(worldDay[0]+worldDay[1]));}"}) {
                String source = "#version 330 core\nout vec4 outColor;\n" + invalid;
                var bad = new PreparedShaderPack.Pass("bad-world-type", ShaderSource.DEFAULT_VERTEX, ShaderSource.translate(source, false));
                try (var ignored = new ShaderPipeline(new PreparedShaderPack("bad-world-type", List.of(bad)))) {
                    throw new AssertionError("Invalid world uniform type or array accepted");
                } catch (IOException expected) { }
            }
        } finally {
            if (texture != 0) GL33C.glDeleteTextures(texture);
            GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, packBuffer);
            for (int i = 0; i < packNames.length; i++) GL33C.glPixelStorei(packNames[i], packValues[i]);
        }
        System.out.println("Kernel world shader inputs passed: native pixels, distinct integer bindings, weather, frame updates and missing-input recovery");
    }

    /** Called with the world's color image bound for reading, before the native HUD changes it. */
    public static void verifyWorldPixels(net.minecraft.client.Minecraft minecraft, net.minecraft.client.DeltaTracker tracker, int height) {
        var data = ShaderWorldCapture.capture(minecraft, tracker);
        try (var stack = MemoryStack.stackPush()) {
            var pixel = stack.malloc(4);
            for (int sample = 0; sample < 2; sample++) {
                GL33C.glReadPixels(sample * 2, height / 2, 1, 1, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, pixel);
                float[] expected = sample == 0 ? new float[]{data.rainStrength(), data.thunderStrength(), data.moonPhase() / 7f, 1}
                    : new float[]{data.worldTime() / 24000f, (data.worldDay() % 256) / 255f, 0, 1};
                for (int channel = 0; channel < 4; channel++)
                    if (Math.abs((pixel.get(channel) & 255) - expected[channel] * 255) > 1.1)
                        throw new AssertionError("Live world shader input differs: sample " + sample + ", channel " + channel);
            }
        }
        if (data.rainStrength() > .1f && data.thunderStrength() > .05f) weatherObserved = true;
    }

    public static void beginLiveWeatherSample(net.minecraft.client.Minecraft minecraft) {
        minecraft.level.setRainLevel(.375f);
        minecraft.level.setThunderLevel(.625f);
    }

    public static boolean weatherObserved() { return weatherObserved; }
}
