package dev.kernel.fabric.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kernel.fabric.render.FastVertexMath;
import dev.kernel.fabric.render.VertexUploadScratch;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

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
 * Removes temporary objects and buffers from common vertex transformation and baked-quad upload paths.
 */
@Mixin(VertexConsumer.class)
public interface VertexConsumerMixin {
    //? if >=1.21.11 {
    // Author: literal.uu
    // Reason: Transform immediate-mode positions without allocating a temporary vector.
    @Overwrite
    default VertexConsumer addVertex(Matrix4fc matrix, float x, float y, float z) {
        return ((VertexConsumer)(Object)this).addVertex(
            FastVertexMath.transformX(matrix, x, y, z),
            FastVertexMath.transformY(matrix, x, y, z),
            FastVertexMath.transformZ(matrix, x, y, z)
        );
    }
    //?} else {
    /*// Author: literal.uu
    // Reason: Transform immediate-mode positions without allocating a temporary vector.
    @Overwrite
    default VertexConsumer addVertex(Matrix4f matrix, float x, float y, float z) {
        return ((VertexConsumer)(Object)this).addVertex(
            FastVertexMath.transformX(matrix, x, y, z),
            FastVertexMath.transformY(matrix, x, y, z),
            FastVertexMath.transformZ(matrix, x, y, z)
        );
    }
    *///?}

    // Author: literal.uu
    // Reason: Reuse a thread-local normal vector instead of allocating one for every transformed normal.
    @Overwrite
    default VertexConsumer setNormal(PoseStack.Pose pose, float x, float y, float z) {
        Vector3f normal = pose.transformNormal(x, y, z, VertexUploadScratch.normal());
        return ((VertexConsumer)(Object)this).setNormal(normal.x(), normal.y(), normal.z());
    }

    //? if >=1.21.11 {
    // Author: literal.uu
    // Reason: Transform immediate-mode 2D positions without allocating a temporary vector.
    @Overwrite
    default VertexConsumer addVertexWith2DPose(Matrix3x2fc matrix, float x, float y) {
        return ((VertexConsumer)(Object)this).addVertex(
            FastVertexMath.transform2DX(matrix, x, y),
            FastVertexMath.transform2DY(matrix, x, y),
            0.0F
        );
    }
    //?} else if >=1.21.9 {
    /*// Author: literal.uu
    // Reason: Transform immediate-mode 2D positions without allocating a temporary vector.
    @Overwrite
    default VertexConsumer addVertexWith2DPose(Matrix3x2f matrix, float x, float y) {
        return ((VertexConsumer)(Object)this).addVertex(
            FastVertexMath.transform2DX(matrix, x, y),
            FastVertexMath.transform2DY(matrix, x, y),
            0.0F
        );
    }
    *///?} else if >=1.21.6 {
    /*// Author: literal.uu
    // Reason: Transform immediate-mode 2D positions without allocating a temporary vector.
    @Overwrite
    default VertexConsumer addVertexWith2DPose(Matrix3x2f matrix, float x, float y, float z) {
        return ((VertexConsumer)(Object)this).addVertex(
            FastVertexMath.transform2DX(matrix, x, y),
            FastVertexMath.transform2DY(matrix, x, y),
            z
        );
    }
    *///?}

    //? if >=26 {
    // Author: literal.uu
    // Reason: Decode and transform baked quads without allocating temporary position vectors.
    @Overwrite
    default void putBakedQuad(PoseStack.Pose pose, BakedQuad quad, QuadInstance instance) {
        VertexConsumer consumer = (VertexConsumer)(Object)this;
        Matrix4fc matrix = pose.pose();
        Vector3fc faceNormal = quad.direction().getUnitVec3f();
        Vector3f normal = pose.transformNormal(faceNormal, VertexUploadScratch.normal());
        float normalX = normal.x(), normalY = normal.y(), normalZ = normal.z();
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
                normalX,
                normalY,
                normalZ
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
        Vector3f normal = pose.transformNormal(faceNormal, VertexUploadScratch.normal());
        float normalX = normal.x(), normalY = normal.y(), normalZ = normal.z();
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
                normalX,
                normalY,
                normalZ
            );
        }
    }

    // Author: literal.uu
    // Reason: Reuse the uniform brightness and light arrays used by the convenience quad-upload overload.
    @Overwrite
    default void putBulkData(
        PoseStack.Pose pose,
        BakedQuad quad,
        float red,
        float green,
        float blue,
        float alpha,
        int light,
        int overlay
    ) {
        // Unknown consumers may retain their arrays; only the native builder receives leased storage.
        try (var scratch = VertexUploadScratch.acquireQuad(((Object) this).getClass() == BufferBuilder.class, light)) {
            ((VertexConsumer)(Object)this).putBulkData(
                pose, quad, scratch.brightness(), red, green, blue, alpha, scratch.lights(), overlay
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
        Vector3f normal = pose.transformNormal(faceNormal.getX(), faceNormal.getY(), faceNormal.getZ(), VertexUploadScratch.normal());
        float normalX = normal.x(), normalY = normal.y(), normalZ = normal.z();

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
                normalX,
                normalY,
                normalZ
            );
        }
    }

    // Author: literal.uu
    // Reason: Reuse the uniform brightness and light arrays used by the convenience quad-upload overload.
    @Overwrite
    default void putBulkData(
        PoseStack.Pose pose,
        BakedQuad quad,
        float red,
        float green,
        float blue,
        float alpha,
        int light,
        int overlay
    ) {
        // Unknown consumers may retain their arrays; only the native builder receives leased storage.
        try (var scratch = VertexUploadScratch.acquireQuad(((Object) this).getClass() == BufferBuilder.class, light)) {
            ((VertexConsumer)(Object)this).putBulkData(
                pose, quad, scratch.brightness(), red, green, blue, alpha, scratch.lights(), overlay, false
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
        Vector3f normal = pose.transformNormal(faceNormal.getX(), faceNormal.getY(), faceNormal.getZ(), VertexUploadScratch.normal());
        float normalX = normal.x(), normalY = normal.y(), normalZ = normal.z();

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
                normalX,
                normalY,
                normalZ
            );
        }
    }

    // Author: literal.uu
    // Reason: Reuse the uniform brightness and light arrays used by the convenience quad-upload overload.
    @Overwrite
    default void putBulkData(
        PoseStack.Pose pose,
        BakedQuad quad,
        float red,
        float green,
        float blue,
        float alpha,
        int light,
        int overlay
    ) {
        // Unknown consumers may retain their arrays; only the native builder receives leased storage.
        try (var scratch = VertexUploadScratch.acquireQuad(((Object) this).getClass() == BufferBuilder.class, light)) {
            ((VertexConsumer)(Object)this).putBulkData(
                pose, quad, scratch.brightness(), red, green, blue, alpha, scratch.lights(), overlay, false
            );
        }
    }
    *///?}
}
