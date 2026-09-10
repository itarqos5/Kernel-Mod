package dev.kernel.fabric.render;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class IndexedChunkTaskQueueTest {
    @Test
    void preservesClosestFirstTiesAndTwoRecompileQuota() {
        IndexedChunkTaskQueue queue = new IndexedChunkTaskQueue();
        Task fresh = new Task(10, false, true);
        Task first = new Task(1, true, true);
        Task second = new Task(2, true, true);
        Task third = new Task(3, true, true);
        queue.add(fresh); queue.add(first); queue.add(second); queue.add(third);
        assertSame(first, queue.poll(0, 0, 0));
        assertSame(second, queue.poll(0, 0, 0));
        assertSame(fresh, queue.poll(0, 0, 0));
        assertSame(third, queue.poll(0, 0, 0));
        Task tieInitial = new Task(1, false, true);
        Task tieAgain = new Task(1, true, true);
        queue.add(tieAgain); queue.add(tieInitial);
        assertSame(tieInitial, queue.poll(0, 0, 0));
        assertSame(tieAgain, queue.poll(0, 0, 0));
        Task tieOne = new Task(1, false, true);
        Task tieTwo = new Task(1, false, false);
        queue.add(tieOne); queue.add(tieTwo);
        assertSame(tieOne, queue.poll(0, 0, 0));
        assertSame(tieTwo, queue.poll(0, 0, 0));
    }

    @Test
    void matchesTheVanillaSelectionRulesThroughRandomQueueLifecycles() {
        IndexedChunkTaskQueue queue = new IndexedChunkTaskQueue();
        Reference reference = new Reference();
        List<Task> created = new ArrayList<>();
        Random random = new Random(57119);
        double camera = 0;
        for (int operation = 0; operation < 50_000; operation++) {
            int action = random.nextInt(100);
            if (action < 48) {
                Task task = new Task(random.nextInt(200) - 100, random.nextBoolean(), random.nextInt(4) != 0);
                created.add(task);
                queue.add(task);
                reference.tasks.add(task);
            } else if (action < 65 && !created.isEmpty()) {
                Task task = created.get(random.nextInt(created.size()));
                task.cancelled = true;
                if (task.nativeTask || random.nextBoolean()) task.kernel$notifyCancelled();
            } else if (action < 97) {
                if (random.nextInt(10) == 0) camera = random.nextInt(200) - 100;
                // Custom task positions may change without a cancellation or camera update.
                if (!created.isEmpty()) {
                    Task mutable = created.get(random.nextInt(created.size()));
                    if (!mutable.nativeTask) mutable.x += random.nextInt(5) - 2;
                }
                assertSame(reference.poll(camera), queue.poll(camera, 0, 0), "operation " + operation);
                assertEquals(reference.tasks.size(), queue.size());
            } else {
                queue.clear();
                reference.tasks.clear();
                assertEquals(0, queue.size());
            }
        }
        while (!reference.tasks.isEmpty()) assertSame(reference.poll(camera), queue.poll(camera, 0, 0));
        assertNull(queue.poll(camera, 0, 0));
    }

    @Test
    void cameraMovementQueriesFixedOriginsWithoutCallingNativeTaskGettersAgain() {
        IndexedChunkTaskQueue queue = new IndexedChunkTaskQueue();
        Task near = new Task(0, false, true);
        Task middle = new Task(20, false, true);
        Task far = new Task(40, false, true);
        queue.add(near); queue.add(middle); queue.add(far);
        assertSame(near, queue.poll(0, 0, 0));
        int reads = far.distanceReads;
        assertSame(middle, queue.poll(0, 0, 0));
        assertEquals(reads, far.distanceReads);
        Task other = new Task(-40, false, true);
        queue.add(other);
        assertSame(far, queue.poll(50, 0, 0));
        assertEquals(reads, far.distanceReads);
        assertNull(far.removal);
        queue.clear();
        assertNull(other.removal);
    }

    @Test
    void cancellationIsPromptAndCannotBeLostDuringRegistration() {
        IndexedChunkTaskQueue queue = new IndexedChunkTaskQueue();
        List<Task> tasks = new ArrayList<>();
        for (int index = 0; index < 4000; index++) {
            Task task = new Task(index, false, true);
            tasks.add(task);
            queue.add(task);
        }
        for (Task task : tasks) task.kernel$cancel();
        assertEquals(0, queue.size());
        for (Task task : tasks) assertNull(task.removal);
        Task racing = new Task(1, false, true);
        racing.beforeRegistration = racing::kernel$cancel;
        queue.add(racing);
        assertEquals(0, queue.size());
        assertNull(racing.removal);
    }

    @Test
    void requeueOwnsANewHandleAndCustomDuplicatesArePreserved() {
        IndexedChunkTaskQueue queue = new IndexedChunkTaskQueue();
        Task nativeTask = new Task(1, false, true);
        queue.add(nativeTask); queue.add(nativeTask);
        assertEquals(1, queue.size());
        var oldRemoval = nativeTask.removal;
        assertSame(nativeTask, queue.poll(0, 0, 0));
        queue.add(nativeTask);
        oldRemoval.remove();
        assertEquals(1, queue.size());
        assertSame(nativeTask, queue.poll(0, 0, 0));
        Task customTask = new Task(1, false, false);
        queue.add(customTask); queue.add(customTask); queue.add(customTask);
        assertEquals(3, queue.size());
        assertSame(customTask, queue.poll(0, 0, 0));
        assertNotNull(customTask.removal);
        customTask.kernel$cancel();
        assertEquals(0, queue.size());
        assertNull(customTask.removal);
    }

    @Test
    void clearCancelsInInsertionOrderAndFinishesAfterCallbackFailure() {
        IndexedChunkTaskQueue queue = new IndexedChunkTaskQueue();
        List<Integer> cancelled = new ArrayList<>();
        RuntimeException failure = new RuntimeException("cancel callback");
        List<Task> tasks = new ArrayList<>();
        for (int index = 10; index >= 0; index--) {
            Task task = new Task(index, (index & 1) == 0, true);
            int id = index;
            task.onCancel = () -> {
                cancelled.add(id);
                if (id == 8) throw failure;
            };
            tasks.add(task);
            queue.add(task);
        }
        assertSame(failure, assertThrows(RuntimeException.class, queue::clear));
        assertEquals(List.of(10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0), cancelled);
        assertEquals(0, queue.size());
        for (Task task : tasks) assertNull(task.removal);
    }

    @Test
    void nonFiniteCamerasAndMissedCancellationNotificationsRecover() {
        IndexedChunkTaskQueue queue = new IndexedChunkTaskQueue();
        List<Task> tasks = new ArrayList<>();
        for (int index = 0; index < 400; index++) {
            Task task = new Task(index, false, true);
            tasks.add(task); queue.add(task);
        }
        assertNull(queue.poll(Double.NaN, 0, 0));
        assertNull(queue.poll(Double.POSITIVE_INFINITY, 0, 0));
        for (int index = 0; index < tasks.size(); index += 2) tasks.get(index).cancelled = true;
        for (int index = 1; index < tasks.size(); index += 2) assertSame(tasks.get(index), queue.poll(0, 0, 0));
        assertEquals(0, queue.size());
        for (Task task : tasks) assertNull(task.removal);
    }

    @Test
    void producersCancellationAndPollingDoNotDuplicateOrLoseLiveTasks() throws Exception {
        IndexedChunkTaskQueue queue = new IndexedChunkTaskQueue();
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger producers = new AtomicInteger(3);
        Set<Task> live = ConcurrentHashMap.newKeySet();
        Set<Task> consumed = ConcurrentHashMap.newKeySet();
        try (var executor = Executors.newFixedThreadPool(4)) {
            var consumer = executor.submit(() -> {
                start.await();
                while (producers.get() != 0 || queue.size() != 0) {
                    KernelChunkTask task = queue.poll(0, 0, 0);
                    if (task != null) assertTrue(consumed.add((Task) task));
                    else Thread.yield();
                }
                return null;
            });
            List<java.util.concurrent.Future<?>> work = new ArrayList<>();
            for (int worker = 0; worker < 3; worker++) {
                work.add(executor.submit(() -> {
                    start.await();
                    try {
                        for (int index = 0; index < 2000; index++) {
                            Task task = new Task(index, (index & 1) == 0, true);
                            if (index % 3 == 0) task.kernel$cancel();
                            queue.add(task);
                            if (index % 3 == 1) task.kernel$cancel();
                            else if (index % 3 == 2) live.add(task);
                        }
                    } finally { producers.decrementAndGet(); }
                    return null;
                }));
            }
            start.countDown();
            for (var future : work) future.get(30, TimeUnit.SECONDS);
            consumer.get(30, TimeUnit.SECONDS);
        }
        assertTrue(consumed.containsAll(live));
        assertEquals(0, queue.size());
    }

    static final class Task implements KernelChunkTask {
        double x;
        final boolean recompile;
        final boolean nativeTask;
        volatile boolean cancelled;
        volatile IndexedChunkTaskQueue.Removal removal;
        int distanceReads;
        Runnable beforeRegistration;
        Runnable onCancel;

        Task(double x, boolean recompile, boolean nativeTask) {
            this.x = x; this.recompile = recompile; this.nativeTask = nativeTask;
        }
        @Override public boolean kernel$isCancelled() { return cancelled; }
        @Override public boolean kernel$isRecompile() { return recompile; }
        @Override public boolean kernel$isNativeTask() { return nativeTask; }
        @Override public int kernel$originX() { return (int) x; }
        @Override public int kernel$originY() { return 0; }
        @Override public int kernel$originZ() { return 0; }
        @Override public double kernel$distanceTo(double cameraX, double cameraY, double cameraZ) {
            distanceReads++;
            double dx = x + 0.5 - cameraX, dy = 0.5 - cameraY, dz = 0.5 - cameraZ;
            return dx * dx + dy * dy + dz * dz;
        }
        @Override public void kernel$cancel() {
            cancelled = true;
            kernel$notifyCancelled();
            if (onCancel != null) onCancel.run();
        }
        @Override public void kernel$setQueueRemoval(IndexedChunkTaskQueue.Removal removal) {
            if (beforeRegistration != null) { beforeRegistration.run(); beforeRegistration = null; }
            this.removal = removal;
        }
        @Override public void kernel$clearQueueRemoval(IndexedChunkTaskQueue.Removal removal) {
            if (this.removal == removal) this.removal = null;
        }
        @Override public void kernel$notifyCancelled() {
            var listener = removal;
            if (listener != null) listener.remove();
        }
    }

    private static final class Reference {
        final List<Task> tasks = new ArrayList<>();
        int quota = 2;

        Task poll(double camera) {
            tasks.removeIf(task -> task.cancelled);
            Task fresh = null, again = null;
            double freshDistance = Double.MAX_VALUE, againDistance = Double.MAX_VALUE;
            for (Task task : tasks) {
                double distance = task.kernel$distanceTo(camera, 0, 0);
                if (task.recompile && distance < againDistance) { againDistance = distance; again = task; }
                if (!task.recompile && distance < freshDistance) { freshDistance = distance; fresh = task; }
            }
            Task selected;
            if (again == null || fresh != null && (quota <= 0 || !(againDistance < freshDistance))) {
                quota = 2; selected = fresh;
            } else { quota--; selected = again; }
            if (selected != null) tasks.remove(selected);
            return selected;
        }
    }
}
