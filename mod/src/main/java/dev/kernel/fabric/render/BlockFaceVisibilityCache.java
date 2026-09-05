package dev.kernel.fabric.render;

/**
 * A small, bounded identity-pair cache for block-face visibility results.
 *
 * <p>Voxel shapes used by block states are shared objects, so identity is both sufficient and cheaper than
 * allocating a compound lookup key. Two ways per set reduce collision churn while keeping lookup work bounded.
 */
public final class BlockFaceVisibilityCache {
    public static final byte MISS = -1;
    public static final byte HIDDEN = 0;
    public static final byte VISIBLE = 1;

    private static final int DEFAULT_SET_COUNT = 256;
    private static final int WAYS = 2;
    private static final ThreadLocal<BlockFaceVisibilityCache> LOCAL =
            ThreadLocal.withInitial(BlockFaceVisibilityCache::new);

    private final Object[] firstShapes;
    private final Object[] secondShapes;
    private final byte[] results;
    private final byte[] replacementWays;
    private final int setMask;

    private BlockFaceVisibilityCache() {
        this(DEFAULT_SET_COUNT);
    }

    BlockFaceVisibilityCache(int setCount) {
        if (setCount <= 0 || (setCount & (setCount - 1)) != 0) {
            throw new IllegalArgumentException("setCount must be a positive power of two");
        }

        this.firstShapes = new Object[setCount * WAYS];
        this.secondShapes = new Object[setCount * WAYS];
        this.results = new byte[setCount * WAYS];
        this.replacementWays = new byte[setCount];
        this.setMask = setCount - 1;
    }

    public static BlockFaceVisibilityCache get() {
        return LOCAL.get();
    }

    public byte find(Object firstShape, Object secondShape) {
        int set = setIndex(firstShape, secondShape);
        int firstWay = set * WAYS;
        if (matches(firstWay, firstShape, secondShape)) {
            this.replacementWays[set] = 1;
            return this.results[firstWay];
        }

        int secondWay = firstWay + 1;
        if (matches(secondWay, firstShape, secondShape)) {
            this.replacementWays[set] = 0;
            return this.results[secondWay];
        }

        return MISS;
    }

    public void put(Object firstShape, Object secondShape, boolean visible) {
        int set = setIndex(firstShape, secondShape);
        int firstWay = set * WAYS;
        int slot;
        if (this.firstShapes[firstWay] == null || matches(firstWay, firstShape, secondShape)) {
            slot = firstWay;
            this.replacementWays[set] = 1;
        } else {
            int secondWay = firstWay + 1;
            if (this.firstShapes[secondWay] == null || matches(secondWay, firstShape, secondShape)) {
                slot = secondWay;
                this.replacementWays[set] = 0;
            } else {
                int way = this.replacementWays[set];
                slot = firstWay + way;
                this.replacementWays[set] = (byte) (way ^ 1);
            }
        }

        this.firstShapes[slot] = firstShape;
        this.secondShapes[slot] = secondShape;
        this.results[slot] = visible ? VISIBLE : HIDDEN;
    }

    private boolean matches(int slot, Object firstShape, Object secondShape) {
        return this.firstShapes[slot] == firstShape && this.secondShapes[slot] == secondShape;
    }

    private int setIndex(Object firstShape, Object secondShape) {
        int hash = System.identityHashCode(firstShape) * 31 + System.identityHashCode(secondShape);
        hash ^= hash >>> 16;
        hash *= 0x7FEB352D;
        hash ^= hash >>> 15;
        return hash & this.setMask;
    }
}
