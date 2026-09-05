package dev.kernel.fabric.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class BlockQuadUploadScratchTest {
    @Test
    void rewritesBrightnessValuesWithoutReplacingTheArray() {
        BlockQuadUploadScratch scratch = new BlockQuadUploadScratch();
        float[] first = scratch.brightness(0.1F, 0.2F, 0.3F, 0.4F);

        assertArrayEquals(new float[]{0.1F, 0.2F, 0.3F, 0.4F}, first);
        assertSame(first, scratch.brightness(0.5F, 0.6F, 0.7F, 0.8F));
        assertArrayEquals(new float[]{0.5F, 0.6F, 0.7F, 0.8F}, first);
    }

    @Test
    void rewritesLightValuesWithoutReplacingTheArray() {
        BlockQuadUploadScratch scratch = new BlockQuadUploadScratch();
        int[] first = scratch.light(1, 2, 3, 4);

        assertArrayEquals(new int[]{1, 2, 3, 4}, first);
        assertSame(first, scratch.light(5, 6, 7, 8));
        assertArrayEquals(new int[]{5, 6, 7, 8}, first);
    }
}
