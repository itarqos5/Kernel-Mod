package dev.kernel.fabric.render;

/**
 * Scalar form of Minecraft's weighted fluid-corner height calculation.
 */
public final class FluidHeightMath {
    private FluidHeightMath() {
    }

    public static float weightedAverage(
        float center,
        float firstAdjacent,
        float secondAdjacent,
        float diagonal,
        boolean includeDiagonal
    ) {
        if (secondAdjacent >= 1.0F || firstAdjacent >= 1.0F || includeDiagonal && diagonal >= 1.0F) {
            return 1.0F;
        }

        float weightedHeight = 0.0F;
        float totalWeight = 0.0F;
        if (includeDiagonal) {
            if (diagonal >= 0.8F) {
                weightedHeight += diagonal * 10.0F;
                totalWeight += 10.0F;
            } else if (diagonal >= 0.0F) {
                weightedHeight += diagonal;
                totalWeight++;
            }
        }

        if (center >= 0.8F) {
            weightedHeight += center * 10.0F;
            totalWeight += 10.0F;
        } else if (center >= 0.0F) {
            weightedHeight += center;
            totalWeight++;
        }

        if (secondAdjacent >= 0.8F) {
            weightedHeight += secondAdjacent * 10.0F;
            totalWeight += 10.0F;
        } else if (secondAdjacent >= 0.0F) {
            weightedHeight += secondAdjacent;
            totalWeight++;
        }

        if (firstAdjacent >= 0.8F) {
            weightedHeight += firstAdjacent * 10.0F;
            totalWeight += 10.0F;
        } else if (firstAdjacent >= 0.0F) {
            weightedHeight += firstAdjacent;
            totalWeight++;
        }

        return weightedHeight / totalWeight;
    }
}
