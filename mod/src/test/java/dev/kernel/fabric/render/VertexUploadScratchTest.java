package dev.kernel.fabric.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VertexUploadScratchTest {
    @Test void nativeLeasesIsolateNestedUploadsAndReleaseAfterExceptions() {
        float[] primary;
        try (var first = VertexUploadScratch.acquireQuad(true, 11)) {
            primary = first.brightness();
            first.brightness()[2] = 0.25F;
            try (var nested = VertexUploadScratch.acquireQuad(true, 22)) {
                assertNotSame(primary, nested.brightness());
                assertArrayEquals(new int[]{11, 11, 11, 11}, first.lights());
                assertArrayEquals(new int[]{22, 22, 22, 22}, nested.lights());
                assertEquals(0.25F, first.brightness()[2]);
            }
        }
        assertThrows(IllegalStateException.class, () -> {
            try (var failed = VertexUploadScratch.acquireQuad(true, 1)) {
                assertSame(primary, failed.brightness());
                throw new IllegalStateException("consumer failure");
            }
        });
        try (var later = VertexUploadScratch.acquireQuad(true, 33)) {
            assertSame(primary, later.brightness());
            assertArrayEquals(new float[]{1, 1, 1, 1}, later.brightness());
        }
    }

    @Test void customConsumersMayRetainConvenienceArraysAfterReturning() {
        int[] retained;
        try (var custom = VertexUploadScratch.acquireQuad(false, 11)) { retained = custom.lights(); }
        try (var later = VertexUploadScratch.acquireQuad(false, 22)) {
            assertNotSame(retained, later.lights());
            assertArrayEquals(new int[]{11, 11, 11, 11}, retained);
        }
    }
}
