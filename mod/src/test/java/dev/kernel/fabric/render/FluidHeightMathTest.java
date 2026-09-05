package dev.kernel.fabric.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FluidHeightMathTest {
    private static final float[] HEIGHTS = {
        Float.NaN,
        -1.0F,
        -0.0F,
        0.0F,
        0.2F,
        0.79F,
        0.8F,
        0.99F,
        1.0F
    };

    @Test
    void scalarCalculationMatchesTheLegacyAccumulatorForBoundaryValues() {
        for (float center : HEIGHTS) {
            for (float firstAdjacent : HEIGHTS) {
                for (float secondAdjacent : HEIGHTS) {
                    for (float diagonal : HEIGHTS) {
                        boolean includeDiagonal = secondAdjacent > 0.0F || firstAdjacent > 0.0F;
                        float expected = reference(center, firstAdjacent, secondAdjacent, diagonal, includeDiagonal);
                        float actual = FluidHeightMath.weightedAverage(
                            center,
                            firstAdjacent,
                            secondAdjacent,
                            diagonal,
                            includeDiagonal
                        );

                        assertEquals(
                            Float.floatToIntBits(expected),
                            Float.floatToIntBits(actual),
                            () -> "mismatch for center=" + center
                                + ", firstAdjacent=" + firstAdjacent
                                + ", secondAdjacent=" + secondAdjacent
                                + ", diagonal=" + diagonal
                        );
                    }
                }
            }
        }
    }

    private static float reference(
        float center,
        float firstAdjacent,
        float secondAdjacent,
        float diagonal,
        boolean includeDiagonal
    ) {
        if (secondAdjacent >= 1.0F || firstAdjacent >= 1.0F) {
            return 1.0F;
        }

        float[] accumulator = new float[2];
        if (includeDiagonal) {
            if (diagonal >= 1.0F) {
                return 1.0F;
            }
            addWeightedHeight(accumulator, diagonal);
        }

        addWeightedHeight(accumulator, center);
        addWeightedHeight(accumulator, secondAdjacent);
        addWeightedHeight(accumulator, firstAdjacent);
        return accumulator[0] / accumulator[1];
    }

    private static void addWeightedHeight(float[] accumulator, float height) {
        if (height >= 0.8F) {
            accumulator[0] += height * 10.0F;
            accumulator[1] += 10.0F;
        } else if (height >= 0.0F) {
            accumulator[0] += height;
            accumulator[1]++;
        }
    }
}
