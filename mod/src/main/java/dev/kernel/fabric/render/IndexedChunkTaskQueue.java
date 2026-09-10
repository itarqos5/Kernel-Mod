package dev.kernel.fabric.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;

/** Spatially indexed task selection, with vanilla's two-recompile quota and FIFO distance ties. */
public final class IndexedChunkTaskQueue {
    public interface Removal {
        void remove();
    }

    private final IdentityHashMap<KernelChunkTask, Entry> entries = new IdentityHashMap<>();
    private final SpatialTaskIndex<Entry> initial = new SpatialTaskIndex<>();
    private final SpatialTaskIndex<Entry> recompile = new SpatialTaskIndex<>();
    private final List<Entry> custom = new ArrayList<>();
    private long sequence;
    private int recompileQuota = 2;

    public synchronized void add(KernelChunkTask task) {
        boolean nativeTask = task.kernel$isNativeTask();
        if (task.kernel$isCancelled() || nativeTask && entries.containsKey(task)) return;
        SpatialTaskIndex<Entry> tree = nativeTask ? (task.kernel$isRecompile() ? recompile : initial) : null;
        Entry entry = new Entry(task, tree, sequence++);
        if (tree != null) entry.node = tree.add(entry, task.kernel$originX(), task.kernel$originY(), task.kernel$originZ(), entry.sequence);
        entry.nextForTask = entries.put(task, entry);
        if (tree == null) custom.add(entry);
        task.kernel$setQueueRemoval(entry);
        // Cancellation may have won the race just before the listener was installed.
        if (task.kernel$isCancelled()) removeEntry(entry);
    }

    public synchronized KernelChunkTask poll(double x, double y, double z) {
        Entry first = firstLive(initial, x, y, z);
        Entry again = firstLive(recompile, x, y, z);
        // Third-party subclasses can override getters or cancellation. Keep their priorities live.
        for (int index = 0; index < custom.size();) {
            Entry entry = custom.get(index);
            if (entry.task.kernel$isCancelled()) {
                removeEntry(entry);
            } else {
                entry.distance = entry.task.kernel$distanceTo(x, y, z);
                if (entry.task.kernel$isRecompile()) {
                    if (selectable(entry) && before(entry, again)) again = entry;
                } else if (selectable(entry) && before(entry, first)) {
                    first = entry;
                }
                index++;
            }
        }
        Entry selected;
        if (again == null || first != null && (recompileQuota == 0 || !(again.distance < first.distance))) {
            recompileQuota = 2;
            selected = first;
        } else {
            recompileQuota = Math.max(0, recompileQuota - 1);
            selected = again;
        }
        if (selected == null) return null;
        removeEntry(selected);
        return selected.task;
    }

    public synchronized int size() {
        return initial.size() + recompile.size() + custom.size();
    }

    public synchronized void clear() {
        List<Entry> pending = new ArrayList<>(size());
        for (Entry head : entries.values()) {
            for (Entry entry = head; entry != null; entry = entry.nextForTask) pending.add(entry);
        }
        pending.sort(Comparator.comparingLong(entry -> entry.sequence));
        // Detach every listener before cancellation callbacks can re-enter the queue.
        entries.clear();
        initial.clear();
        recompile.clear();
        custom.clear();
        for (Entry entry : pending) {
            entry.node = null;
            entry.nextForTask = null;
            entry.task.kernel$clearQueueRemoval(entry);
        }
        Throwable failure = null;
        for (Entry entry : pending) {
            try {
                entry.task.kernel$cancel();
            } catch (RuntimeException | Error exception) {
                if (failure == null) failure = exception;
                else if (failure != exception) failure.addSuppressed(exception);
            }
        }
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
    }

    private Entry firstLive(SpatialTaskIndex<Entry> tree, double x, double y, double z) {
        SpatialTaskIndex.Node<Entry> node;
        while ((node = tree.closest(x, y, z)) != null) {
            Entry entry = node.value;
            if (entry.task.kernel$isCancelled()) removeEntry(entry);
            else { entry.distance = node.distance; return entry; }
        }
        return null;
    }

    private static boolean selectable(Entry entry) {
        // Vanilla's search starts at MAX_VALUE and uses strict <, including for exceptional coordinates.
        return entry.distance < Double.MAX_VALUE;
    }

    private static boolean before(Entry first, Entry second) {
        if (second == null) return true;
        int comparison = first.distance == second.distance ? 0 : Double.compare(first.distance, second.distance);
        return comparison < 0 || comparison == 0 && first.sequence < second.sequence;
    }

    private void removeEntry(Entry entry) {
        Entry current = entries.get(entry.task);
        if (current == entry) {
            if (entry.nextForTask == null) entries.remove(entry.task);
            else entries.put(entry.task, entry.nextForTask);
            entry.task.kernel$clearQueueRemoval(entry);
            if (entry.nextForTask != null) entry.task.kernel$setQueueRemoval(entry.nextForTask);
        } else {
            while (current != null && current.nextForTask != entry) current = current.nextForTask;
            if (current == null) return;
            current.nextForTask = entry.nextForTask;
        }
        if (entry.tree != null) { entry.tree.remove(entry.node); entry.node = null; }
        else custom.remove(entry);
        entry.nextForTask = null;
    }

    private final class Entry implements Removal {
        final KernelChunkTask task;
        final SpatialTaskIndex<Entry> tree;
        final long sequence;
        SpatialTaskIndex.Node<Entry> node;
        double distance;
        Entry nextForTask;

        Entry(KernelChunkTask task, SpatialTaskIndex<Entry> tree, long sequence) {
            this.task = task;
            this.tree = tree;
            this.sequence = sequence;
        }

        @Override
        public void remove() {
            synchronized (IndexedChunkTaskQueue.this) {
                removeEntry(this);
                if (task.kernel$isCancelled()) {
                    Entry remaining;
                    while ((remaining = entries.get(task)) != null) removeEntry(remaining);
                }
            }
        }
    }

}
