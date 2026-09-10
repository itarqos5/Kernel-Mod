package dev.kernel.fabric.render;

import com.sun.management.ThreadMXBean;
import net.minecraft.client.renderer.chunk.VisGraph;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Locale;
import java.util.Random;

/** Isolated CPU/allocation benchmark, not an FPS or frame-time benchmark. Run outside buildAll. */
public final class SectionVisibilityBenchmark {
    private static volatile Object sink;
    private static final ThreadMXBean ALLOCATIONS = (ThreadMXBean) ManagementFactory.getThreadMXBean();

    public static void main(String[] arguments) throws Exception {
        System.out.println("Section visibility microbenchmark; Java " + Runtime.version() + "; warm JVM, median of 7 samples.");
        System.out.println("scenario,vanilla_ns,Kernel_ns,vanilla_bytes,Kernel_bytes");
        for (String scenario : new String[]{"empty", "solid", "plane", "terrain", "checkerboard", "random-25", "random-50", "random-90"}) {
            BitSet initial = fixture(scenario);
            int emptyCount = 4096 - initial.cardinality();
            VisGraph vanilla = new VisGraph();
            var bitsField = VisGraph.class.getDeclaredField("bitSet");
            bitsField.setAccessible(true);
            BitSet vanillaBits = (BitSet) bitsField.get(vanilla);
            var emptyField = VisGraph.class.getDeclaredField("empty");
            emptyField.setAccessible(true);
            emptyField.setInt(vanilla, emptyCount);
            BitSet kernelBits = new BitSet(4096);
            Runnable reference = () -> {
                vanillaBits.clear();
                vanillaBits.or(initial);
                sink = vanilla.resolve();
            };
            Runnable kernel = () -> {
                kernelBits.clear();
                kernelBits.or(initial);
                sink = SectionVisibilityAdapter.resolve(kernelBits, emptyCount);
            };
            for (int warmup = 0; warmup < 1500; warmup++) {
                reference.run();
                kernel.run();
            }
            double[][] vanillaSamples = new double[7][];
            double[][] kernelSamples = new double[7][];
            for (int sample = 0; sample < 7; sample++) {
                if ((sample & 1) == 0) {
                    vanillaSamples[sample] = measure(reference);
                    kernelSamples[sample] = measure(kernel);
                } else {
                    kernelSamples[sample] = measure(kernel);
                    vanillaSamples[sample] = measure(reference);
                }
            }
            System.out.printf(Locale.ROOT, "%s,%.0f,%.0f,%.0f,%.0f%n", scenario,
                median(vanillaSamples, 0), median(kernelSamples, 0), median(vanillaSamples, 1), median(kernelSamples, 1));
        }
    }

    private static double[] measure(Runnable operation) {
        long thread = Thread.currentThread().threadId();
        long allocated = ALLOCATIONS.getThreadAllocatedBytes(thread);
        long started = System.nanoTime();
        for (int iteration = 0; iteration < 1000; iteration++) operation.run();
        long elapsed = System.nanoTime() - started;
        return new double[]{elapsed / 1000.0, (ALLOCATIONS.getThreadAllocatedBytes(thread) - allocated) / 1000.0};
    }

    private static double median(double[][] samples, int column) {
        double[] values = Arrays.stream(samples).mapToDouble(sample -> sample[column]).sorted().toArray();
        return values[values.length / 2];
    }

    private static BitSet fixture(String scenario) {
        BitSet result = new BitSet(4096);
        Random random = new Random(42);
        for (int cell = 0; cell < 4096; cell++) {
            int x = cell & 15;
            int z = (cell >>> 4) & 15;
            int y = cell >>> 8;
            boolean opaque = switch (scenario) {
                case "empty" -> false;
                case "solid" -> true;
                case "plane" -> x == 8;
                case "terrain" -> y < 6 + (x + z) % 5;
                case "checkerboard" -> ((x + y + z) & 1) == 0;
                case "random-25" -> random.nextDouble() < 0.25;
                case "random-50" -> random.nextDouble() < 0.5;
                case "random-90" -> random.nextDouble() < 0.9;
                default -> throw new IllegalArgumentException(scenario);
            };
            if (opaque) result.set(cell);
        }
        return result;
    }
}
