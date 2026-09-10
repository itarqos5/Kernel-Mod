package dev.kernel.client.startup;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;

import static org.junit.jupiter.api.Assertions.*;

final class StartupCacheTransformerTest {
    @Test
    void transformsBothAuditedFabricClassesIntoValidBytecode() throws Exception {
        for (String name : new String[]{StartupCacheTransformer.KNOT, StartupCacheTransformer.MIXIN_SERVICE}) {
            byte[] original = readClass(name);
            byte[] transformed = new StartupCacheTransformer().transform(getClass().getClassLoader(), name, null, null, original);
            assertNotNull(transformed, "recognized Fabric method was not patched: " + name);
            StringWriter diagnostics = new StringWriter();
            // The old forwarding test supplies an unsigned fake KnotClient. Verify the real, signed Fabric
            // package in its own loader rather than mixing that fixture with Fabric's signed classes.
            URL resource = getClass().getResource("/" + name + ".class");
            URL jar = ((JarURLConnection) resource.openConnection()).getJarFileURL();
            try (var verifier = new URLClassLoader(new URL[]{jar}, getClass().getClassLoader()) {
                @Override
                protected Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
                    if (!className.startsWith("net.fabricmc.loader.")) return super.loadClass(className, resolve);
                    synchronized (getClassLoadingLock(className)) {
                        Class<?> loaded = findLoadedClass(className);
                        if (loaded == null) loaded = findClass(className);
                        if (resolve) resolveClass(loaded);
                        return loaded;
                    }
                }
            }) {
                CheckClassAdapter.verify(new ClassReader(transformed), verifier, false, new PrintWriter(diagnostics));
            }
            assertEquals("", diagnostics.toString());
            ClassNode node = new ClassNode();
            new ClassReader(transformed).accept(node, 0);
            long bridgeCalls = node.methods.stream().flatMap(method -> java.util.Arrays.stream(method.instructions.toArray()))
                .filter(instruction -> instruction instanceof MethodInsnNode call
                    && call.owner.equals("dev/kernel/client/startup/StartupCaches")).count();
            assertEquals(1, bridgeCalls);
        }
    }

    @Test
    void leavesDifferentLoaderBytecodeAndOtherAgentsEditsUntouched() throws Exception {
        byte[] original = readClass(StartupCacheTransformer.MIXIN_SERVICE);
        byte[] changed = original.clone();
        changed[changed.length - 1] ^= 1;
        StartupCacheTransformer transformer = new StartupCacheTransformer();
        assertNull(transformer.transform(getClass().getClassLoader(), StartupCacheTransformer.MIXIN_SERVICE, null, null, changed));
        assertNull(transformer.transform(getClass().getClassLoader(), "unrelated/Class", null, null, original));
        assertNull(transformer.transform(getClass().getClassLoader(), StartupCacheTransformer.MIXIN_SERVICE, getClass(), null, original));
    }

    static byte[] readClass(String name) throws Exception {
        try (var input = StartupCacheTransformerTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input);
            return input.readAllBytes();
        }
    }
}
