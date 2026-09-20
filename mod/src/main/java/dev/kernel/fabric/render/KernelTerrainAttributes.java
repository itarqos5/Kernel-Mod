package dev.kernel.fabric.render;

import dev.kernel.fabric.shader.KernelBlockIdentities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block a chunk section is currently meshing, for the vertex attributes a shader pack asked for.
 *
 * <p>A pack's extended attributes describe the block a vertex belongs to, but the buffer that receives
 * the vertex knows only coordinates. This carries the block across that gap: the block renderer records
 * which block it is about to tessellate, and the buffer reads it while filling each vertex.
 *
 * <p>Sections are meshed on worker threads, several at once, so the record is per thread. A buffer takes
 * a direct reference to its own thread's record when it is built rather than looking one up for every
 * vertex, which is safe because a section's buffers are built and filled by the one thread that owns
 * them, and it keeps the cost on the hot path to a field read.
 *
 * <p>Nothing here is reached unless a pack declared an attribute that needs it. A buffer whose format
 * carries no Kernel element never takes a reference at all.
 */
public final class KernelTerrainAttributes {
    /** Sixty-fourths of a block, the unit the format gives the offset to a block's centre in. */
    private static final float MID_BLOCK_SCALE = 64.0f;

    /** One thread's current block. Mutable and reused; never published beyond its own thread. */
    public static final class Current {
        /** The identity the pack's block.properties gave this block, or zero for a block it did not name. */
        public int identity;
        /** The block's centre, in the section-local space the vertices being written use. */
        public float centreX, centreY, centreZ;

        void clear() { identity = 0; centreX = centreY = centreZ = 0.0f; }
    }

    private static final ThreadLocal<Current> CURRENT = ThreadLocal.withInitial(Current::new);

    private KernelTerrainAttributes() {}

    /** This thread's record, taken once by a buffer that has Kernel elements to fill. */
    public static Current current() { return CURRENT.get(); }

    /**
     * Records the block about to be tessellated.
     *
     * <p>The centre is computed in section-local coordinates because that is the space the chunk builder
     * writes vertices in: it translates the pose by the block's position within its section, so a
     * vertex of the block at section-local {@code (x, y, z)} lands near that, and the centre is half a
     * block further on each axis.
     */
    public static void begin(BlockState state, BlockPos position) {
        Current current = CURRENT.get();
        current.identity = KernelBlockIdentities.identity(state);
        current.centreX = (position.getX() & 15) + 0.5f;
        current.centreY = (position.getY() & 15) + 0.5f;
        current.centreZ = (position.getZ() & 15) + 0.5f;
    }

    /** Forgets the block, so anything drawn outside a block is not given that block's identity. */
    public static void end() { CURRENT.get().clear(); }

    /**
     * The offset from a vertex to its block's centre, in the signed bytes the format stores.
     *
     * <p>Clamped rather than wrapped. A model reaching more than two blocks past its own centre would
     * otherwise be given an offset pointing the opposite way, which waving foliage would read as a
     * violent jump; holding at the edge of the range merely stops the effect growing.
     */
    public static byte offset(float distance) {
        int scaled = Math.round(distance * MID_BLOCK_SCALE);
        return (byte) Math.max(Byte.MIN_VALUE, Math.min(Byte.MAX_VALUE, scaled));
    }
}
