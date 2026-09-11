package dev.kernel.client.startup;

import com.sun.management.ThreadMXBean;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.Arrays;
import java.util.Locale;

/** Measures warm repeated reads/parses of real dependency classes; does not measure total launch time. */
public final class StartupCacheBenchmark {
    private static volatile Object sink;
    private static final ThreadMXBean ALLOCATIONS = (ThreadMXBean) ManagementFactory.getThreadMXBean();

    public static void main(String[] arguments) throws Exception {
        URL[] urls = new URL[]{
            resource("org/objectweb/asm/ClassReader.class"),
            resource("net/fabricmc/loader/impl/launch/knot/KnotClassDelegate.class"),
            resource("org/spongepowered/asm/mixin/transformer/MixinInfo.class"),
            resource("org/spongepowered/asm/mixin/transformer/ClassInfo.class")
        };
        byte[][] bytes = new byte[urls.length][];
        for (int i = 0; i < urls.length; i++) {
            try (var input = urls[i].openStream()) { bytes[i] = input.readAllBytes(); }
        }
        TargetClassCache targets = new TargetClassCache(16 * 1024 * 1024, 2048);
        RawClassCache raw = new RawClassCache(32 * 1024 * 1024, 4096);
        System.out.println("Startup cache microbenchmark; Java " + Runtime.version() + "; repeated reads of 4 immutable classes.");
        System.out.println("operation,uncached_ns,cached_ns,uncached_bytes,cached_bytes");
        compare("target-parse", index -> {
            ClassNode node = new ClassNode();
            new ClassReader(bytes[index & 3]).accept(node, 0);
            sink = node;
        }, index -> sink = targets.read(bytes[index & 3], 0));
        compare("jar-class-read", index -> {
            try (var input = urls[index & 3].openStream()) { sink = input.readAllBytes(); }
        }, index -> sink = raw.read(urls[index & 3]));
        int[] sizes = Arrays.stream(bytes).mapToInt(value -> value.length).toArray();
        compare("jar-entry-miss-read", index -> {
            try (var input = urls[index & 3].openStream()) { sink = input.readAllBytes(); }
        }, index -> sink = RawClassCache.readBytes(urls[index & 3].openConnection(), sizes[index & 3]));
        targets.close();
        raw.close();
    }

    private static void compare(String name, Operation reference, Operation optimized) throws Exception {
        for (int warmup = 0; warmup < 3000; warmup++) {
            reference.run(warmup);
            optimized.run(warmup);
        }
        double[][] baseline = new double[7][];
        double[][] kernel = new double[7][];
        for (int sample = 0; sample < 7; sample++) {
            if ((sample & 1) == 0) {
                baseline[sample] = measure(reference);
                kernel[sample] = measure(optimized);
            } else {
                kernel[sample] = measure(optimized);
                baseline[sample] = measure(reference);
            }
        }
        System.out.printf(Locale.ROOT, "%s,%.0f,%.0f,%.0f,%.0f%n", name,
            median(baseline, 0), median(kernel, 0), median(baseline, 1), median(kernel, 1));
    }

    private static double[] measure(Operation operation) throws Exception {
        long thread = Thread.currentThread().threadId();
        long allocated = ALLOCATIONS.getThreadAllocatedBytes(thread);
        long start = System.nanoTime();
        for (int iteration = 0; iteration < 1000; iteration++) operation.run(iteration);
        long elapsed = System.nanoTime() - start;
        return new double[]{elapsed / 1000.0, (ALLOCATIONS.getThreadAllocatedBytes(thread) - allocated) / 1000.0};
    }

    private static double median(double[][] samples, int column) {
        double[] values = Arrays.stream(samples).mapToDouble(sample -> sample[column]).sorted().toArray();
        return values[values.length / 2];
    }

    private static URL resource(String name) {
        URL url = StartupCacheBenchmark.class.getClassLoader().getResource(name);
        if (url == null) throw new IllegalStateException("Missing benchmark resource " + name);
        return url;
    }

    private interface Operation {
        void run(int index) throws Exception;
    }
}
