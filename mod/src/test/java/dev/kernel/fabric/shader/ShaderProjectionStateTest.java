package dev.kernel.fabric.shader;

import java.io.IOException;
import java.util.Random;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShaderProjectionStateTest {
    @Test void nativeDepthConventionsPreserveScreenCoordinatesAndInverseTransforms() {
        Random random = new Random(20260911);
        for (boolean reverse : new boolean[]{false, true}) for (boolean zero : new boolean[]{false, true}) {
            for (int iteration = 0; iteration < 1024; iteration++) {
                Matrix4f nativeProjection = new Matrix4f().setPerspective((float) Math.toRadians(35 + random.nextFloat() * 70),
                    .5f + random.nextFloat() * 3, reverse ? 512 : .05f, reverse ? .05f : 512, zero);
                if ((iteration & 1) != 0) nativeProjection.translate(.03f, -.08f, .01f)
                    .rotateX(.05f).rotateZ(-.02f).scale(.91f, 1.07f, 1).rotate(.04f, 0, .70710677f, .70710677f);
                Matrix4f saved = new Matrix4f(nativeProjection), shaderProjection = new Matrix4f();
                ShaderProjectionState.normalize(nativeProjection, reverse, zero, shaderProjection);
                assertEquals(saved, nativeProjection);
                Matrix4f inverse = shaderProjection.invert(new Matrix4f());
                for (int sample = 0; sample < 16; sample++) {
                    Vector4f point = new Vector4f((random.nextFloat() - .5f) * .5f, (random.nextFloat() - .5f) * .5f,
                        -.15f - random.nextFloat() * 20, 1);
                    Vector4f nativeClip = nativeProjection.transform(point, new Vector4f());
                    Vector4f shaderClip = shaderProjection.transform(point, new Vector4f());
                    double nativeDepth = nativeClip.z / nativeClip.w;
                    if (!zero) nativeDepth = nativeDepth * .5 + .5;
                    if (reverse) nativeDepth = 1 - nativeDepth;
                    assertEquals(nativeDepth, shaderClip.z / shaderClip.w * .5 + .5, 3e-7);
                    assertEquals(nativeClip.x, shaderClip.x); assertEquals(nativeClip.y, shaderClip.y); assertEquals(nativeClip.w, shaderClip.w);
                    Vector4f restored = inverse.transform(shaderClip, new Vector4f()); restored.div(restored.w);
                    assertEquals(point.x, restored.x, .003f); assertEquals(point.y, restored.y, .003f); assertEquals(point.z, restored.z, .003f);
                }
            }
        }
    }
    @Test void capturesOwnedValuesAndKeepsOnlyCompletedHistoryAcrossResizeAndWorldChanges() throws Exception {
        for (int mask = 1; mask < 8; mask++) {
            var state = new ShaderProjectionState(mask);
            assertThrows(IOException.class, () -> state.prepare(100, 50));
            var first = new Matrix4f().perspective(1.0f, 2, .05f, 512);
            var copy = new Matrix4f(first);
            state.capture(first, false, false); first.zero(); state.prepare(100, 50);
            check(state, mask, copy, copy);
            state.complete(); assertThrows(IOException.class, () -> state.prepare(100, 50));
            var second = new Matrix4f().perspective(1.4f, 1, .05f, 512);
            state.capture(second, false, false); state.prepare(100, 50);
            check(state, mask, second, copy);
            state.beginWorld(); // An unfinished render must not advance the history.
            assertFalse(state.capture(new Matrix4f().m00(Float.NaN), false, false));
            assertThrows(IOException.class, () -> state.prepare(100, 50));
            if ((mask & ShaderProjectionState.INVERSE) != 0)
                assertFalse(state.capture(new Matrix4f().zero(), false, false));
            state.capture(second, false, false); state.prepare(100, 50); check(state, mask, second, copy);
            state.complete();
            state.capture(copy, false, false); state.prepare(200, 50); check(state, mask, copy, copy);
            state.complete(); state.resetHistory();
            state.capture(second, false, false); state.prepare(200, 50); check(state, mask, second, second);
            state.beginWorld(); assertThrows(IOException.class, () -> state.prepare(200, 50));
        }
    }
    private static void check(ShaderProjectionState state, int mask, Matrix4f current, Matrix4f previous) {
        if ((mask & 1) != 0) assertArrayEquals(current.get(new float[16]), state.values("gbufferProjection"));
        if ((mask & 2) != 0) assertArrayEquals(current.invert(new Matrix4f()).get(new float[16]), state.values("gbufferProjectionInverse"));
        if ((mask & 4) != 0) assertArrayEquals(previous.get(new float[16]), state.values("gbufferPreviousProjection"));
    }
}
