package dev.kernel.fabric.shader.pack;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShaderWorldAttributesTest {
    @Test void aDeclarationIsFoundInEitherSpellingAPackMayUse() {
        assertTrue(ShaderWorldAttributes.declaredIn("attribute vec2 mc_Entity;")
            .contains(ShaderWorldAttributes.MC_ENTITY));
        assertTrue(ShaderWorldAttributes.declaredIn("in vec3 at_midBlock;")
            .contains(ShaderWorldAttributes.AT_MID_BLOCK));
    }

    @Test void onlyTheAttributesAProgramDeclaresAreCounted() {
        var declared = ShaderWorldAttributes.declaredIn("attribute vec2 mc_Entity;\nin vec4 at_tangent;\n");
        assertEquals(2, declared.size());
        assertFalse(declared.contains(ShaderWorldAttributes.AT_MID_BLOCK));
        assertTrue(ShaderWorldAttributes.declaredIn("void main() {}").isEmpty());
    }

    @Test void aNameUsedWithoutBeingDeclaredIsNotADeclaration() {
        // The pack's own varying of the same name is not an attribute Kernel would have to write.
        assertTrue(ShaderWorldAttributes.declaredIn("varying vec2 mc_Entity;").isEmpty());
    }

    @Test void refusalNamesTheAttributeTheProgramNeeds() {
        var missing = ShaderWorldAttributes.unsupportedIn("attribute vec4 at_tangent;\nvoid main() {}");
        assertEquals(1, missing.size());
        assertEquals("at_tangent", ShaderWorldAttributes.names(missing));
    }

    @Test void aProgramNeedingNothingExtraIsNotRefused() {
        assertTrue(ShaderWorldAttributes.unsupportedIn("void main() { gl_Position = ftransform(); }").isEmpty());
        assertTrue(ShaderWorldAttributes.unsupportedIn(null).isEmpty());
    }

    @Test void everyAttributeStillRefusedIsReportedByName() {
        // Nothing is written during meshing yet, so each of the five is named rather than lumped.
        for (var attribute : ShaderWorldAttributes.values()) {
            if (attribute.supplied()) continue;
            var missing = ShaderWorldAttributes.unsupportedIn("attribute vec4 " + attribute.glslName() + ";");
            assertTrue(missing.contains(attribute), attribute.glslName());
            assertTrue(ShaderWorldAttributes.names(missing).contains(attribute.glslName()));
        }
    }
}
