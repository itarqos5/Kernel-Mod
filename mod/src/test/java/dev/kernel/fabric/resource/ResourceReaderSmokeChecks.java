package dev.kernel.fabric.resource;

import com.sun.management.ThreadMXBean;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.server.packs.resources.Resource;

public final class ResourceReaderSmokeChecks {
    public static final java.util.concurrent.atomic.AtomicInteger ownedReaders = new java.util.concurrent.atomic.AtomicInteger();
    private static volatile Object sink;
    public static void run() throws Exception {
        boolean enabled = Boolean.getBoolean("kernel.resourceProbe.expectedEnabled");
        check(enabled == ResourceSettings.compactReadersActive(), "Configuration ownership");
        var random = new Random(0x342912aL);
        List<byte[]> samples = new ArrayList<>();
        for (int length : new int[]{0, 1, 2, 17, 1023, 1024, 2047, 2048, 2049, 4096, 8191, 8192, 8193, 32769}) {
            byte[] bytes = new byte[length]; random.nextBytes(bytes); samples.add(bytes);
            samples.add(("one Ω🌍\r\ntwo\nthree\rlast\u0000".repeat(length / 20 + 1)).getBytes(StandardCharsets.UTF_8));
        }
        samples.add(("Ω🌍 abc\\\"".repeat(20000) + "\nend\r\n").getBytes(StandardCharsets.UTF_8));
        for (byte[] sample : samples) verify(sample);
        lifecycle();
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            var results = new ArrayList<Future<?>>();
            for (int thread = 0; thread < 4; thread++) results.add(workers.submit(() -> {
                try { for (int i = 0; i < 10; i++) verify(samples.get(21 + i % 8)); }
                catch (Exception exception) { throw new CompletionException(exception); }
            }));
            for (var result : results) result.get();
        } finally { workers.shutdownNow(); }
        boolean competing = Boolean.getBoolean("kernel.resourceProbe.competing");
        check(competing == (ownedReaders.get() > 0), "Competing factory ownership");
        allocation(enabled && !competing);
        System.out.println("Kernel resource readers: enabled=" + enabled + ", competing=" + competing + "; native decoding, bulk/line/mark/skip/close semantics, failures, custom ownership and concurrency passed");
        if (Boolean.getBoolean("kernel.resourceReaderBenchmark")) ResourceReaderBenchmark.run();
    }
    private static Resource resource(byte[] bytes, int chunk) {
        return new Resource(null, () -> new ShortStream(bytes, chunk));
    }
    static BufferedReader original(Resource resource) throws IOException {
        return new BufferedReader(new InputStreamReader(resource.open(), StandardCharsets.UTF_8));
    }
    private static void verify(byte[] bytes) throws Exception {
        String expected;
        try (var reader = original(resource(bytes, Integer.MAX_VALUE))) { expected = readAll(reader, 8192); }
        for (int chunk : new int[]{1, 17, Integer.MAX_VALUE}) {
            for (int size : new int[]{1, 127, 1024, 2048, 8192, 16384}) {
                // Byte-limited streams need only exercise boundaries, not every character one-by-one.
                if (bytes.length > 50000 && (size == 1 || chunk == 1)) continue;
                try (var reader = resource(bytes, chunk).openAsReader()) {
                    check(reader.getClass() == BufferedReader.class, "Reader subclass changed");
                    check(expected.equals(readAll(reader, size)), "UTF-8/bulk content mismatch");
                    check(reader.read() == -1 && reader.read(new char[1]) == -1, "Repeated EOF");
                    check(reader.read(new char[0]) == 0, "Empty read at EOF");
                }
            }
        }
        try (var reader = resource(bytes, Integer.MAX_VALUE).openAsReader(); var reference = new BufferedReader(new StringReader(expected))) {
            String line;
            while ((line = reference.readLine()) != null) check(line.equals(reader.readLine()), "Line content mismatch");
            check(reader.readLine() == null, "Extra line");
        }
        try (var reader = resource(bytes, Integer.MAX_VALUE).openAsReader()) {
            check(reader.markSupported(), "Marks unavailable");
            reader.mark(expected.length() + 20);
            int read = Math.min(expected.length(), 10000);
            check(expected.substring(0, read).equals(readCount(reader, read)), "Marked read mismatch");
            reader.reset();
            check(expected.equals(readAll(reader, 4093)), "Large read-ahead reset mismatch");
        }
        try (var reader = resource(bytes, Integer.MAX_VALUE).openAsReader()) {
            int skip = expected.length() / 2;
            check(reader.skip(skip) == skip, "Skip length");
            CharBuffer buffer = CharBuffer.allocate(263);
            var actual = new StringBuilder();
            while (true) {
                buffer.clear().position(3).limit(259);
                int count = reader.read(buffer);
                if (count == -1) break;
                check(count > 0 && buffer.position() == 3 + count, "CharBuffer position/progress");
                actual.append(buffer.array(), 3, count);
            }
            check(expected.substring(skip).contentEquals(actual), "Skip/CharBuffer content");
        }
        try (var reader = resource(bytes, 17).openAsReader()) {
            var writer = new StringWriter();
            check(reader.transferTo(writer) == expected.length(), "Transfer length");
            check(expected.equals(writer.toString()), "Transfer content");
        }
        try (var reader = resource(bytes, 17).openAsReader(); var reference = new BufferedReader(new StringReader(expected))) {
            check(reader.lines().toList().equals(reference.lines().toList()), "Line stream content");
        }
    }
    private static String readCount(Reader reader, int length) throws IOException {
        char[] chars = new char[length]; int total = 0;
        while (total < length) { int count = reader.read(chars, total, length - total); check(count > 0, "Early EOF"); total += count; }
        return new String(chars);
    }
    private static String readAll(Reader reader, int size) throws IOException {
        StringBuilder result = new StringBuilder(); char[] buffer = new char[size]; int count;
        while ((count = reader.read(buffer)) != -1) { check(count > 0, "Read made no progress"); result.append(buffer, 0, count); }
        return result.toString();
    }
    private static void lifecycle() throws Exception {
        byte[] bytes = ("A\r\nΩ🌍Z\n".repeat(2000)).getBytes(StandardCharsets.UTF_8);
        var stream = new ShortStream(bytes, 7);
        int[] opens = {0};
        Resource base = new Resource(null, () -> { opens[0]++; return stream; });
        var reader = base.openAsReader();
        check(opens[0] == 1 && stream.reads == 0, "Factory read content eagerly or reopened stream");
        check(!reader.ready(), "Unread unavailable stream reported ready");
        check(reader.read() == 'A' && reader.ready(), "Buffered ready contract");
        reader.close(); reader.close();
        check(stream.closes == 1, "Close ownership");
        expect(IOException.class, reader::read);
        expect(IOException.class, reader::ready);
        expect(IOException.class, () -> reader.mark(1));
        expect(IOException.class, reader::reset);
        expect(IOException.class, () -> reader.skip(1));
        try (var valid = resource(bytes, 17).openAsReader()) {
            expect(IllegalArgumentException.class, () -> valid.mark(-1));
            expect(IllegalArgumentException.class, () -> valid.skip(-1));
            expect(IndexOutOfBoundsException.class, () -> valid.read(new char[4], 2, 3));
            expect(NullPointerException.class, () -> valid.read((char[]) null, 0, 1));
        }
        IOException failure = new IOException("test open failure");
        try { new Resource(null, () -> { throw failure; }).openAsReader(); throw new AssertionError("Open failure swallowed"); }
        catch (IOException actual) { check(actual == failure, "Open failure replaced"); }
        var failed = new InputStream() { @Override public int read() throws IOException { throw failure; } };
        try (var failing = new Resource(null, () -> failed).openAsReader()) {
            try { failing.read(); throw new AssertionError("Read failure swallowed"); }
            catch (IOException actual) { check(actual == failure, "Read failure replaced"); }
        }
        var failedClose = new InputStream() {
            @Override public int read() { return -1; }
            @Override public void close() throws IOException { throw failure; }
        };
        var closing = new Resource(null, () -> failedClose).openAsReader();
        try { closing.close(); throw new AssertionError("Close failure swallowed"); }
        catch (IOException actual) { check(actual == failure, "Close failure replaced"); }
        closing.close();
        expect(IOException.class, closing::read);
        BufferedReader custom = new BufferedReader(new StringReader("custom"));
        Resource overriddenReader = new Resource(null, () -> { throw new AssertionError("Custom reader opened stream"); }) {
            @Override public BufferedReader openAsReader() { return custom; }
        };
        check(overriddenReader.openAsReader() == custom, "Custom reader ownership"); custom.close();
        Resource overriddenStream = new Resource(null, () -> { throw new AssertionError("Custom stream ignored"); }) {
            @Override public InputStream open() { return new ByteArrayInputStream(new byte[]{'X'}); }
        };
        try (var opened = overriddenStream.openAsReader()) { check(opened.read() == 'X', "Custom stream ownership"); }
    }
    private static void allocation(boolean enabled) throws Exception {
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        Resource empty = new Resource(null, () -> new ByteArrayInputStream(new byte[0]));
        for (int i = 0; i < 5000; i++) { sink = original(empty); sink = empty.openAsReader(); }
        long thread = Thread.currentThread().threadId();
        long before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 1024; i++) sink = original(empty);
        long nativeBytes = bean.getThreadAllocatedBytes(thread) - before;
        before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 1024; i++) sink = empty.openAsReader();
        long liveBytes = bean.getThreadAllocatedBytes(thread) - before;
        check(nativeBytes - liveBytes == (enabled ? 12288L * 1024 : 0), "Unexpected native/live reader allocation delta: " + nativeBytes + "/" + liveBytes);
        System.out.println("Kernel resource reader allocation: native=" + nativeBytes / 1024 + ", live=" + liveBytes / 1024 + " bytes/open");
    }
    private static final class ShortStream extends ByteArrayInputStream {
        private final int chunk; int reads, closes;
        ShortStream(byte[] bytes, int chunk) { super(bytes); this.chunk = chunk; }
        @Override public synchronized int read(byte[] buffer, int offset, int length) { reads++; return super.read(buffer, offset, Math.min(length, chunk)); }
        @Override public synchronized int available() { return 0; }
        @Override public void close() { closes++; }
    }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private static void expect(Class<? extends Throwable> type, Checked call) throws Exception {
        try { call.run(); } catch (Throwable failure) { if (type.isInstance(failure)) return; throw failure; }
        throw new AssertionError("Missing expected " + type.getSimpleName());
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
