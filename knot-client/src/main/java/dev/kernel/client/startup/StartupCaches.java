package dev.kernel.client.startup;

import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.net.URL;

/** Bridge called only by the two recognized Fabric methods patched by the optional Java agent. */
public final class StartupCaches {
    private static final RawClassCache RAW = new RawClassCache(32 * 1024 * 1024, 4096);
    private static final TargetClassCache TARGETS = new TargetClassCache(16 * 1024 * 1024, 2048);
    private static boolean finished;

    private StartupCaches() {
    }

    public static byte[] readClass(URL url) throws IOException {
        return RAW.read(url);
    }

    public static ClassNode readTarget(byte[] bytes, int readerFlags) {
        return TARGETS.read(bytes, readerFlags);
    }

    public static synchronized void finish() {
        if (finished) return;
        finished = true;
        String raw = RAW.close();
        String targets = TARGETS.close();
        System.err.println("[Kernel] Startup caches released. Class files: " + raw + "; Mixin targets: " + targets);
    }
}
