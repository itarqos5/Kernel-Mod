package dev.kernel.fabric.world;

import java.util.BitSet;

/** Ordinary interface remains loadable when the optional construction Mixin is disabled. */
public interface ShapeConstructionAccess {
    BitSet kernel$constructionStorage();
    void kernel$constructionBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ);
}
