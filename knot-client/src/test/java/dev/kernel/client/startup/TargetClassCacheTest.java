package dev.kernel.client.startup;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

final class TargetClassCacheTest {
    @Test
    void reusesByteIdenticalInputsButReturnsIndependentMutableTrees() {
        TargetClassCache cache = new TargetClassCache(100_000, 8);
        byte[] bytes = fixture("TargetA");
        ClassNode first = cache.read(bytes, 0);
        first.name = "changed";
        first.methods.getFirst().instructions.clear();
        first.methods.getFirst().instructions.add(new InsnNode(Opcodes.ATHROW));
        first.interfaces.add("unexpected/Interface");

        ClassNode second = cache.read(bytes.clone(), 0);
        assertEquals("TargetA", second.name);
        assertTrue(second.interfaces.isEmpty());
        assertEquals(Opcodes.ICONST_1, second.methods.getFirst().instructions.getFirst().getOpcode());
        assertEquals(1, cache.hits());
        assertArrayEquals(serialize(new ClassReader(bytes)), serialize(second));
    }

    @Test
    void changedBytecodeAndReaderFlagsAreNeverStale() {
        TargetClassCache cache = new TargetClassCache(100_000, 8);
        byte[] firstBytes = fixture("TargetA");
        byte[] saved = firstBytes.clone();
        cache.read(firstBytes, 0);
        // Callers may reuse or modify their byte arrays after returning.
        java.util.Arrays.fill(firstBytes, (byte) 0);
        assertEquals("TargetA", cache.read(saved, ClassReader.SKIP_CODE).name);
        assertEquals(0, cache.read(saved, ClassReader.SKIP_CODE).methods.getFirst().instructions.size());
        assertTrue(cache.read(saved, 0).methods.getFirst().instructions.size() > 0);
        assertEquals("TargetB", cache.read(fixture("TargetB"), 0).name);
    }

    @Test
    void evictsAtBothBoundsAndNeverRepopulatesAfterClose() {
        byte[] a = fixture("A");
        byte[] b = fixture("B");
        TargetClassCache cache = new TargetClassCache(4 * a.length, 1);
        cache.read(a, 0);
        cache.read(b, 0);
        cache.read(a, 0);
        assertEquals(0, cache.hits());
        assertTrue(cache.retainedBytes() <= 4 * a.length);
        cache.close();
        cache.read(a, 0);
        assertEquals(0, cache.retainedBytes());

        TargetClassCache disabled = new TargetClassCache(0, 1);
        assertEquals("A", disabled.read(a, 0).name);
        assertEquals(0, disabled.retainedBytes());
    }

    @Test
    void concurrentlyReadsDifferentFlagsWithoutSharingTrees() throws Exception {
        TargetClassCache cache = new TargetClassCache(100_000, 8);
        byte[] bytes = fixture("Concurrent");
        try (var executor = Executors.newFixedThreadPool(4)) {
            var jobs = IntStream.range(0, 100).<Callable<Void>>mapToObj(i -> () -> {
                int flags = i % 2 == 0 ? 0 : ClassReader.SKIP_CODE;
                ClassNode node = cache.read(bytes, flags);
                assertEquals(flags == 0, node.methods.getFirst().instructions.size() > 0);
                node.methods.clear();
                return null;
            }).toList();
            for (var result : executor.invokeAll(jobs)) result.get();
        }
        assertEquals(99, cache.hits());
    }

    static byte[] fixture(String name) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "value", "()I", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] serialize(ClassReader reader) {
        ClassNode node = new ClassNode();
        reader.accept(node, 0);
        return serialize(node);
    }

    private static byte[] serialize(ClassNode node) {
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }
}
