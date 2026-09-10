package dev.kernel.client.startup;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reuses ASM constant-pool indexing and UTF-8 decoding for byte-identical Mixin target inputs.
 * Fabric's bytecode provider still runs on EVERY request; no transformer or access widener is skipped.
 * A fresh, independently parsed mutable ClassNode is returned for every request and reader-flags value.
 */
public final class TargetClassCache {
    private final Map<ByteKey, ClassReader> readers = new LinkedHashMap<>(16, 0.75F, true);
    private final int byteBudget;
    private final int entryLimit;
    private int retainedBytes;
    private long hits;
    private long misses;
    private boolean closed;

    public TargetClassCache(int byteBudget, int entryLimit) {
        if (byteBudget < 0 || entryLimit < 1) throw new IllegalArgumentException("Invalid target cache bounds");
        this.byteBudget = byteBudget;
        this.entryLimit = entryLimit;
    }

    public ClassNode read(byte[] bytes, int flags) {
        ClassReader reader = this.reader(bytes);
        ClassNode node = new ClassNode();
        // ClassReader lazily fills its UTF-8/constant-dynamic tables. Only identical inputs share this lock;
        // different targets can still be prepared concurrently. ClassNode objects are never cached.
        synchronized (reader) {
            reader.accept(node, flags);
        }
        return node;
    }

    private synchronized ClassReader reader(byte[] bytes) {
        // The charge reserves room for ClassReader's per-constant offset/reference tables as well as bytes.
        long charge = 4L * bytes.length;
        if (this.closed || charge > this.byteBudget) return new ClassReader(bytes);
        ByteKey lookup = new ByteKey(bytes);
        ClassReader cached = this.readers.get(lookup);
        if (cached != null) {
            this.hits++;
            return cached;
        }
        this.misses++;
        byte[] owned = bytes.clone();
        ClassReader reader = new ClassReader(owned);
        while (!this.readers.isEmpty()
            && (this.retainedBytes + charge > this.byteBudget || this.readers.size() >= this.entryLimit)) {
            var iterator = this.readers.keySet().iterator();
            ByteKey oldest = iterator.next();
            this.retainedBytes -= 4 * oldest.bytes.length;
            iterator.remove();
        }
        this.readers.put(new ByteKey(owned), reader);
        this.retainedBytes += (int) charge;
        return reader;
    }

    public synchronized String close() {
        this.closed = true;
        this.readers.clear();
        this.retainedBytes = 0;
        return this.hits + " hits, " + this.misses + " misses";
    }

    synchronized int retainedBytes() {
        return this.retainedBytes;
    }

    synchronized long hits() {
        return this.hits;
    }

    private static final class ByteKey {
        private final byte[] bytes;
        private final int hash;

        private ByteKey(byte[] bytes) {
            this.bytes = bytes;
            this.hash = Arrays.hashCode(bytes);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ByteKey key && Arrays.equals(this.bytes, key.bytes);
        }
    }
}
