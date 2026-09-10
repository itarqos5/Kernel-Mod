package dev.kernel.fabric.bootstrap;

import org.slf4j.LoggerFactory;

/** Optional bridge to the launcher class loader; ordinary Fabric launches need no agent classes. */
public final class StartupCacheControl {
    private StartupCacheControl() {
    }

    public static void finish() {
        try {
            Class<?> agent = Class.forName("dev.kernel.client.KernelAgent", false, ClassLoader.getSystemClassLoader());
            agent.getMethod("finishStartup").invoke(null);
        } catch (ClassNotFoundException ignored) {
            // Normal on the installation launch or with an unsupported launcher.
        } catch (ReflectiveOperationException | LinkageError exception) {
            LoggerFactory.getLogger("Kernel").warn("Could not release optional startup caches", exception);
        }
    }
}
