package dev.kernel.fabric.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class BlockFaceVisibilityCacheTest {
    @Test
    void storesBothVisibilityResults() {
        BlockFaceVisibilityCache cache = new BlockFaceVisibilityCache(4);
        Object first = new Object();
        Object second = new Object();
        Object third = new Object();

        assertEquals(BlockFaceVisibilityCache.MISS, cache.find(first, second));

        cache.put(first, second, true);
        cache.put(first, third, false);

        assertEquals(BlockFaceVisibilityCache.VISIBLE, cache.find(first, second));
        assertEquals(BlockFaceVisibilityCache.HIDDEN, cache.find(first, third));
    }

    @Test
    void comparesPairMembersByIdentity() {
        BlockFaceVisibilityCache cache = new BlockFaceVisibilityCache(4);
        String storedFirst = new String("same");
        String equalFirst = new String("same");
        Object second = new Object();

        cache.put(storedFirst, second, true);

        assertEquals(BlockFaceVisibilityCache.VISIBLE, cache.find(storedFirst, second));
        assertEquals(BlockFaceVisibilityCache.MISS, cache.find(equalFirst, second));
    }

    @Test
    void evictsTheLeastRecentlyUsedWayWithoutReturningAStaleResult() {
        BlockFaceVisibilityCache cache = new BlockFaceVisibilityCache(1);
        Object firstA = new Object();
        Object secondA = new Object();
        Object firstB = new Object();
        Object secondB = new Object();
        Object firstC = new Object();
        Object secondC = new Object();

        cache.put(firstA, secondA, true);
        cache.put(firstB, secondB, false);
        assertEquals(BlockFaceVisibilityCache.VISIBLE, cache.find(firstA, secondA));

        cache.put(firstC, secondC, true);

        assertEquals(BlockFaceVisibilityCache.VISIBLE, cache.find(firstA, secondA));
        assertEquals(BlockFaceVisibilityCache.MISS, cache.find(firstB, secondB));
        assertEquals(BlockFaceVisibilityCache.VISIBLE, cache.find(firstC, secondC));
    }

    @Test
    void exposesOneCachePerThreadAndRejectsInvalidSizes() {
        assertSame(BlockFaceVisibilityCache.get(), BlockFaceVisibilityCache.get());
        assertThrows(IllegalArgumentException.class, () -> new BlockFaceVisibilityCache(3));
    }
}
