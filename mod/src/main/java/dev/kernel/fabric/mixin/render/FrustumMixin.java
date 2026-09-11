package dev.kernel.fabric.mixin.render;

import net.minecraft.client.renderer.culling.Frustum;
import org.joml.FrustumIntersection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The native visibility caller needs a boolean, not a full containment/plane classification. */
@Mixin(Frustum.class)
public abstract class FrustumMixin {
    @Shadow @Final private FrustumIntersection intersection;
    @Shadow private double camX;
    @Shadow private double camY;
    @Shadow private double camZ;
    @Shadow private int cubeInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        throw new AssertionError("Mixin shadow was not linked");
    }

    @Redirect(method = "isVisible", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/culling/Frustum;cubeInFrustum(DDDDDD)I"))
    private int kernel$visibleBounds(Frustum self, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        // This private native call is on the current instance. Subclass/intersector behavior stays native.
        if (self.getClass() != Frustum.class || intersection.getClass() != FrustumIntersection.class)
            return cubeInFrustum(minX, minY, minZ, maxX, maxY, maxZ);
        boolean visible = intersection.testAab((float) (minX - camX), (float) (minY - camY), (float) (minZ - camZ),
            (float) (maxX - camX), (float) (maxY - camY), (float) (maxZ - camZ));
        return visible ? FrustumIntersection.INTERSECT : FrustumIntersection.OUTSIDE;
    }
}
