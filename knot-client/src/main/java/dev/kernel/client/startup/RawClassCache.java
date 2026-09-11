package dev.kernel.client.startup;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Caches raw entries from already-open, immutable JARs during this launch only. Fabric still resolves the
 * resource URL and checks parent-loader access on every call. Directories, custom URLs, missing entries,
 * oversized entries and failures use uncached reads. No cache files or transformed classes are written.
 */
public final class RawClassCache {
    private static final int MAX_PREALLOCATION = 1024 * 1024;
    private final Map<EntryKey, byte[]> entries = new LinkedHashMap<>(16, 0.75F, true);
    private final int byteBudget;
    private final int entryLimit;
    private int retainedBytes;
    private long hits;
    private long misses;
    private boolean closed;

    public RawClassCache(int byteBudget, int entryLimit) {
        if (byteBudget < 0 || entryLimit < 1) throw new IllegalArgumentException("Invalid class cache bounds");
        this.byteBudget = byteBudget;
        this.entryLimit = entryLimit;
    }

    public byte[] read(URL url) throws IOException {
        if (!this.active()) return readBytes(url.openConnection());
        URLConnection connection = url.openConnection();
        if (!(connection instanceof JarURLConnection jarConnection)
            || !"file".equals(jarConnection.getJarFileURL().getProtocol()) || !connection.getUseCaches()) {
            return readBytes(connection);
        }

        JarEntry entry = jarConnection.getJarEntry();
        if (entry == null || !entry.getName().endsWith(".class") || entry.getSize() < 0
            || entry.getSize() > this.byteBudget || entry.getCrc() < 0) {
            return readBytes(connection);
        }
        // JarFile equality is object identity: the same path opened as a new archive cannot hit old entries.
        // The JDK owns this shared handle; closing it here would break other users of JarURLConnection.
        EntryKey key = new EntryKey(jarConnection.getJarFile(), entry.getName(), entry.getSize(), entry.getCrc());
        byte[] cached;
        synchronized (this) {
            cached = this.closed ? null : this.entries.get(key);
            if (cached != null) this.hits++;
            else this.misses++;
        }
        if (cached != null) return cached.clone();

        // Keep I/O outside the cache lock so independent Fabric loads remain parallel.
        byte[] bytes = readBytes(connection, (int) entry.getSize());
        synchronized (this) {
            if (!this.closed && bytes.length == entry.getSize() && !this.entries.containsKey(key)) {
                while (!this.entries.isEmpty()
                    && (this.retainedBytes + (long) bytes.length > this.byteBudget || this.entries.size() >= this.entryLimit)) {
                    var iterator = this.entries.values().iterator();
                    this.retainedBytes -= iterator.next().length;
                    iterator.remove();
                }
                this.entries.put(key, bytes.clone());
                this.retainedBytes += bytes.length;
            }
        }
        return bytes;
    }

    private synchronized boolean active() {
        return !this.closed;
    }

    private static byte[] readBytes(URLConnection connection) throws IOException {
        return readBytes(connection, -1);
    }

    /** A size hint avoids chunk buffers for small JAR entries; EOF, not metadata, determines the result. */
    static byte[] readBytes(URLConnection connection, int expectedSize) throws IOException {
        try (InputStream input = connection.getInputStream()) {
            if (expectedSize < 0 || expectedSize > MAX_PREALLOCATION) return input.readAllBytes();
            byte[] bytes = new byte[expectedSize];
            int length = input.readNBytes(bytes, 0, bytes.length);
            if (length < bytes.length) return Arrays.copyOf(bytes, length);
            int extra = input.read();
            if (extra < 0) return bytes;
            // A custom connection or malformed size must not truncate the raw class stream.
            var overflow = new ByteArrayOutputStream();
            overflow.write(bytes);
            overflow.write(extra);
            input.transferTo(overflow);
            return overflow.toByteArray();
        }
    }

    public synchronized String close() {
        this.closed = true;
        this.entries.clear();
        this.retainedBytes = 0;
        return this.hits + " hits, " + this.misses + " misses";
    }

    synchronized int retainedBytes() {
        return this.retainedBytes;
    }

    synchronized long hits() {
        return this.hits;
    }

    private record EntryKey(JarFile jar, String name, long size, long crc) {
    }
}
