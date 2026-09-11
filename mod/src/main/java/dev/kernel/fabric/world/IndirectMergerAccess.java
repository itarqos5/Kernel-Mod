package dev.kernel.fabric.world;

/** Read-only first mapping of a native indirect merger, used only after verifying a nonempty axis. */
public interface IndirectMergerAccess {
    /** Low 32 bits contain the first input index; high 32 bits contain the second input index. */
    long kernel$firstIndexPair();
}
