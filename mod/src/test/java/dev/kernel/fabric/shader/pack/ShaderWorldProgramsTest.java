package dev.kernel.fabric.shader.pack;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ShaderWorldProgramsTest {
    @Test void everyChainEndsAtTheRootProgram() {
        for (String program : ShaderWorldPrograms.names()) {
            var chain = ShaderWorldPrograms.chain(program);
            assertEquals(program, chain.getFirst(), program);
            assertEquals("gbuffers_basic", chain.getLast(), program + " chain: " + chain);
            assertEquals(chain.size(), Set.copyOf(chain).size(), "a chain must not repeat a program: " + chain);
        }
    }

    @Test void terrainFallsBackThroughTheTexturedProgramsToTheRoot() {
        assertEquals(List.of("gbuffers_terrain", "gbuffers_textured_lit", "gbuffers_textured", "gbuffers_basic"),
            ShaderWorldPrograms.chain("gbuffers_terrain"));
        assertEquals(List.of("gbuffers_water", "gbuffers_terrain", "gbuffers_textured_lit", "gbuffers_textured", "gbuffers_basic"),
            ShaderWorldPrograms.chain("gbuffers_water"));
        assertEquals(List.of("gbuffers_basic"), ShaderWorldPrograms.chain("gbuffers_basic"));
    }

    @Test void resolutionPrefersTheNearestProgramThePackActuallyShips() {
        assertEquals("gbuffers_terrain", ShaderWorldPrograms.resolve("gbuffers_terrain", Set.of("gbuffers_terrain", "gbuffers_basic")));
        assertEquals("gbuffers_textured", ShaderWorldPrograms.resolve("gbuffers_terrain", Set.of("gbuffers_textured", "gbuffers_basic")));
        assertEquals("gbuffers_basic", ShaderWorldPrograms.resolve("gbuffers_terrain", Set.of("gbuffers_basic")));
        assertNull(ShaderWorldPrograms.resolve("gbuffers_terrain", Set.of("gbuffers_hand")));
        assertNull(ShaderWorldPrograms.resolve("gbuffers_terrain", Set.of()));
    }

    @Test void aPackShippingOnlyTheRootStillReplacesEverySubstitutableCoreShader() {
        var resolved = ShaderWorldPrograms.resolveAll(Set.of("gbuffers_basic"));
        assertEquals(ShaderWorldPrograms.coreShaders().keySet(), resolved.keySet());
        for (String program : resolved.values()) assertEquals("gbuffers_basic", program);
    }

    @Test void aPackShippingNothingUsableReplacesNoCoreShaderAtAll() {
        assertTrue(ShaderWorldPrograms.resolveAll(Set.of()).isEmpty());
        // gbuffers_hand is on no core shader's chain here, so it cannot stand in for one.
        assertTrue(ShaderWorldPrograms.resolveAll(Set.of("gbuffers_hand")).isEmpty());
    }

    @Test void aTerrainPackLeavesUnrelatedDrawsOnTheirOwnFallbacks() {
        var resolved = ShaderWorldPrograms.resolveAll(Set.of("gbuffers_terrain", "gbuffers_textured"));
        assertEquals("gbuffers_terrain", resolved.get("terrain"));
        // Entities fall past the missing textured_lit onto textured; clouds resolve to it directly.
        assertEquals("gbuffers_textured", resolved.get("entity"));
        assertEquals("gbuffers_textured", resolved.get("rendertype_clouds"));
        // Untextured draws have no textured ancestor, so this pack does not replace them.
        assertNull(resolved.get("position"));
    }

    @Test void unknownCoreShadersAreNeverSubstituted() {
        assertNull(ShaderWorldPrograms.forCoreShader("rendertype_text"));
        assertNull(ShaderWorldPrograms.forCoreShader("blit_screen"));
        assertNull(ShaderWorldPrograms.forCoreShader("gui"));
        assertNull(ShaderWorldPrograms.forCoreShader(""));
        assertFalse(ShaderWorldPrograms.resolveAll(ShaderWorldPrograms.names()).containsKey("rendertype_text"));
    }

    @Test void everyMappedProgramIsOneThisClassKnowsHowToResolve() {
        for (Map.Entry<String, String> entry : ShaderWorldPrograms.coreShaders().entrySet())
            assertTrue(ShaderWorldPrograms.names().contains(entry.getValue()), entry.toString());
    }
}
