package dev.kernel.fabric.render;

/** Adapter implemented on Minecraft's task base; no game work runs through this interface. */
public interface KernelChunkTask {
    boolean kernel$isCancelled();
    boolean kernel$isRecompile();
    double kernel$distanceTo(double x, double y, double z);
    default int kernel$originX() { throw new UnsupportedOperationException("Custom task has no fixed origin"); }
    default int kernel$originY() { throw new UnsupportedOperationException("Custom task has no fixed origin"); }
    default int kernel$originZ() { throw new UnsupportedOperationException("Custom task has no fixed origin"); }
    void kernel$cancel();
    void kernel$setQueueRemoval(IndexedChunkTaskQueue.Removal removal);
    void kernel$clearQueueRemoval(IndexedChunkTaskQueue.Removal removal);
    void kernel$notifyCancelled();

    /** Custom task subclasses keep live distance evaluation on every poll. */
    default boolean kernel$isNativeTask() {
        return false;
    }
}
