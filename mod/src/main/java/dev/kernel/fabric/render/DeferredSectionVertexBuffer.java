package dev.kernel.fabric.render;

//? if <=1.21.4 {
/*import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.vertex.VertexBuffer;

// Identifies only the legacy section-layer buffers whose native allocation may be deferred.
public final class DeferredSectionVertexBuffer extends VertexBuffer {
    public DeferredSectionVertexBuffer(BufferUsage usage) { super(usage); }
}
*///? } else {
/** Newer renderers already create their layer buffers on upload. */
public final class DeferredSectionVertexBuffer {
    private DeferredSectionVertexBuffer() {}
}
//? }
