package dev.kernel.fabric.render;

import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.core.Direction;

import java.util.BitSet;

/** Keeps Minecraft's public visibility representation and graph traversal intact. */
public final class SectionVisibilityAdapter {
    private static final Direction[] FACES = {
        Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private SectionVisibilityAdapter() {
    }

    public static VisibilitySet resolve(BitSet occupied, int emptyCount) {
        VisibilitySet result = new VisibilitySet();
        if (4096 - emptyCount < 256) {
            result.setAll(true);
            return result;
        }
        if (emptyCount == 0) return result;
        long connections = SectionVisibility.resolve(occupied, emptyCount);
        if (connections == SectionVisibility.ALL_VISIBLE) {
            result.setAll(true);
        } else if (connections != 0L) {
            for (int first = 0; first < FACES.length; first++) {
                for (int second = first; second < FACES.length; second++) {
                    if ((connections & (1L << (first + 6 * second))) != 0L) {
                        result.set(FACES[first], FACES[second], true);
                    }
                }
            }
        }
        return result;
    }
}
