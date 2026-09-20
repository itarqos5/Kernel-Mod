package dev.kernel.fabric.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.kernel.fabric.shader.pack.ShaderWorldAttributes;
import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Written to hold on every target. Where the vertex format API is one Kernel does not extend, no
 * element is registered and the terrain format is always the game's own, which these still assert.
 */
class KernelVertexFormatsTest {
    @Test void demandingNothingLeavesTheGamesOwnFormatInPlace() {
        KernelVertexFormats.register();
        // Identity, not equality: Minecraft compares its own format by reference to pick fast paths.
        assertSame(DefaultVertexFormat.BLOCK, KernelVertexFormats.terrain(Set.of()));
        assertSame(DefaultVertexFormat.BLOCK, KernelVertexFormats.terrain(null));
    }

    @Test void anAttributeKernelDoesNotWriteNeverChangesTheFormat() {
        KernelVertexFormats.register();
        assertSame(DefaultVertexFormat.BLOCK,
            KernelVertexFormats.terrain(EnumSet.of(ShaderWorldAttributes.AT_TANGENT, ShaderWorldAttributes.MC_MID_TEX_COORD)));
    }

    @Test void aDemandedAttributeIsAppendedWithoutDisturbingWhatCameBefore() {
        KernelVertexFormats.register();
        if (KernelVertexFormats.element(ShaderWorldAttributes.MC_ENTITY) == null) return;
        var extended = KernelVertexFormats.terrain(EnumSet.of(ShaderWorldAttributes.MC_ENTITY));
        assertNotSame(DefaultVertexFormat.BLOCK, extended);
        assertTrue(extended.getVertexSize() > DefaultVertexFormat.BLOCK.getVertexSize(),
            "an added attribute has to make the vertex larger");
        assertEquals(0, extended.getVertexSize() % 4, "an unaligned stride costs more than the padding saves");
        // Everything the game's own format declares is still declared, in the same order.
        assertEquals(DefaultVertexFormat.BLOCK.getElements(),
            extended.getElements().subList(0, DefaultVertexFormat.BLOCK.getElements().size()));
    }

    @Test void eachDemandCostsOnlyWhatItAsksFor() {
        KernelVertexFormats.register();
        if (KernelVertexFormats.element(ShaderWorldAttributes.MC_ENTITY) == null) return;
        var one = KernelVertexFormats.terrain(EnumSet.of(ShaderWorldAttributes.MC_ENTITY));
        var both = KernelVertexFormats.terrain(EnumSet.of(ShaderWorldAttributes.MC_ENTITY, ShaderWorldAttributes.AT_MID_BLOCK));
        assertTrue(both.getVertexSize() > one.getVertexSize(), "a second attribute costs more than the first alone");
        assertEquals(0, both.getVertexSize() % 4);
    }

    @Test void theSameDemandReturnsTheSameFormat() {
        KernelVertexFormats.register();
        if (KernelVertexFormats.element(ShaderWorldAttributes.MC_ENTITY) == null) return;
        // Formats are compared by reference on the hot path, so a rebuilt one would defeat that.
        assertSame(KernelVertexFormats.terrain(EnumSet.of(ShaderWorldAttributes.MC_ENTITY)),
            KernelVertexFormats.terrain(EnumSet.of(ShaderWorldAttributes.MC_ENTITY)));
    }

    @Test void registeringTwiceClaimsNothingFurther() {
        KernelVertexFormats.register();
        var first = KernelVertexFormats.element(ShaderWorldAttributes.MC_ENTITY);
        KernelVertexFormats.register();
        assertSame(first, KernelVertexFormats.element(ShaderWorldAttributes.MC_ENTITY));
    }
}
