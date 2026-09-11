package dev.kernel.fabric.shader;

import java.io.IOException;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Render-thread-owned world projection, inverse and previous completed world frame. */
final class ShaderProjectionState {
    static final int CURRENT = 1, INVERSE = 2, PREVIOUS = 4;
    private final Matrix4f current = new Matrix4f();
    private final Matrix4f inverse, last;
    private final float[] currentValues, inverseValues, previousValues;
    private boolean captured, prepared, history;
    private int width, height, lastWidth, lastHeight;

    ShaderProjectionState(int required) {
        if (required == 0 || (required & ~7) != 0) throw new IllegalArgumentException("Invalid projection inputs");
        currentValues = (required & CURRENT) != 0 ? new float[16] : null;
        inverse = (required & INVERSE) != 0 ? new Matrix4f() : null;
        inverseValues = inverse == null ? null : new float[16];
        last = (required & PREVIOUS) != 0 ? new Matrix4f() : null;
        previousValues = last == null ? null : new float[16];
    }
    void beginWorld() { captured = prepared = false; }
    void resetHistory() { history = false; beginWorld(); }
    boolean capture(Matrix4fc source, boolean reverse, boolean zeroToOne) {
        beginWorld();
        normalize(source, reverse, zeroToOne, current);
        if (!current.isFinite()) return false;
        if (inverse != null) {
            current.invert(inverse);
            if (!inverse.isFinite()) return false;
            inverse.get(inverseValues);
        }
        if (currentValues != null) current.get(currentValues);
        captured = true;
        return true;
    }
    void prepare(int width, int height) throws IOException {
        if (!captured || width <= 0 || height <= 0) throw new IOException("This shader requires the current world projection");
        if (previousValues != null) (history && lastWidth == width && lastHeight == height ? last : current).get(previousValues);
        this.width = width; this.height = height; prepared = true;
    }
    float[] values(String name) {
        if (!prepared) throw new IllegalStateException("Projection uniforms are not prepared");
        float[] values = switch (name) {
            case "gbufferProjection" -> currentValues;
            case "gbufferProjectionInverse" -> inverseValues;
            case "gbufferPreviousProjection" -> previousValues;
            default -> throw new IllegalArgumentException("Unknown projection input: " + name);
        };
        if (values == null) throw new IllegalStateException("Projection input was not requested");
        return values; // Only the render-thread uniform uploader reads these owned arrays.
    }
    void complete() {
        if (!prepared) throw new IllegalStateException("No completed world projection");
        if (last != null) last.set(current);
        lastWidth = width; lastHeight = height; history = true;
        beginWorld();
    }
    static void normalize(Matrix4fc source, boolean reverse, boolean zeroToOne, Matrix4f destination) {
        destination.set(source);
        if (!reverse && !zeroToOne) return;
        float scale = (zeroToOne ? 2 : 1) * (reverse ? -1 : 1);
        float offset = zeroToOne ? (reverse ? 1 : -1) : 0;
        destination.m02(scale * source.m02() + offset * source.m03())
            .m12(scale * source.m12() + offset * source.m13())
            .m22(scale * source.m22() + offset * source.m23())
            .m32(scale * source.m32() + offset * source.m33());
    }
}

