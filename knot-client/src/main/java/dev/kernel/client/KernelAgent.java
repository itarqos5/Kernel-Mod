package dev.kernel.client;

import dev.kernel.client.startup.StartupCacheTransformer;
import dev.kernel.client.startup.StartupCaches;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Optional, process-local startup hooks; never attaches to or changes another Java process. */
public final class KernelAgent {
    private static volatile boolean active;
    private KernelAgent() {
    }

    public static void premain(String options, Instrumentation instrumentation) {
        if (active || "false".equalsIgnoreCase(System.getProperty("kernel.startupCache"))) return;
        try {
            Class.forName("org.objectweb.asm.ClassReader", false, KernelAgent.class.getClassLoader());
            Class.forName("org.objectweb.asm.tree.ClassNode", false, KernelAgent.class.getClassLoader());
            instrumentation.addTransformer(new StartupCacheTransformer(), false);
            active = true;
            // Recovery if the mod is removed or loading fails before Minecraft's completion callback.
            CompletableFuture.delayedExecutor(3, TimeUnit.MINUTES).execute(KernelAgent::finishStartup);
            System.err.println("[Kernel] Startup caches armed; Fabric and Minecraft JARs remain unchanged.");
        } catch (LinkageError | RuntimeException | ClassNotFoundException exception) {
            System.err.println("[Kernel] Startup caches unavailable; continuing normally: " + exception);
        }
    }

    public static void finishStartup() {
        if (active) StartupCaches.finish();
    }
}
