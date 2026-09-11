package dev.kernel.fabric.shader;

import java.io.IOException;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Render-thread-owned matrix, optional inverse and previous completed world frame. */
final class ShaderMatrixState {
    static final int CURRENT = 1, INVERSE = 2, PREVIOUS = 4;
    enum Kind { PROJECTION, MODEL_VIEW }
    private final Kind kind;
    private final Matrix4f current = new Matrix4f();
    private final Matrix4f inverse, last;
    private final float[] currentValues, inverseValues, previousValues;
    private boolean captured, prepared, history;
    private int width, height, lastWidth, lastHeight;

    ShaderMatrixState(Kind kind, int required) {
        if (required == 0 || (required & ~7) != 0) throw new IllegalArgumentException("Invalid matrix inputs");
        this.kind = java.util.Objects.requireNonNull(kind);
        currentValues = (required & CURRENT) != 0 ? new float[16] : null;
        inverse = (required & INVERSE) != 0 ? new Matrix4f() : null;
        inverseValues = inverse == null ? null : new float[16];
        last = (required & PREVIOUS) != 0 ? new Matrix4f() : null;
        previousValues = last == null ? null : new float[16];
    }
    void beginWorld() { captured = prepared = false; }
    void resetHistory() { history = false; beginWorld(); }
    void discardHistory() { history = false; }
    boolean capture(Matrix4fc source, boolean reverse, boolean zeroToOne) {
        beginWorld();
        if (kind == Kind.PROJECTION) normalize(source, reverse, zeroToOne, current);
        else current.set(source);
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
        if (!captured || width <= 0 || height <= 0) throw new IOException("This shader requires the current world " + kind);
        if (previousValues != null) (history && lastWidth == width && lastHeight == height ? last : current).get(previousValues);
        this.width = width; this.height = height; prepared = true;
    }
    float[] values(String name) {
        if (!prepared) throw new IllegalStateException("Matrix uniforms are not prepared");
        int input = kind == Kind.PROJECTION ? dev.kernel.fabric.shader.pack.ShaderUniforms.projectionInput(name)
            : dev.kernel.fabric.shader.pack.ShaderUniforms.modelViewInput(name);
        float[] values = switch (input) {
            case CURRENT -> currentValues;
            case INVERSE -> inverseValues;
            case PREVIOUS -> previousValues;
            default -> throw new IllegalArgumentException("Unknown matrix input: " + name);
        };
        if (values == null) throw new IllegalStateException("Matrix input was not requested");
        return values; // Only the render-thread uniform uploader reads these owned arrays.
    }
    void complete() {
        if (!prepared) throw new IllegalStateException("No completed world matrix");
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

