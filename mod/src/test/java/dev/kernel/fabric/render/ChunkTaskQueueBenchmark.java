package dev.kernel.fabric.render;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Arrays;
import java.util.Random;

/** Isolated scheduling workload, including insertion and cancellation. No meshes or GPU work. */
public final class ChunkTaskQueueBenchmark {
    private static volatile long consumed;

    public static void main(String[] args) {
        System.out.println("queue tasks camera-period cancel-every linear-ms indexed-ms linear/indexed");
        for (int count : new int[] {1024, 8192}) {
            for (int cameraPeriod : new int[] {Integer.MAX_VALUE, 16, 1}) {
                for (int cancelEvery : new int[] {0, 2}) {
                    Task[] tasks = new Task[count];
                    Random random = new Random(7331);
                    for (int i = 0; i < count; i++) tasks[i] = new Task(i,
                        (random.nextInt(64) - 32) * 16, (random.nextInt(24) - 4) * 16,
                        (random.nextInt(64) - 32) * 16, random.nextBoolean());
                    for (int i = 0; i < 8; i++) {
                        run(tasks, cameraPeriod, cancelEvery, false);
                        run(tasks, cameraPeriod, cancelEvery, true);
                    }
                    long[] linear = new long[7], indexed = new long[7];
                    for (int i = 0; i < linear.length; i++) {
                        if ((i & 1) == 0) {
                            linear[i] = run(tasks, cameraPeriod, cancelEvery, false);
                            indexed[i] = run(tasks, cameraPeriod, cancelEvery, true);
                        } else {
                            indexed[i] = run(tasks, cameraPeriod, cancelEvery, true);
                            linear[i] = run(tasks, cameraPeriod, cancelEvery, false);
                        }
                    }
                    Arrays.sort(linear); Arrays.sort(indexed);
                    System.out.printf("queue %d %s %d %.3f %.3f %.2f%n", count,
                        cameraPeriod == Integer.MAX_VALUE ? "stationary" : cameraPeriod, cancelEvery,
                        linear[3] / 1e6, indexed[3] / 1e6, (double) linear[3] / indexed[3]);
                }
            }
        }
    }

    private static long run(Task[] tasks, int cameraPeriod, int cancelEvery, boolean indexed) {
        for (Task task : tasks) { task.cancelled = false; task.removal = null; }
        long start = System.nanoTime();
        var queue = indexed ? new IndexedChunkTaskQueue() : null;
        var baseline = indexed ? null : new Linear();
        for (Task task : tasks) {
            if (indexed) queue.add(task); else baseline.tasks.add(task);
        }
        if (cancelEvery != 0) for (int i = 0; i < tasks.length; i += cancelEvery) tasks[i].kernel$cancel();
        long checksum = 0;
        int poll = 0;
        while (true) {
            double camera = (poll++ / cameraPeriod) * 0.125;
            Task task = (Task) (indexed ? queue.poll(camera, 0, 0) : baseline.poll(camera));
            if (task == null) break;
            checksum = checksum * 31 + task.id;
        }
        consumed = checksum;
        return System.nanoTime() - start;
    }

    // Independent expression of vanilla's linear nearest-initial/nearest-recompile selection rules.
    private static final class Linear {
        final ObjectArrayList<Task> tasks = new ObjectArrayList<>();
        int quota = 2;

        Task poll(double camera) {
            int initial = -1, again = -1, index = 0;
            double initialDistance = Double.MAX_VALUE, againDistance = Double.MAX_VALUE;
            var iterator = tasks.iterator();
            while (iterator.hasNext()) {
                Task task = iterator.next();
                if (task.cancelled) { iterator.remove(); continue; }
                double distance = task.kernel$distanceTo(camera, 0, 0);
                if (task.recompile) {
                    if (distance < againDistance) { again = index; againDistance = distance; }
                } else if (distance < initialDistance) { initial = index; initialDistance = distance; }
                index++;
            }
            if (again != -1 && (initial == -1 || quota > 0 && againDistance < initialDistance)) {
                quota--; return tasks.remove(again);
            }
            quota = 2;
            return initial == -1 ? null : tasks.remove(initial);
        }
    }

    private static final class Task implements KernelChunkTask {
        final int id, x, y, z;
        final boolean recompile;
        volatile boolean cancelled;
        volatile IndexedChunkTaskQueue.Removal removal;
        Task(int id, int x, int y, int z, boolean recompile) {
            this.id = id; this.x = x; this.y = y; this.z = z; this.recompile = recompile;
        }
        @Override public boolean kernel$isCancelled() { return cancelled; }
        @Override public boolean kernel$isRecompile() { return recompile; }
        @Override public boolean kernel$isNativeTask() { return true; }
        @Override public int kernel$originX() { return x; }
        @Override public int kernel$originY() { return y; }
        @Override public int kernel$originZ() { return z; }
        @Override public double kernel$distanceTo(double x, double y, double z) {
            double dx = this.x + 0.5 - x, dy = this.y + 0.5 - y, dz = this.z + 0.5 - z;
            return dx * dx + dy * dy + dz * dz;
        }
        @Override public void kernel$cancel() { cancelled = true; kernel$notifyCancelled(); }
        @Override public void kernel$setQueueRemoval(IndexedChunkTaskQueue.Removal removal) { this.removal = removal; }
        @Override public void kernel$clearQueueRemoval(IndexedChunkTaskQueue.Removal removal) {
            if (this.removal == removal) this.removal = null;
        }
        @Override public void kernel$notifyCancelled() { var listener = removal; if (listener != null) listener.remove(); }
    }
}
