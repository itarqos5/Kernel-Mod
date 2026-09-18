package dev.kernel.fabric.shader.pack;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ShaderPropertiesTest {
    @Test void slidersScreensAndColumnsAreReadAndUnknownKeysAreKept() {
        var properties = ShaderProperties.parse("""
            # a comment
            sliders = SHADOW_QUALITY SUN_SIZE
            screen = BLOOM [LIGHTING] <empty> SHADOW_QUALITY
            screen.LIGHTING = SUN_SIZE TORCH_COLOR
            screen.LIGHTING.columns = 2
            screen.columns = 1
            profile.LOW = BLOOM=false
            """);
        assertEquals(java.util.Set.of("SHADOW_QUALITY", "SUN_SIZE"), properties.sliders());
        assertEquals(List.of("BLOOM", "[LIGHTING]", "<empty>", "SHADOW_QUALITY"), properties.screen());
        assertEquals(List.of("SUN_SIZE", "TORCH_COLOR"), properties.subScreens().get("LIGHTING"));
        assertEquals(2, properties.columns().get("LIGHTING"));
        assertEquals(1, properties.columns().get(""));
        assertEquals("BLOOM=false", properties.raw().get("profile.LOW"));
    }

    @Test void theLayoutFlattensSubScreensInOrderWithoutRepeatingOrLooping() {
        var properties = ShaderProperties.parse("""
            screen = BLOOM [LIGHTING] BLOOM
            screen.LIGHTING = SUN_SIZE [LIGHTING] [DEEP]
            screen.DEEP = TORCH_COLOR SUN_SIZE
            """);
        assertEquals(List.of("BLOOM", "SUN_SIZE", "TORCH_COLOR"), properties.layout());
    }

    @Test void onlyConditionsKernelCanReadDisableAProgram() {
        var properties = ShaderProperties.parse("""
            program.composite2.enabled = false
            program.composite3.enabled = BLOOM
            program.composite4.enabled = !BLOOM
            program.composite5.enabled = BLOOM && SHADOWS
            """);
        var options = Map.of("BLOOM", "false");
        assertTrue(properties.programEnabled("composite1", options), "an undeclared program stays enabled");
        assertFalse(properties.programEnabled("composite2", options));
        assertFalse(properties.programEnabled("composite3", options));
        assertTrue(properties.programEnabled("composite4", options));
        // Kernel cannot evaluate this expression, and dropping a stage on a guess would lose rendering.
        assertTrue(properties.programEnabled("composite5", options));
        assertTrue(properties.programEnabled("composite3", Map.of("BLOOM", "true")));
        assertTrue(properties.programEnabled("composite3", Map.of()), "an option with no value falls back to enabled");
    }

    @Test void malformedLinesAreSkippedRatherThanFailing() {
        var properties = ShaderProperties.parse("no separator here\n\n  \nsliders\n= missing key\nscreen : A B\n");
        assertEquals(List.of("A", "B"), properties.screen());
        assertTrue(properties.sliders().isEmpty());
    }

    @Test void anEmptyPropertiesFileOffersNothingAndKeepsEveryProgram() {
        var properties = ShaderProperties.empty();
        assertTrue(properties.layout().isEmpty());
        assertTrue(properties.programEnabled("composite", Map.of()));
        assertEquals("", properties.description("BLOOM"));
    }
}
