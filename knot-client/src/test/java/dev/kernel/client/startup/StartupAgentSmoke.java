package dev.kernel.client.startup;

import dev.kernel.client.KernelAgent;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.launch.knot.MixinServiceKnot;
import org.objectweb.asm.ClassReader;

import java.util.Arrays;
import java.util.List;

/** Runs in a fresh JVM with the packaged -javaagent, using Fabric's real signed loader classes. */
public final class StartupAgentSmoke {
    public static void main(String[] arguments) throws Exception {
        Class<?> loaderType = Class.forName("net.fabricmc.loader.impl.launch.knot.KnotClassLoader");
        var constructor = loaderType.getDeclaredConstructor(boolean.class, EnvType.class, GameProvider.class);
        constructor.setAccessible(true);
        Object loader = constructor.newInstance(false, EnvType.CLIENT, null);
        var getDelegate = loaderType.getDeclaredMethod("getDelegate");
        getDelegate.setAccessible(true);
        Object delegate = getDelegate.invoke(loader);
        var read = delegate.getClass().getDeclaredMethod("getRawClassBytes", String.class);
        read.setAccessible(true);
        byte[] first = (byte[]) read.invoke(delegate, "org.objectweb.asm.ClassReader");
        byte[] second = (byte[]) read.invoke(delegate, "org.objectweb.asm.ClassReader");
        require(first != null && first != second && Arrays.equals(first, second), "raw bytes changed");
        var rawField = StartupCaches.class.getDeclaredField("RAW");
        rawField.setAccessible(true);
        RawClassCache raw = (RawClassCache) rawField.get(null);
        require(raw.hits() >= 1, "packaged raw-read hook did not execute");

        // Even a warm cache cannot bypass a change in Fabric's parent-loader isolation policy.
        var restrict = delegate.getClass().getDeclaredMethod("setValidParentClassPath", java.util.Collection.class);
        restrict.setAccessible(true);
        restrict.invoke(delegate, List.of());
        require(read.invoke(delegate, "org.objectweb.asm.ClassReader") == null, "parent-loader isolation was bypassed");
        require(read.invoke(delegate, "kernel.smoke.MissingClass") == null, "missing class was invented");

        Provider provider = new Provider();
        provider.bytes = first;
        var tree = provider.getClassNode("one", true, 0);
        tree.methods.clear();
        var fresh = provider.getClassNode("one", false, 0);
        require(!fresh.methods.isEmpty() && provider.calls == 2 && !provider.transformers,
            "target cache skipped provider or shared a mutable ClassNode");
        var skipped = provider.getClassNode("one", true, ClassReader.SKIP_CODE);
        require(skipped.methods.stream().allMatch(method -> method.instructions.size() == 0), "reader flags ignored");
        var targetsField = StartupCaches.class.getDeclaredField("TARGETS");
        targetsField.setAccessible(true);
        TargetClassCache targets = (TargetClassCache) targetsField.get(null);
        require(targets.hits() >= 2, "packaged target-read hook did not execute");

        KernelAgent.finishStartup();
        require(raw.retainedBytes() == 0 && targets.retainedBytes() == 0, "startup memory was not released");
        provider.getClassNode("one", true, 0);
        require(targets.retainedBytes() == 0, "cache refilled after startup");
        System.out.println("Kernel startup agent smoke passed on Java " + Runtime.version().feature());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static final class Provider extends MixinServiceKnot {
        private byte[] bytes;
        private int calls;
        private boolean transformers;

        @Override
        public byte[] getClassBytes(String name, boolean runTransformers) {
            this.calls++;
            this.transformers = runTransformers;
            return this.bytes.clone();
        }
    }
}
