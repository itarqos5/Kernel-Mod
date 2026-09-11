package dev.kernel.fabric.shader;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceDescriptor;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShaderDepthPassTest {
    private static final class Target { int value; boolean freed; }
    @Test void capturesBeforeLateClearsAndRetainsTransientResourcesUntilUse() {
        for (boolean transparent : new boolean[]{false, true}) for (boolean late : new boolean[]{false, true}) {
            List<String> events = new ArrayList<>();
            FrameGraphBuilder graph = new FrameGraphBuilder();
            Target main = new Target();
            var mainHandle = graph.importExternal("main", main);
            var transientHandle = transparent ? graph.createInternal("transparent", new ResourceDescriptor<Target>() {
                public Target allocate() { events.add("allocate"); return new Target(); }
                public void free(Target value) { value.freed = true; events.add("free"); }
            }) : null;
            var world = graph.addPass("world");
            mainHandle = world.readsAndWrites(mainHandle);
            if (transientHandle != null) transientHandle = world.readsAndWrites(transientHandle);
            var drawnMain = mainHandle;
            var drawnTransparent = transientHandle;
            world.executes(() -> {
                events.add("world"); drawnMain.get().value = 5;
                if (drawnTransparent != null) drawnTransparent.get().value = 3;
            });
            if (transparent) {
                var composite = graph.addPass("composite");
                mainHandle = composite.readsAndWrites(mainHandle); composite.reads(transientHandle);
                var input = transientHandle;
                composite.executes(() -> { assertFalse(input.get().freed); assertEquals(3, input.get().value); events.add("composite"); });
            }
            Target[] capturedTransient = new Target[1];
            mainHandle = ShaderDepthPass.add(graph, mainHandle, transparent ? List.of(transientHandle) : List.of(), inputs -> {
                assertSame(main, inputs.getFirst()); assertEquals(5, main.value);
                assertEquals(transparent ? 2 : 1, inputs.size());
                if (transparent) {
                    capturedTransient[0] = inputs.get(1);
                    assertFalse(capturedTransient[0].freed); assertEquals(3, capturedTransient[0].value);
                    assertTrue(events.contains("composite"));
                }
                events.add("capture");
            });
            if (late) {
                var debug = graph.addPass("late_debug");
                mainHandle = debug.readsAndWrites(mainHandle);
                var output = mainHandle;
                debug.executes(() -> { events.add("debug"); output.get().value = 10; });
            }
            graph.execute(GraphicsResourceAllocator.UNPOOLED);
            assertTrue(events.contains("capture"));
            if (late) assertTrue(events.indexOf("capture") < events.indexOf("debug"));
            if (transparent) {
                assertTrue(capturedTransient[0].freed);
                assertTrue(events.indexOf("free") > events.indexOf("capture"));
            }
            assertEquals(late ? 10 : 5, main.value); assertFalse(main.freed);
        }
    }
    @Test void depthHandMacroDescribesTheNativeUnscaledProjection() throws Exception {
        String code = dev.kernel.fabric.shader.pack.ShaderSource.translate(
            "#version 120\nvoid main(){gl_FragColor=vec4(MC_HAND_DEPTH);}", false);
        assertTrue(code.contains("#define MC_HAND_DEPTH 1.0\n"));
        assertThrows(java.io.IOException.class, () -> dev.kernel.fabric.shader.pack.ShaderSource.translate(
            "void main(){gl_FragColor=vec4(MC_HAND_DEPTH_EXTRA);}", false));
    }
}
