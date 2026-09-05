package dev.kernel.fabric.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kernel.fabric.render.FastVertexMath;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

//? if >=26 {
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
//?} else if >=1.21.11 {
/*import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.util.ARGB;
*///?} else {
/*import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Vec3i;
*///?}

/**
 * Removes temporary buffers and per-vertex position objects from Minecraft's baked-quad upload path.
 */
@Mixin(VertexConsumer.class)
public interface VertexConsumerMixin {
    @Unique
    ThreadLocal<Vector3f> KERNEL_NORMAL_SCRATCH = ThreadLocal.withInitial(Vector3f::new);

    //? if >=26 {
    // Author: literal.uu
    // Reason: Decode and transform baked quads without allocating temporary position vectors.
    @Overwrite
    default void putBakedQuad(PoseStack.Pose pose, BakedQuad quad, QuadInstance instance) {
        VertexConsumer consumer = (VertexConsumer)(Object)this;
        Matrix4fc matrix = pose.pose();
        Vector3fc faceNormal = quad.direction().getUnitVec3f();
        Vector3f normal = pose.transformNormal(faceNormal, KERNEL_NORMAL_SCRATCH.get());
        int lightEmission = quad.materialInfo().lightEmission();
        int overlay = instance.overlayCoords();

        for (int vertexIndex = 0; vertexIndex < BakedQuad.VERTEX_COUNT; vertexIndex++) {
            Vector3fc position = quad.position(vertexIndex);
            float x = position.x();
            float y = position.y();
            float z = position.z();
            long packedUv = quad.packedUV(vertexIndex);

            consumer.addVertex(
                FastVertexMath.transformX(matrix, x, y, z),
                FastVertexMath.transformY(matrix, x, y, z),
                FastVertexMath.transformZ(matrix, x, y, z),
                instance.getColor(vertexIndex),
                UVPair.unpackU(packedUv),
                UVPair.unpackV(packedUv),
                overlay,
                instance.getLightCoordsWithEmission(vertexIndex, lightEmission),
                normal.x(),
                normal.y(),
                normal.z()
            );
        }
    }
    //?} else if >=1.21.11 {
    /*// Author: literal.uu
    // Reason: Decode and transform baked quads without allocating temporary position vectors.
    @Overwrite
    default void putBulkData(
        PoseStack.Pose pose,
        BakedQuad quad,
        float[] brightness,
        float red,
        float green,
        float blue,
        float alpha,
        int[] lights,
        int overlay
    ) {
        VertexConsumer consumer = (VertexConsumer)(Object)this;
        Matrix4fc matrix = pose.pose();
        Vector3fc faceNormal = quad.direction().getUnitVec3f();
        Vector3f normal = pose.transformNormal(faceNormal, KERNEL_NORMAL_SCRATCH.get());
        int lightEmission = quad.lightEmission();

        for (int vertexIndex = 0; vertexIndex < BakedQuad.VERTEX_COUNT; vertexIndex++) {
            Vector3fc position = quad.position(vertexIndex);
            float x = position.x();
            float y = position.y();
            float z = position.z();
            float shade = brightness[vertexIndex];
            long packedUv = quad.packedUV(vertexIndex);

            consumer.addVertex(
                FastVertexMath.transformX(matrix, x, y, z),
                FastVertexMath.transformY(matrix, x, y, z),
                FastVertexMath.transformZ(matrix, x, y, z),
                ARGB.colorFromFloat(alpha, shade * red, shade * green, shade * blue),
                UVPair.unpackU(packedUv),
                UVPair.unpackV(packedUv),
                overlay,
                LightTexture.lightCoordsWithEmission(lights[vertexIndex], lightEmission),
                normal.x(),
                normal.y(),
                normal.z()
            );
        }
    }
    *///?} else if >=1.21.5 {
    /*// Author: literal.uu
    // Reason: Decode legacy packed quad vertices without a temporary native buffer or position vectors.
    @Overwrite
    default void putBulkData(
        PoseStack.Pose pose,
        BakedQuad quad,
        float[] brightness,
        float red,
        float green,
        float blue,
        float alpha,
        int[] lights,
        int overlay,
        boolean useVertexColor
    ) {
        VertexConsumer consumer = (VertexConsumer)(Object)this;
        Matrix4fc matrix = pose.pose();
        Vec3i faceNormal = quad.direction().getUnitVec3i();
        int[] vertices = quad.vertices();
        int lightEmission = quad.lightEmission();
        Vector3f normal = pose.transformNormal(faceNormal.getX(), faceNormal.getY(), faceNormal.getZ(), KERNEL_NORMAL_SCRATCH.get());

        for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
            int base = vertexIndex * 8;
            float x = Float.intBitsToFloat(vertices[base]);
            float y = Float.intBitsToFloat(vertices[base + 1]);
            float z = Float.intBitsToFloat(vertices[base + 2]);

            consumer.addVertex(
                FastVertexMath.transformX(matrix, x, y, z),
                FastVertexMath.transformY(matrix, x, y, z),
                FastVertexMath.transformZ(matrix, x, y, z),
                FastVertexMath.legacyColor(
                    vertices[base + 3],
                    brightness[vertexIndex],
                    red,
                    green,
                    blue,
                    alpha,
                    useVertexColor
                ),
                Float.intBitsToFloat(vertices[base + 4]),
                Float.intBitsToFloat(vertices[base + 5]),
                overlay,
                LightTexture.lightCoordsWithEmission(lights[vertexIndex], lightEmission),
                normal.x(),
                normal.y(),
                normal.z()
            );
        }
    }
    *///?} else {
    /*// Author: literal.uu
    // Reason: Decode legacy packed quad vertices without a temporary native buffer or position vectors.
    @Overwrite
    default void putBulkData(
        PoseStack.Pose pose,
        BakedQuad quad,
        float[] brightness,
        float red,
        float green,
        float blue,
        float alpha,
        int[] lights,
        int overlay,
        boolean useVertexColor
    ) {
        VertexConsumer consumer = (VertexConsumer)(Object)this;
        Matrix4fc matrix = pose.pose();
        Vec3i faceNormal = quad.getDirection().getUnitVec3i();
        int[] vertices = quad.getVertices();
        int lightEmission = quad.getLightEmission();
        Vector3f normal = pose.transformNormal(faceNormal.getX(), faceNormal.getY(), faceNormal.getZ(), KERNEL_NORMAL_SCRATCH.get());

        for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
            int base = vertexIndex * 8;
            float x = Float.intBitsToFloat(vertices[base]);
            float y = Float.intBitsToFloat(vertices[base + 1]);
            float z = Float.intBitsToFloat(vertices[base + 2]);

            consumer.addVertex(
                FastVertexMath.transformX(matrix, x, y, z),
                FastVertexMath.transformY(matrix, x, y, z),
                FastVertexMath.transformZ(matrix, x, y, z),
                FastVertexMath.legacyColor(
                    vertices[base + 3],
                    brightness[vertexIndex],
                    red,
                    green,
                    blue,
                    alpha,
                    useVertexColor
                ),
                Float.intBitsToFloat(vertices[base + 4]),
                Float.intBitsToFloat(vertices[base + 5]),
                overlay,
                LightTexture.lightCoordsWithEmission(lights[vertexIndex], lightEmission),
                normal.x(),
                normal.y(),
                normal.z()
            );
        }
    }
    *///?}
}
