package dev.kernel.fabric.world;

import java.util.BitSet;

/** Internal, call-scoped access; callers must not retain or mutate the native occupancy. */
public interface ShapeStorageAccess {
    BitSet kernel$storage();
}
