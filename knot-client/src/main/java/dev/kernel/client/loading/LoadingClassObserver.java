package dev.kernel.client.loading;

import java.lang.instrument.ClassFileTransformer;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;

/** Observes definitions without modifying them, including launches where optional ASM hooks are unavailable. */
public final class LoadingClassObserver implements ClassFileTransformer {
    private static final byte[] ADAPTER = "dev/kernel/fabric/bootstrap/StartupWindowBridge".getBytes(StandardCharsets.US_ASCII);
    @Override public byte[] transform(ClassLoader loader, String name, Class<?> redefined, ProtectionDomain domain, byte[] bytes) {
        if ("net/fabricmc/loader/impl/launch/knot/KnotClient".equals(name)
            || "net/fabricmc/loader/launch/knot/KnotClient".equals(name)) StartupProgress.fabricClassLoaded();
        StartupProgress.classLoaded(name);
        if ("com/mojang/blaze3d/platform/Window".equals(name) || "net/minecraft/class_1041".equals(name)) {
            try { EarlyLoadingWindow.windowClass(contains(bytes, ADAPTER)); }
            catch (LinkageError ignored) { /* No LWJGL in a headless process. */ }
        }
        return null;
    }
    private static boolean contains(byte[] bytes, byte[] part) {
        outer: for (int i = 0; i <= bytes.length - part.length; i++) {
            for (int j = 0; j < part.length; j++) if (bytes[i + j] != part[j]) continue outer;
            return true;
        }
        return false;
    }
}
