package dev.kernel.fabric.render;

import org.joml.Vector3f;

/** Interface Mixins cannot introduce fields; shared upload scratch belongs in an ordinary helper class. */
public final class VertexUploadScratch {
    private static final ThreadLocal<Vector3f> NORMAL = ThreadLocal.withInitial(Vector3f::new);
    private static final ReentrantThreadLocalPool<Quad> QUADS = new ReentrantThreadLocalPool<>(() -> new Quad(true), ignored -> {});

    private VertexUploadScratch() {}
    public static Vector3f normal() { return NORMAL.get(); }
    public static Quad acquireQuad(boolean reusable, int light) {
        Quad quad = reusable ? QUADS.acquire() : new Quad(false);
        for (int index = 0; index < 4; index++) { quad.brightness[index] = 1; quad.lights[index] = light; }
        return quad;
    }

    public static final class Quad implements AutoCloseable {
        private final boolean pooled;
        private final float[] brightness = new float[4];
        private final int[] lights = new int[4];
        private Quad(boolean pooled) { this.pooled = pooled; }
        public float[] brightness() { return brightness; }
        public int[] lights() { return lights; }
        @Override public void close() { if (pooled) QUADS.release(); }
    }
}
