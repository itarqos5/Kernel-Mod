package dev.kernel.fabric.shader;

import dev.kernel.fabric.shader.pack.ShaderSource;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShaderLegacySamplerTest {
    @Test void legacyCallsResolveBeforeTheSamplerShadowsTheModernFunction() throws Exception {
        for (String declaration : new String[]{"uniform sampler2D texture;", "uniform highp sampler2D texture;", "uniform sampler2D colortex0, texture;"}) {
            String translated = ShaderSource.translate("#version 120\n" + declaration
                + "\nvoid main(){gl_FragColor=texture2D(texture,vec2(.5));}", false);
            assertTrue(translated.contains(declaration));
            assertTrue(translated.indexOf("return texture(s, p);") < translated.indexOf(declaration));
            assertTrue(translated.contains("kernel_texture2D(texture,vec2(.5))"));
            assertTrue(translated.contains("return texture(s, p, bias);"));
        }
        String vertex = ShaderSource.translate("uniform sampler2D texture; void main(){gl_Position=texture2D(texture,vec2(.5));}", true);
        assertTrue(vertex.contains("return texture(s, p);"));
        assertFalse(vertex.contains("float bias"));
    }
    @Test void ordinaryModernCallsCommentsAndOtherSamplerNamesAreUnaffected() throws Exception {
        String source = "#version 330 core\n// uniform sampler2D texture;\nuniform sampler2D colortex0;"
            + "out vec4 color; void main(){color=texture(colortex0,vec2(.5));}";
        assertFalse(ShaderSource.translate(source, false).contains("kernel_texture2D"));
        assertFalse(ShaderSource.translate("uniform sampler2D textureOther; /* uniform sampler2D texture; */", false).contains("kernel_texture2D"));
        assertThrows(IOException.class, () -> ShaderSource.translate("uniform sampler2D texture; float kernel_texture2D;", false));
    }
    @Test void unterminatedRepeatedDeclarationsStayBounded() {
        assertTimeout(Duration.ofSeconds(5), () -> ShaderSource.translate("uniform sampler2D ".repeat(65536), false));
    }
}
