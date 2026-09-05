package dev.kernel.fabric.render;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ChunkGpuUploadSchedulerTest {
    @Test
    void preservesOrderAndStopsAfterTheTimeBudget() {
        AtomicLong clock = new AtomicLong();
        List<Integer> completed = new ArrayList<>();
        Queue<Runnable> uploads = new ArrayDeque<>();
        for (int index = 0; index < 5; index++) {
            int uploadIndex = index;
            uploads.add(() -> {
                completed.add(uploadIndex);
                clock.addAndGet(600L);
            });
        }

        int drained = ChunkGpuUploadScheduler.drain(uploads, false, clock::get, 1_000L, 10);

        assertEquals(2, drained);
        assertEquals(List.of(0, 1), completed);
        assertEquals(3, uploads.size());
    }

    @Test
    void guaranteesProgressEvenWithNoTimeBudget() {
        Queue<Runnable> uploads = new ArrayDeque<>();
        List<Integer> completed = new ArrayList<>();
        uploads.add(() -> completed.add(1));
        uploads.add(() -> completed.add(2));

        int drained = ChunkGpuUploadScheduler.drain(uploads, false, () -> 0L, 0L, 10);

        assertEquals(1, drained);
        assertEquals(List.of(1), completed);
        assertEquals(1, uploads.size());
    }

    @Test
    void capsUploadsWhenTheClockDoesNotAdvance() {
        Queue<Runnable> uploads = new ArrayDeque<>();
        List<Integer> completed = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            int uploadIndex = index;
            uploads.add(() -> completed.add(uploadIndex));
        }

        int drained = ChunkGpuUploadScheduler.drain(uploads, false, () -> 0L, 1_000L, 3);

        assertEquals(3, drained);
        assertEquals(List.of(0, 1, 2), completed);
        assertEquals(2, uploads.size());
    }

    @Test
    void drainsEveryUploadDuringShutdown() {
        Queue<Runnable> uploads = new ArrayDeque<>();
        List<Integer> completed = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            int uploadIndex = index;
            uploads.add(() -> completed.add(uploadIndex));
        }

        int drained = ChunkGpuUploadScheduler.drain(uploads, true, () -> 0L, 0L, 1);

        assertEquals(5, drained);
        assertEquals(List.of(0, 1, 2, 3, 4), completed);
        assertEquals(0, uploads.size());
    }

    @Test
    void propagatesUploadFailuresAndLeavesLaterWorkQueued() {
        Queue<Runnable> uploads = new ArrayDeque<>();
        uploads.add(() -> {
            throw new IllegalStateException("upload failed");
        });
        uploads.add(() -> {
        });

        assertThrows(
            IllegalStateException.class,
            () -> ChunkGpuUploadScheduler.drain(uploads, false, () -> 0L, 1_000L, 10)
        );
        assertEquals(1, uploads.size());
    }
}
