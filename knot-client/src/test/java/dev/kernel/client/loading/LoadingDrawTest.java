package dev.kernel.client.loading;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class LoadingDrawTest {
    @Test void fitsSmallScaledAndWideWindowsAndAlwaysCoversTheNativeSplash() {
        for (int[] size : new int[][]{{320,180},{480,270},{960,540},{1920,320},{3840,2160}}) {
            for (double progress : new double[]{-1, Double.NaN, 0, 0.3, 1, 1.5}) {
                int[] frame = LoadingDraw.frame(size[0], size[1], progress, 12_345);
                assertEquals(0, frame[1]); assertEquals(0, frame[2]);
                assertEquals(size[0], frame[3]); assertEquals(size[1], frame[4]);
                assertEquals(255, frame[5] >>> 24);
                assertEquals(1, frame[0] % 5);
                for (int i = 1; i < frame[0]; i += 5) {
                    assertTrue(frame[i] >= 0 && frame[i+1] >= 0);
                    assertTrue(frame[i+2] > 0 && frame[i+3] > 0);
                    assertTrue(frame[i] + frame[i+2] <= size[0]);
                    assertTrue(frame[i+1] + frame[i+3] <= size[1]);
                }
            }
        }
    }
}
