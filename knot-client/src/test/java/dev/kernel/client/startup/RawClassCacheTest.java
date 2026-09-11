package dev.kernel.client.startup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URI;
import java.net.JarURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.JarFile;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

final class RawClassCacheTest {
    @TempDir Path directory;
    private final List<JarFile> testArchives = new ArrayList<>();

    @AfterEach
    void closeTestOwnedArchivesBeforeWindowsTempDirectoryCleanup() throws IOException {
        for (JarFile archive : this.testArchives) archive.close();
    }

    @Test
    void cachesArchiveEntriesWithoutExposingMutableStoredBytes() throws Exception {
        URL url = this.archive("one.jar", new byte[]{1, 2, 3});
        RawClassCache cache = new RawClassCache(1024, 4);
        byte[] first = cache.read(url);
        first[0] = 100;
        byte[] second = cache.read(url);
        second[1] = 100;
        assertArrayEquals(new byte[]{1, 2, 3}, cache.read(url));
        assertEquals(2, cache.hits());
        assertEquals(3, cache.retainedBytes());
    }

    @Test
    void sameEntryNameInDifferentArchivesNeverAliases() throws Exception {
        RawClassCache cache = new RawClassCache(1024, 4);
        URL first = this.archive("one.jar", new byte[]{1, 2});
        URL second = this.archive("two.jar", new byte[]{3, 4});
        assertArrayEquals(new byte[]{1, 2}, cache.read(first));
        assertArrayEquals(new byte[]{3, 4}, cache.read(second));
        assertArrayEquals(new byte[]{1, 2}, cache.read(first));
        assertEquals(1, cache.hits());
    }

    @Test
    void mutableFilesAndMissingResourcesAreNeverCached() throws Exception {
        RawClassCache cache = new RawClassCache(1024, 4);
        Path path = this.directory.resolve("Target.class");
        URL url = path.toUri().toURL();
        assertThrows(IOException.class, () -> cache.read(url));
        Files.write(path, new byte[]{1});
        assertArrayEquals(new byte[]{1}, cache.read(url));
        Files.write(path, new byte[]{2});
        assertArrayEquals(new byte[]{2}, cache.read(url));
        assertEquals(0, cache.retainedBytes());
        assertEquals(0, cache.hits());
    }

    @Test
    void respectsByteAndEntryBoundsAndShutdownDuringConcurrentReads() throws Exception {
        URL first = this.archive("one.jar", new byte[]{1, 2, 3});
        URL second = this.archive("two.jar", new byte[]{4, 5, 6});
        RawClassCache cache = new RawClassCache(3, 1);
        cache.read(first);
        cache.read(second);
        cache.read(first);
        assertEquals(0, cache.hits());
        assertEquals(3, cache.retainedBytes());
        try (var executor = Executors.newFixedThreadPool(4)) {
            var jobs = IntStream.range(0, 40).<Callable<Void>>mapToObj(i -> () -> {
                if (i == 20) cache.close();
                assertArrayEquals(new byte[]{1, 2, 3}, cache.read(first));
                return null;
            }).toList();
            for (var result : executor.invokeAll(jobs)) result.get();
        }
        assertEquals(0, cache.retainedBytes());
        cache.read(second);
        assertEquals(0, cache.retainedBytes());

        RawClassCache tooSmall = new RawClassCache(2, 1);
        assertArrayEquals(new byte[]{1, 2, 3}, tooSmall.read(first));
        assertEquals(0, tooSmall.retainedBytes());
    }

    @Test
    void sizeHintsNeverTruncateStreamsAndPartialReadsStillClose() throws Exception {
        for (int size : new int[]{0, 1, 7, 8191, 8192, 8193, 65536}) {
            byte[] bytes = new byte[size]; new java.util.Random(size).nextBytes(bytes);
            for (int hint : new int[]{-1, 0, 1, 7, 8192, 65536, 1048576, 1048577, Integer.MAX_VALUE}) {
                boolean[] closed = {false};
                var input = new java.io.FilterInputStream(new java.io.ByteArrayInputStream(bytes)) {
                    @Override public int read(byte[] out, int offset, int length) throws IOException {
                        return super.read(out, offset, Math.min(length, 3));
                    }
                    @Override public void close() throws IOException { closed[0] = true; super.close(); }
                };
                assertArrayEquals(bytes, RawClassCache.readBytes(connection(input), hint), "Size/hint " + size + "/" + hint);
                assertTrue(closed[0]);
            }
        }
    }

    @Test
    void errorsBeyondTheDeclaredLengthStillPropagateAndClose() throws Exception {
        for (int hint : new int[]{-1, 0, 7, 16, 8192}) {
            boolean[] closed = {false};
            IOException failure = new IOException("I/O failure after declared bytes");
            var input = new java.io.InputStream() {
                int position;
                @Override public int read() throws IOException { if (position++ >= 16) throw failure; return 23; }
                @Override public void close() { closed[0] = true; }
            };
            assertSame(failure, assertThrows(IOException.class, () -> RawClassCache.readBytes(connection(input), hint)));
            assertTrue(closed[0]);
        }
    }

    @Test
    void realJarReadsPreserveBytesAcrossBufferAndPreallocationBoundaries() throws Exception {
        RawClassCache cache = new RawClassCache(2 * 1024 * 1024, 16);
        int[] sizes = {0, 7, 8191, 8192, 8193, 1048576, 1048577};
        for (int size : sizes) {
            byte[] bytes = new byte[size]; new java.util.Random(size).nextBytes(bytes);
            URL url = archive("entry-" + size + ".jar", bytes);
            byte[] cold = cache.read(url);
            assertArrayEquals(bytes, cold);
            if (cold.length > 0) cold[0] ^= 27;
            byte[] warm = cache.read(url);
            assertArrayEquals(bytes, warm);
            assertNotSame(cold, warm);
        }
        assertEquals(sizes.length, cache.hits());
    }

    private static java.net.URLConnection connection(java.io.InputStream input) throws Exception {
        return new java.net.URLConnection(URI.create("file:/kernel-test-stream").toURL()) {
            @Override public void connect() {}
            @Override public java.io.InputStream getInputStream() { return input; }
        };
    }

    private URL archive(String name, byte[] bytes) throws Exception {
        Path path = this.directory.resolve(name);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new JarEntry("Target.class"));
            output.write(bytes);
            output.closeEntry();
        }
        URL url = URI.create("jar:" + path.toUri() + "!/Target.class").toURL();
        this.testArchives.add(((JarURLConnection) url.openConnection()).getJarFile());
        return url;
    }
}
