package dev.kernel.fabric.shader.pack;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShaderMipmapTest {
    @Test void requestsBelongToOneFragmentProgramAndResolveAliases() throws Exception {
        var parser = new ShaderBufferDirectives();
        assertEquals(0x8081, parser.read("""
            const bool colortex0MipmapEnabled = true;
            /* const bool gaux4MipmapEnabled = true; */
            const bool colortex15MipmapEnabled = true;
            const bool colortex1MipmapEnabled = false;
            """, "composite.fsh", true));
        assertEquals(0, parser.read("const bool colortex7MipmapEnabled = false;", "final.fsh", true));
        assertEquals(0, parser.read("void main(){}", "composite2.fsh", true));
        assertEquals(128, parser.build().bytesPerPixel(0xffff));
        assertEquals(128, parser.read("const bool gaux4MipmapEnabled = true;\nconst bool colortex7MipmapEnabled = true;", "same.fsh", true));
    }
    @Test void invalidStagesConflictsAndNonliteralRequestsFailExplicitly() {
        assertThrows(IOException.class, () -> new ShaderBufferDirectives().read("const bool gaux4MipmapEnabled=true;", "composite.vsh", false));
        for (String source : List.of("const bool colortex7MipmapEnabled=true;\nconst bool gaux4MipmapEnabled=false;",
            "const bool colortex7MipmapEnabled=ENABLED;", "const int colortex7MipmapEnabled=1;",
            "#ifdef ENABLED\nconst bool colortex7MipmapEnabled=true;\n#endif"))
            assertThrows(IOException.class, () -> new ShaderBufferDirectives().read(source, "composite.fsh", true));
        assertThrows(IllegalArgumentException.class, () -> new PreparedShaderPack.Pass("final", "", "", List.of(0), 1 << 16));
        assertThrows(IllegalArgumentException.class, () -> new PreparedShaderPack.Pass("final", "", "", List.of(0), -1));
    }
    @Test void memoryAccountingIncludesBothChainsAndOddOrOneDimensionalLevels() throws Exception {
        var parser = new ShaderBufferDirectives();
        parser.read("const int colortex1Format=RGBA32F;", "config");
        var settings = parser.build();
        for (int width = 1; width < 35; width++) for (int height = 1; height < 35; height++) {
            long pixels = (long) width * height, extra = 0;
            for (int level = 1; (width >> (level - 1)) > 1 || (height >> (level - 1)) > 1; level++)
                extra += (long) Math.max(1, width >> level) * Math.max(1, height >> level);
            assertEquals(40 * pixels, settings.allocationBytes(3, 0, width, height));
            assertEquals(40 * pixels + 8 * extra, settings.allocationBytes(3, 1, width, height));
            assertEquals(40 * pixels + 32 * extra, settings.allocationBytes(3, 2, width, height));
            assertEquals(40 * (pixels + extra), settings.allocationBytes(3, 3, width, height));
            assertEquals(40 * pixels, settings.allocationBytes(3, 4, width, height));
        }
        assertEquals(Long.MAX_VALUE, settings.allocationBytes(0xffff, 0xffff, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> settings.allocationBytes(1, 1, 0, 4));
        assertThrows(IllegalArgumentException.class, () -> settings.allocationBytes(1, 1, 4, -1));
    }
}
