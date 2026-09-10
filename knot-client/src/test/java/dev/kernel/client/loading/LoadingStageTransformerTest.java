package dev.kernel.client.loading;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.util.CheckClassAdapter;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

final class LoadingStageTransformerTest {
    @Test void labelsAuditedEntrypointAndMixinMethodsWithoutReplacingTheirBodies() throws Exception {
        var transformer = new LoadingStageTransformer();
        for (String name : new String[]{LoadingStageTransformer.MIXIN, LoadingStageTransformer.ENTRYPOINT}) {
            byte[] original;
            try (var stream = getClass().getResourceAsStream("/" + name + ".class")) {
                assertNotNull(stream); original = stream.readAllBytes();
            }
            byte[] result = transformer.transform(getClass().getClassLoader(), name, null, null, original);
            assertNotNull(result, "Audited class was not instrumented: " + name);
            new ClassReader(result).accept(new CheckClassAdapter(new ClassWriter(0)), 0);
            ClassNode before = new ClassNode(), after = new ClassNode();
            new ClassReader(original).accept(before, 0); new ClassReader(result).accept(after, 0);
            assertEquals(before.methods.size(), after.methods.size());
            int bridgeCalls = 0;
            for (int i = 0; i < before.methods.size(); i++) {
                var oldMethod = before.methods.get(i); var newMethod = after.methods.get(i);
                assertEquals(oldMethod.tryCatchBlocks.size(), newMethod.tryCatchBlocks.size());
                assertTrue(newMethod.instructions.size() >= oldMethod.instructions.size());
                bridgeCalls += (int) Arrays.stream(newMethod.instructions.toArray())
                    .filter(insn -> insn instanceof MethodInsnNode call && call.owner.equals("dev/kernel/client/loading/StartupProgress")).count();
            }
            assertEquals(1, bridgeCalls);
            byte[] changed = original.clone(); changed[changed.length - 1] ^= 1;
            assertNull(transformer.transform(getClass().getClassLoader(), name, null, null, changed));
            assertNull(transformer.transform(getClass().getClassLoader(), name, getClass(), null, original));
        }
    }
}
