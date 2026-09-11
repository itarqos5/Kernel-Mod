package dev.kernel.fabric.shader.pack;

import java.io.IOException;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShaderBufferSettingsTest {
    @Test void readsAllFormatsAndAccountsForBothImagesOfEachRequiredBuffer() throws Exception {
        var parser = new ShaderBufferDirectives();
        int index = 0;
        long bytes = 0;
        for (var format : ShaderColorFormat.values()) {
            parser.read("const int colortex" + index + "Format = " + format + ";", "composite.fsh");
            bytes += 2L * format.bytes(); index++;
        }
        var settings = parser.build();
        assertEquals(bytes, settings.bytesPerPixel((1 << index) - 1));
        for (int buffer = 0; buffer < index; buffer++) {
            assertEquals(ShaderColorFormat.values()[buffer], settings.buffers().get(buffer).format());
            assertTrue(settings.buffers().get(buffer).explicitFormat());
        }
    }

    @Test void acceptsAliasesBlockCommentDeclarationsAndFourLiteralClearComponents() throws Exception {
        var parser = new ShaderBufferDirectives();
        parser.read("""
            // const int colortex3Format = UNREAD;
            /*
            const int gaux4Format = RGBA16F;
            */
            const bool gaux4Clear = false;
            const vec4 gaux4ClearColor = vec4(-0., .25f, 2e-1, +1.0F);
            const bool gaux4MipmapEnabled = false;
            """, "composite.fsh");
        parser.read("/* const int colortex7Format = RGBA16F; */", "final.fsh");
        var settings = parser.build();
        var buffer = settings.buffers().get(7);
        assertEquals(ShaderColorFormat.RGBA16F, buffer.format());
        assertFalse(buffer.clear());
        assertArrayEquals(new float[]{-0f,.25f,.2f,1f}, buffer.color().array());
        assertEquals(ShaderColorFormat.RGBA8, settings.buffers().get(3).format());
    }

    @Test void snapshotsRemainImmutableAndLegacyDepthUpgradesOnlyAnUnspecifiedFormat() throws Exception {
        var parser = new ShaderBufferDirectives();
        var first = parser.build();
        assertEquals(ShaderColorFormat.RGBA32F, first.withLegacyDepth().buffers().get(1).format());
        assertEquals(ShaderColorFormat.RGBA8, first.buffers().get(1).format());
        parser.read("const int gdepthFormat = R16F;", "composite.vsh");
        var second = parser.build();
        assertSame(second, second.withLegacyDepth());
        assertThrows(UnsupportedOperationException.class, () -> second.buffers().clear());
        var mutable = new ArrayList<>(second.buffers());
        var copied = new ShaderBufferSettings(mutable); mutable.clear();
        assertEquals(16, copied.buffers().size());
        float[] color = first.buffers().get(1).color().array(); color[0] = 0;
        assertEquals(1, first.buffers().get(1).color().red());
    }

    @Test void rejectsConflictingConditionalAndUnsupportedConfiguration() throws Exception {
        var parser = new ShaderBufferDirectives();
        parser.read("const int colortex1Format = RGBA16F;", "first");
        assertThrows(IOException.class, () -> parser.read("const int gdepthFormat = R32F;", "second"));
        for (String bad : new String[]{
            "#if 0\nconst int colortex1Format = RGBA16F;\n#endif",
            "#ifdef GUARD\nconst bool colortex1Clear = false;\n#endif",
            "const int colortex16Format = RGBA8;", "const int gaux5Format = RGBA8;",
            "const int colortex1Format = RGBA8I;", "const int colortex1Format = MY_FORMAT;",
            "const bool colortex1Clear = 0;", "const float colortex1Clear = false;",
            "const bool colortex0Clear = false;", "const vec4 colortex0ClearColor = vec4(0,0,0,0);",
            "const vec4 colortex1ClearColor = vec4(0);", "const vec4 colortex1ClearColor = vec4(0,0,0,1e999);",
            "const vec4 colortex1ClearColor = vec4(0,0,0,VALUE);", "const bool colortex1MipmapEnabled = true;",
            "/* const int colortex1Format = RGBA16F; */ #endif", "const int colortex1Format = RGBA16F; int extra;"})
            assertThrows(IOException.class, () -> new ShaderBufferDirectives().read(bad, "program"), bad);
    }

    @Test void defaultsAndMalformedCommentScanningStayBounded() {
        var parser = new ShaderBufferDirectives();
        assertTimeout(java.time.Duration.ofSeconds(5), () -> parser.read("/* const int colortex1Format = ".repeat(32768), "malformed"));
        var defaults = parser.build();
        assertEquals(128, defaults.bytesPerPixel(0xffff));
        assertArrayEquals(new float[]{1,1,1,1}, defaults.buffers().get(1).color().array());
        assertArrayEquals(new float[]{0,0,0,0}, defaults.buffers().get(15).color().array());
    }
}
