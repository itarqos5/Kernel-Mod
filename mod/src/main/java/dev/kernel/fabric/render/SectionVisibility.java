package dev.kernel.fabric.render;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Computes section-face connectivity by visiting horizontal runs, rather than individual air cells.
 * Coordinates use Minecraft's section layout: x in bits 0..3, z in 4..7, y in 8..11.
 * Face bits are DOWN, UP, NORTH, SOUTH, WEST, EAST; pair (a, b) occupies bit a + 6*b.
 */
public final class SectionVisibility {
    public static final long ALL_VISIBLE = (1L << 36) - 1;
    private static final int ROW_MASK = 0xffff;
    private static final long[] FACE_CONNECTIONS = createFaceConnections();
    private static final ReentrantThreadLocalPool<Scratch> SCRATCH =
        new ReentrantThreadLocalPool<>(Scratch::new, scratch -> {});

    private SectionVisibility() {
    }

    /**
     * Retains vanilla's sparse-section shortcut and destructive visited-bit semantics. The empty count is
     * supplied by VisGraph, not inferred from the bits: those bits may already contain visited cells.
     */
    public static long resolve(BitSet occupied, int emptyCount) {
        if (4096 - emptyCount < 256) {
            return ALL_VISIBLE;
        }
        if (emptyCount == 0) {
            return 0L;
        }

        Scratch scratch = SCRATCH.acquire();
        try {
            return scratch.resolve(occupied, emptyCount);
        } finally {
            SCRATCH.release();
        }
    }

    /** The entire open run containing a known open cell, without crossing either end of the row. */
    static int spanAt(int open, int cellBit) {
        int blocked = ~open & ROW_MASK;
        int left = 32 - Integer.numberOfLeadingZeros(blocked & (cellBit - 1));
        int right = Math.min(16, Integer.numberOfTrailingZeros(blocked & -cellBit));
        return (ROW_MASK << left) & (ROW_MASK >>> (16 - right));
    }

    private static long[] createFaceConnections() {
        long[] connections = new long[64];
        for (int faces = 0; faces < connections.length; faces++) {
            for (int first = 0; first < 6; first++) {
                if ((faces & (1 << first)) != 0) {
                    for (int second = 0; second < 6; second++) {
                        if ((faces & (1 << second)) != 0) {
                            connections[faces] |= 1L << (first + 6 * second);
                        }
                    }
                }
            }
        }
        return connections;
    }

    private static final class Scratch {
        private final int[] openRows = new int[256];
        // Every queued run claims at least one previously unvisited cell. No section can exceed this bound.
        private final int[] queue = new int[4096];
        private int tail;

        private long resolve(BitSet occupied, int emptyCount) {
            // Dense sections often contain only small isolated cavities. Read their few empty cells instead
            // of scanning thousands of opaque cells before doing a tiny boundary traversal.
            if (emptyCount <= 2048) {
                Arrays.fill(this.openRows, 0);
                for (int cell = occupied.nextClearBit(0); cell < 4096; cell = occupied.nextClearBit(cell + 1)) {
                    this.openRows[cell >>> 4] |= 1 << (cell & 15);
                }
            } else {
                Arrays.fill(this.openRows, ROW_MASK);
                for (int cell = occupied.nextSetBit(0); cell >= 0 && cell < 4096; cell = occupied.nextSetBit(cell + 1)) {
                    this.openRows[cell >>> 4] &= ~(1 << (cell & 15));
                }
            }

            long connections = 0L;
            for (int row = 0; row < 256; row++) {
                int z = row & 15;
                int y = row >>> 4;
                int boundary = (z == 0 || z == 15 || y == 0 || y == 15) ? ROW_MASK : 0x8001;
                int seeds;
                while ((seeds = this.openRows[row] & boundary) != 0) {
                    this.tail = 0;
                    this.claim(row, Integer.lowestOneBit(seeds), occupied);
                    int faces = 0;
                    for (int head = 0; head < this.tail; head++) {
                        int entry = this.queue[head];
                        int currentRow = entry >>> 16;
                        int span = entry & ROW_MASK;
                        int currentZ = currentRow & 15;
                        int currentY = currentRow >>> 4;

                        if ((span & 1) != 0) faces |= 1 << 4;
                        if ((span & 0x8000) != 0) faces |= 1 << 5;
                        if (currentY == 0) faces |= 1;
                        else this.visit(currentRow - 16, span, occupied);
                        if (currentY == 15) faces |= 1 << 1;
                        else this.visit(currentRow + 16, span, occupied);
                        if (currentZ == 0) faces |= 1 << 2;
                        else this.visit(currentRow - 1, span, occupied);
                        if (currentZ == 15) faces |= 1 << 3;
                        else this.visit(currentRow + 1, span, occupied);
                    }
                    connections |= FACE_CONNECTIONS[faces];
                    // Do not return early even when all pairs are visible: vanilla still marks the other
                    // boundary-connected components visited, which matters if resolve is invoked again.
                }
            }
            return connections;
        }

        private void visit(int row, int parentSpan, BitSet occupied) {
            int overlap;
            while ((overlap = this.openRows[row] & parentSpan) != 0) {
                this.claim(row, Integer.lowestOneBit(overlap), occupied);
            }
        }

        private void claim(int row, int cellBit, BitSet occupied) {
            int span = spanAt(this.openRows[row], cellBit);
            this.openRows[row] &= ~span;
            this.queue[this.tail++] = (row << 16) | span;
            int base = row << 4;
            occupied.set(base + Integer.numberOfTrailingZeros(span), base + 32 - Integer.numberOfLeadingZeros(span));
        }
    }
}
