package dev.kernel.fabric.config;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RendererScreenContractTest {
    @Test void backgroundIsDrawnOnceAcrossTheNativeScreenLifecycle() throws IOException {
        ClassNode vanilla = read("net/minecraft/client/gui/screens/Screen.class");
        ClassNode kernel = read("dev/kernel/fabric/config/KernelSettingsScreen.class");
        MethodNode wrapper = vanilla.methods.stream().filter(method -> method.name.contains("WithTooltip")).findFirst().orElseThrow();
        int vanillaBackgrounds = countBackgroundCalls(wrapper);
        int kernelBackgrounds = kernel.methods.stream().mapToInt(RendererScreenContractTest::countBackgroundCalls).sum();
        assertEquals(1, vanillaBackgrounds + kernelBackgrounds, "Screen must not blur the same frame twice or omit the legacy background");
    }

    private static int countBackgroundCalls(MethodNode method) {
        int count = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && (call.name.equals("renderBackground") || call.name.equals("extractBackground"))) count++;
        }
        return count;
    }

    private static ClassNode read(String resource) throws IOException {
        try (var input = RendererScreenContractTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }
}
