package dev.kernel.fabric.render;

import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.BitSet;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

final class SectionVisibilityTest {
    private static final Direction[] FACES = {
        Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    @Test
    void matchesVanillaAtSparseShortcutAndSolidSectionBoundaries() throws Exception {
        for (int solids : new int[]{0, 1, 255, 256, 257, 4095, 4096}) {
            BitSet occupied = new BitSet(4096);
            occupied.set(0, solids);
            compare(occupied, 4096 - solids);
        }
    }

    @Test
    void solidPlanesDisconnectOppositeFacesOnEveryAxis() throws Exception {
        for (int axis = 0; axis < 3; axis++) {
            for (int offset = 0; offset < 16; offset++) {
                BitSet occupied = new BitSet(4096);
                for (int index = 0; index < 4096; index++) {
                    if (((index >>> (axis * 4)) & 15) == offset) occupied.set(index);
                }
                compare(occupied, 3840);
                long result = SectionVisibility.resolve((BitSet) occupied.clone(), 3840);
                int first = axis == 0 ? 4 : axis == 1 ? 2 : 0;
                assertEquals(0L, result & (1L << (first + 6 * (first + 1))));
            }
        }
    }

    @Test
    void handlesEnclosedRoomsSingleCellTunnelsAndDisconnectedBoundaryCavities() throws Exception {
        BitSet room = solidSection();
        for (int y = 1; y < 15; y++) {
            for (int z = 1; z < 15; z++) room.clear(index(1, y, z), index(15, y, z));
        }
        compare(room, 4096 - room.cardinality());
        assertEquals(0L, SectionVisibility.resolve((BitSet) room.clone(), 4096 - room.cardinality()));

        BitSet tunnels = solidSection();
        for (int cell = 0; cell < 16; cell++) {
            tunnels.clear(index(cell, 3, 5));
            tunnels.clear(index(7, cell, 5));
            tunnels.clear(index(7, 3, cell));
        }
        compare(tunnels, 4096 - tunnels.cardinality());
        assertEquals(SectionVisibility.ALL_VISIBLE,
            SectionVisibility.resolve((BitSet) tunnels.clone(), 4096 - tunnels.cardinality()));

        BitSet cavities = solidSection();
        // Adjacent packed indices must not join across a row boundary.
        cavities.clear(index(15, 4, 7));
        cavities.clear(index(0, 4, 8));
        compare(cavities, 2);
        long result = SectionVisibility.resolve((BitSet) cavities.clone(), 2);
        assertEquals(0L, result & (1L << (4 + 6 * 5)));
    }

    @Test
    void handlesMaximumFragmentationAndDoesNotLeakStateAcrossResolves() throws Exception {
        BitSet checkerboard = new BitSet(4096);
        for (int cell = 0; cell < 4096; cell++) {
            if ((((cell & 15) + ((cell >>> 4) & 15) + (cell >>> 8)) & 1) == 0) checkerboard.set(cell);
        }
        compare(checkerboard, 2048);
        compare(solidSection(), 0);
        compare(new BitSet(4096), 4096);
        compare(checkerboard, 2048);
    }

    @Test
    void matchesVanillaForRandomizedSectionsIncludingVisitedBitMutation() throws Exception {
        Random random = new Random(0x4b45524e454cL);
        double[] densities = {0.01, 0.06, 0.0625, 0.1, 0.25, 0.5, 0.75, 0.9, 0.99};
        for (int sample = 0; sample < 900; sample++) {
            BitSet occupied = new BitSet(4096);
            double density = densities[sample % densities.length];
            for (int cell = 0; cell < 4096; cell++) {
                if (random.nextDouble() < density) occupied.set(cell);
            }
            compare(occupied, 4096 - occupied.cardinality());
        }
    }

    @Test
    void preservesVanillaEmptyCounterWhenTheSameOpaqueCellIsMarkedTwice() throws Exception {
        BitSet occupied = new BitSet(4096);
        occupied.set(0, 255);
        // Vanilla counts setOpaque calls, including duplicates, rather than recalculating cardinality.
        compare(occupied, 3840);
        compare(occupied, 0);
        compare(occupied, -1);
    }

    @Test
    void findsExactlyTheContainingRunForEveryPossibleRowAndOpenSeed() {
        for (int row = 1; row <= 0xffff; row++) {
            for (int x = 0; x < 16; x++) {
                if ((row & (1 << x)) == 0) continue;
                int left = x;
                int right = x;
                while (left > 0 && (row & (1 << (left - 1))) != 0) left--;
                while (right < 15 && (row & (1 << (right + 1))) != 0) right++;
                int expected = 0;
                for (int bit = left; bit <= right; bit++) expected |= 1 << bit;
                assertEquals(expected, SectionVisibility.spanAt(row, 1 << x));
            }
        }
    }

    @Test
    void nestedSolvesUseIndependentScratchAndReleaseAfterFailure() {
        BitSet nested = new BitSet(4096) {
            private boolean entered;

            @Override
            public int nextSetBit(int fromIndex) {
                if (!this.entered) {
                    this.entered = true;
                    BitSet inner = solidSection();
                    inner.clear(0);
                    SectionVisibility.resolve(inner, 1);
                }
                return super.nextSetBit(fromIndex);
            }
        };
        nested.set(0, 256);
        BitSet expectedBits = (BitSet) nested.clone();
        // Convert to a plain BitSet so the oracle call does not trigger the nested override.
        expectedBits = BitSet.valueOf(expectedBits.toLongArray());
        long expected = SectionVisibility.resolve(expectedBits, 3840);
        assertEquals(expected, SectionVisibility.resolve(nested, 3840));
        assertEquals(expectedBits, nested);

        BitSet failing = new BitSet() {
            @Override
            public int nextSetBit(int fromIndex) {
                throw new IllegalStateException("test failure");
            }
        };
        assertThrows(IllegalStateException.class, () -> SectionVisibility.resolve(failing, 3840));
        BitSet next = new BitSet(4096);
        next.set(0, 256);
        assertEquals(expected, SectionVisibility.resolve(next, 3840));
    }

    @Test
    void concurrentChunkBuildersMatchVanillaIndependently() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            var jobs = IntStream.range(0, 16).<Callable<Void>>mapToObj(seed -> () -> {
                Random random = new Random(seed);
                for (int sample = 0; sample < 12; sample++) {
                    BitSet occupied = new BitSet(4096);
                    for (int cell = 0; cell < 4096; cell++) {
                        if (random.nextBoolean()) occupied.set(cell);
                    }
                    compare(occupied, 4096 - occupied.cardinality());
                }
                return null;
            }).toList();
            for (var result : executor.invokeAll(jobs)) result.get();
        }
    }

    private static void compare(BitSet initial, int emptyCount) throws Exception {
        VisGraph vanilla = new VisGraph();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int cell = initial.nextSetBit(0); cell >= 0; cell = initial.nextSetBit(cell + 1)) {
            vanilla.setOpaque(position.set(cell & 15, cell >>> 8, (cell >>> 4) & 15));
        }
        Field empty = VisGraph.class.getDeclaredField("empty");
        empty.setAccessible(true);
        empty.setInt(vanilla, emptyCount);
        Field bits = VisGraph.class.getDeclaredField("bitSet");
        bits.setAccessible(true);
        BitSet actualBits = (BitSet) initial.clone();

        // Unit tests run the actual mapped Minecraft class, without applying Kernel's Mixins.
        for (int invocation = 0; invocation < 2; invocation++) {
            VisibilitySet expected = vanilla.resolve();
            VisibilitySet actual = SectionVisibilityAdapter.resolve(actualBits, emptyCount);
            for (Direction first : FACES) {
                for (Direction second : FACES) {
                    assertEquals(expected.visibilityBetween(first, second), actual.visibilityBetween(first, second),
                        () -> "face mismatch " + first + " -> " + second);
                }
            }
            assertEquals(bits.get(vanilla), actualBits, "visited cells differ from vanilla");
        }
    }

    private static BitSet solidSection() {
        BitSet occupied = new BitSet(4096);
        occupied.set(0, 4096);
        return occupied;
    }

    private static int index(int x, int y, int z) {
        return x | (z << 4) | (y << 8);
    }
}
