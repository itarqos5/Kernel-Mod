package dev.kernel.fabric.render;

import com.mojang.blaze3d.vertex.VertexSorting;
import com.sun.management.ThreadMXBean;
import org.joml.Vector3f;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import java.util.stream.IntStream;

/** Isolated sort/adapter benchmark against unmodified Minecraft classes, not a gameplay benchmark. */
public final class VertexSortingBenchmark {
    private static volatile int[] sink;
    private static final ThreadMXBean ALLOCATIONS = (ThreadMXBean) ManagementFactory.getThreadMXBean();

    public static void main(String[] arguments) {
        System.out.println("Quad sorting microbenchmark; Java " + Runtime.version() + "; warm JVM, median of 7 samples.");
        System.out.println("scenario,quads,vanilla_ns,Kernel_ns,vanilla_bytes,Kernel_bytes");
        for (String scenario : new String[]{"random:0", "random:16", "random:32", "random:33", "random:128", "random:256", "random:257", "random:512", "random:1024", "random:4096",
            "random:16384", "ordered:4096", "reversed:4096", "ties:4096", "random:32768"}) {
            String[] parts = scenario.split(":");
            int size = Integer.parseInt(parts[1]);
            var inputs = IntStream.range(0, 8).mapToObj(batch -> KernelVertexSortingTest.input(fixture(parts[0], size, batch))).toList();
            Vector3f camera = new Vector3f();
            VertexSorting vanilla = VertexSorting.byDistance(camera);
            VertexSorting kernel = new KernelVertexSorting(camera::distanceSquared, vanilla);
            // Check every timed input before measuring, without including assertion cost in the samples.
            for (var input : inputs) {
                if (!Arrays.equals(vanilla.sort(input), kernel.sort(input))) throw new AssertionError("Sort mismatch: " + scenario);
            }
            int[] vanillaCursor = {0};
            int[] kernelCursor = {0};
            Runnable reference = () -> sink = vanilla.sort(inputs.get(vanillaCursor[0]++ & 7));
            Runnable optimized = () -> sink = kernel.sort(inputs.get(kernelCursor[0]++ & 7));
            int repetitions = size <= 512 ? 20_000 : 512;
            int warmup = size <= 512 ? 20_000 : 2048;
            for (int iteration = 0; iteration < warmup; iteration++) {
                reference.run();
                optimized.run();
            }
            double[][] vanillaSamples = new double[7][];
            double[][] kernelSamples = new double[7][];
            for (int sample = 0; sample < 7; sample++) {
                if ((sample & 1) == 0) {
                    vanillaSamples[sample] = measure(reference, repetitions);
                    kernelSamples[sample] = measure(optimized, repetitions);
                } else {
                    kernelSamples[sample] = measure(optimized, repetitions);
                    vanillaSamples[sample] = measure(reference, repetitions);
                }
            }
            System.out.printf(Locale.ROOT, "%s,%d,%.0f,%.0f,%.0f,%.0f%n", parts[0], size,
                median(vanillaSamples, 0), median(kernelSamples, 0), median(vanillaSamples, 1), median(kernelSamples, 1));
        }
    }

    private static double[] measure(Runnable operation, int repetitions) {
        long thread = Thread.currentThread().threadId();
        long allocated = ALLOCATIONS.getThreadAllocatedBytes(thread);
        long started = System.nanoTime();
        for (int iteration = 0; iteration < repetitions; iteration++) operation.run();
        long elapsed = System.nanoTime() - started;
        return new double[]{(double) elapsed / repetitions, (double) (ALLOCATIONS.getThreadAllocatedBytes(thread) - allocated) / repetitions};
    }

    private static double median(double[][] samples, int column) {
        double[] values = Arrays.stream(samples).mapToDouble(sample -> sample[column]).sorted().toArray();
        return values[values.length / 2];
    }

    private static Vector3f[] fixture(String scenario, int size, int batch) {
        Random random = new Random(816L + batch);
        Vector3f[] points = new Vector3f[size];
        for (int index = 0; index < size; index++) {
            points[index] = switch (scenario) {
                case "random" -> new Vector3f(random.nextFloat() * 16, random.nextFloat() * 16, random.nextFloat() * 16);
                case "ordered" -> new Vector3f(size - index, 0, 0);
                case "reversed" -> new Vector3f(index, 0, 0);
                case "ties" -> new Vector3f(random.nextInt(4), random.nextInt(4), random.nextInt(4));
                default -> throw new IllegalArgumentException(scenario);
            };
        }
        return points;
    }
}
