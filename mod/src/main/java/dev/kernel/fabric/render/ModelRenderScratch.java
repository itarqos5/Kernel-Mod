package dev.kernel.fabric.render;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Per-thread mutable values reused by Kernel's model rendering paths.
 */
public final class ModelRenderScratch {
    private static final ThreadLocal<ModelRenderScratch> LOCAL = ThreadLocal.withInitial(ModelRenderScratch::new);

    private final Quaternionf rotation = new Quaternionf();
    private final Vector3f normal = new Vector3f();

    private ModelRenderScratch() {
    }

    public static ModelRenderScratch get() {
        return LOCAL.get();
    }

    public Quaternionf rotationZYX(float z, float y, float x) {
        return this.rotation.rotationZYX(z, y, x);
    }

    public Vector3f normal() {
        return this.normal;
    }
}
