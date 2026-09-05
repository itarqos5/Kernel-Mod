package dev.kernel.fabric.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kernel.fabric.render.FastVertexMath;
import dev.kernel.fabric.render.ModelRenderScratch;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Emits model-cube vertices without allocating transformation vectors.
 */
@Mixin(ModelPart.Cube.class)
public abstract class ModelPartCubeMixin {
    @Shadow
    @Final
    public ModelPart.Polygon[] polygons;

    // Author: literal.uu
    // Reason: Reuse normal scratch storage and transform model vertices with scalar matrix operations.
    @Overwrite
    public void compile(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int color) {
        Matrix4fc matrix = pose.pose();
        Vector3f normalScratch = ModelRenderScratch.get().normal();

        for (ModelPart.Polygon polygon : this.polygons) {
            Vector3f normal = pose.transformNormal(polygon.normal(), normalScratch);
            float normalX = normal.x();
            float normalY = normal.y();
            float normalZ = normal.z();

            for (ModelPart.Vertex vertex : polygon.vertices()) {
                //? if >=1.21.9 {
                float x = vertex.worldX();
                float y = vertex.worldY();
                float z = vertex.worldZ();
                //?} else {
                /*Vector3f position = vertex.pos();
                float x = position.x() / 16.0F;
                float y = position.y() / 16.0F;
                float z = position.z() / 16.0F;
                *///?}

                consumer.addVertex(
                    FastVertexMath.transformX(matrix, x, y, z),
                    FastVertexMath.transformY(matrix, x, y, z),
                    FastVertexMath.transformZ(matrix, x, y, z),
                    color,
                    vertex.u(),
                    vertex.v(),
                    overlay,
                    light,
                    normalX,
                    normalY,
                    normalZ
                );
            }
        }
    }
}
