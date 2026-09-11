package dev.kernel.fabric.resource;

import com.google.gson.JsonParser;
import com.sun.management.ThreadMXBean;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;
import net.minecraft.server.packs.resources.Resource;

/** Optional warmed, memory-backed native reader comparison; no game assets are bundled. */
public final class ResourceReaderBenchmark {
    private static final ThreadMXBean ALLOCATION = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static volatile Object sink;
    private record Case(String label, List<Resource> files, boolean json) {}
    public static void run() throws Exception {
        List<Case> cases = new ArrayList<>();
        Path source = Path.of(Resource.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (ZipFile jar = new ZipFile(source.toFile())) {
            for (String prefix : List.of("assets/minecraft/models/", "assets/minecraft/lang/", "assets/minecraft/shaders/")) {
                var entries = jar.stream().filter(e -> !e.isDirectory() && e.getName().startsWith(prefix)
                    && (e.getName().endsWith(".json") || e.getName().endsWith(".vsh") || e.getName().endsWith(".fsh") || e.getName().endsWith(".glsl")))
                    .sorted(Comparator.comparingLong(e -> e.getSize())).toList();
                if (entries.isEmpty()) continue;
                var files = new ArrayList<Resource>();
                for (int i = 0; i < Math.min(192, entries.size()); i++) {
                    int index = entries.size() <= 192 ? i : (int)((long)i * (entries.size() - 1) / 191);
                    try (var in = jar.getInputStream(entries.get(index))) { files.add(resource(in.readAllBytes())); }
                }
                cases.add(new Case(prefix, files, !prefix.contains("shaders")));
            }
        }
        cases.add(new Case("large-json", List.of(resource(("{\"items\":[" +
            "\"a value with Unicode Ω🌍 and escapes \\\" xyz\",".repeat(25000) + "null]}").getBytes(StandardCharsets.UTF_8))), true));
        cases.add(new Case("long-lines", List.of(resource(("large line Ω".repeat(8192) + "\r\n" + "short\n".repeat(2048)).getBytes(StandardCharsets.UTF_8))), false));
        for (Case test : cases) {
            for (String mode : test.json ? List.of("json", "bulk-1024", "bulk-8192") : List.of("lines", "bulk-1024", "bulk-8192")) {
                int repeats = test.files.size() == 1 ? 3 : 1;
                long[][] time = new long[2][11], bytes = new long[2][11];
                for (int round = -8; round < 11; round++) for (int offset = 0; offset < 2; offset++) {
                    int index = Math.floorMod(round + offset, 2);
                    long allocated = ALLOCATION.getThreadAllocatedBytes(Thread.currentThread().threadId()), start = System.nanoTime();
                    for (int repeat = 0; repeat < repeats; repeat++) for (Resource file : test.files) consume(file, index == 1, mode);
                    long elapsed = System.nanoTime() - start;
                    long used = ALLOCATION.getThreadAllocatedBytes(Thread.currentThread().threadId()) - allocated;
                    if (round >= 0) { time[index][round] = elapsed; bytes[index][round] = used; }
                }
                for (int i = 0; i < 2; i++) {
                    Arrays.sort(time[i]); Arrays.sort(bytes[i]);
                    System.out.printf(Locale.ROOT, "Kernel reader benchmark: %s %s %s enabled=%s files=%d us/file=%.3f B/file=%.1f%n",
                        test.label, mode, i == 0 ? "reference" : "live", ResourceSettings.compactReadersActive(), test.files.size(),
                        time[i][5]/1000.0/(repeats*test.files.size()), (double)bytes[i][5]/(repeats*test.files.size()));
                }
            }
        }
    }
    private static Resource resource(byte[] bytes) { return new Resource(null, () -> new ByteArrayInputStream(bytes)); }
    private static void consume(Resource resource, boolean live, String mode) throws Exception {
        try (BufferedReader reader = live ? resource.openAsReader() : ResourceReaderSmokeChecks.original(resource)) {
            if (mode.equals("json")) { sink = JsonParser.parseReader(reader); return; }
            long result = 0;
            if (mode.equals("lines")) {
                String line; while ((line = reader.readLine()) != null) result += line.hashCode();
            } else {
                char[] buffer = new char[mode.equals("bulk-1024") ? 1024 : 8192];
                int count; while ((count = reader.read(buffer)) != -1) result += count;
            }
            sink = result;
        }
    }
}
