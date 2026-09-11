package dev.kernel.fabric.shader;

import dev.kernel.fabric.shader.pack.PreparedShaderPack;
import dev.kernel.fabric.shader.pack.ShaderDrawTargets;
import dev.kernel.fabric.shader.pack.ShaderSource;
import dev.kernel.fabric.shader.pack.ShaderFragmentOutputs;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShaderDrawTargetsTest {
    @Test void mapsOutputSlotsToLogicalBuffersAndKeepsFinalSeparate() throws Exception {
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7), ShaderDrawTargets.read("void main() {}", false));
        assertEquals(List.of(3, 7, 9), ShaderDrawTargets.read("/* DRAWBUFFERS:379 */", false));
        assertEquals(List.of(15, 0, 12), ShaderDrawTargets.read("/* RENDERTARGETS: 15, 0,12 */", false));
        assertEquals(List.of(3, 7), ShaderDrawTargets.read("/* DRAWBUFFERS:37 */\n/* RENDERTARGETS:3,7 */", false));
        assertEquals(List.of(0), ShaderDrawTargets.read("#if 0\n/* RENDERTARGETS: invalid */\n#endif", true));
    }

    @Test void readsRealBlockCommentsWithoutMistakingQuotedOrLineCommentTextForMetadata() throws Exception {
        String source = "// /* DRAWBUFFERS:0 */\n#define TEXT \"/* RENDERTARGETS:0 */\"\n"
            + "/* explanatory DRAWBUFFERS:0 text */\n/* RENDERTARGETS:\n 3, 7 */\n";
        assertEquals(List.of(3, 7), ShaderDrawTargets.read(source, false));
        assertEquals(List.of(2), ShaderDrawTargets.read("/* #if 0 */\n// #ifdef OFF\n/* DRAWBUFFERS:2 */", false));
    }

    @Test void rejectsConditionalMetadataInsteadOfGuessingWhichBranchTheDriverUses() throws Exception {
        for (String condition : List.of("#if 0", "#ifdef A", "#ifndef A", "/* leading */ #if 1")) {
            IOException error = assertThrows(IOException.class,
                () -> ShaderDrawTargets.read(condition + "\n/* DRAWBUFFERS:0 */\n#endif", false));
            assertTrue(error.getMessage().contains("Conditional"));
        }
        assertEquals(List.of(4), ShaderDrawTargets.read("#if 0\n#if 1\n#endif\n#else\n#endif\n/* DRAWBUFFERS:4 */", false));
        assertThrows(IOException.class, () -> ShaderDrawTargets.read("#if 1\n#else\n/* DRAWBUFFERS:0 */\n#endif", false));
        assertThrows(IOException.class, () -> ShaderDrawTargets.read("#if 1\n/* DRAWBUFFERS:0 */ #endif", false));
        assertThrows(IOException.class, () -> ShaderDrawTargets.read("void main() {} /* DRAWBUFFERS:0 */", false));
    }

    @Test void rejectsAmbiguousOutOfRangeDuplicateAndOversizedDeclarations() {
        for (String value : List.of("DRAWBUFFERS:", "DRAWBUFFERS:00", "DRAWBUFFERS:012345678", "DRAWBUFFERS:0,1",
            "RENDERTARGETS:16", "RENDERTARGETS:-1", "RENDERTARGETS:1,1", "RENDERTARGETS:0,",
            "RENDERTARGETS:999999999999999", "RENDERTARGETS:0,1,2,3,4,5,6,7,8"))
            assertThrows(IOException.class, () -> ShaderDrawTargets.read("/* " + value + " */", false), value);
        assertThrows(IOException.class, () -> ShaderDrawTargets.read("/* DRAWBUFFERS:0 */\n/* RENDERTARGETS:1 */", false));
    }

    @Test void passTargetsAreImmutableAndValidateDirectCallers() {
        var input = new ArrayList<>(List.of(15, 0));
        var pass = new PreparedShaderPack.Pass("composite", "vertex", "fragment", input);
        input.clear();
        assertEquals(List.of(15, 0), pass.drawTargets());
        assertThrows(UnsupportedOperationException.class, () -> pass.drawTargets().add(1));
        for (var invalid : List.of(List.<Integer>of(), List.of(-1), List.of(16), List.of(0, 0), List.of(0,1,2,3,4,5,6,7,8)))
            assertThrows(IllegalArgumentException.class, () -> new PreparedShaderPack.Pass("composite", "", "", invalid));
        assertThrows(IllegalArgumentException.class, () -> new PreparedShaderPack.Pass("final", "", "", List.of(1)));
    }

    @Test void translatesEveryLegacyOutputOnceAndRejectsDynamicLocations() throws Exception {
        String translated = ShaderSource.translate("#version 120\nvoid main() { gl_FragData[ 7 ] = vec4(1); gl_FragData[7] += vec4(1); gl_FragColor = vec4(0); }", false);
        assertEquals(1, translated.split("out vec4 kernel_fragColor7;", -1).length - 1);
        assertTrue(translated.contains("layout(location = 7) out vec4 kernel_fragColor7;"));
        assertTrue(translated.contains("layout(location = 0) out vec4 kernel_fragColor0;"));
        assertFalse(translated.contains("gl_FragData"));
        for (String index : List.of("8", "-1", "i", "1 + 1", "INDEX"))
            assertThrows(IOException.class, () -> ShaderSource.translate("void main(){gl_FragData[" + index + "]=vec4(1);}", false));
    }

    @Test void validatesAllGlobalOutputsWithoutConfusingCommentsOrFunctionParameters() throws Exception {
        assertEquals(List.of("outColor0", "other"), ShaderFragmentOutputs.read("""
            // out vec4 imaginary;
            /* out vec4 ignored[2]; */
            layout(location=0) out vec4 outColor0;
            layout(location=1) out highp vec4 other;
            void calculate(out vec4 result) { result = vec4(1); }
            void main(){ calculate(outColor0); other=outColor0; }
            """));
        for (String unsupported : List.of("out vec4 extra[2];", "out vec3 extra;", "out vec4 a,b;", "out Colors {vec4 value;} extra;",
            "#define DECL out vec4\nDECL extra;", "#define extra renamed\nout vec4 extra;", "#define JOIN(a,b) a ## b\n",
            "#define OPEN {\n", "#define CLOSE )\n",
            "#if 0\nvoid ignored(){\n#else\nfloat value=1;\n#endif\nout vec4 hidden[2];\n#if 0\n}\n#endif"))
            assertThrows(IOException.class, () -> ShaderFragmentOutputs.read("out vec4 valid;\n" + unsupported));
        assertEquals(List.of("color"), ShaderFragmentOutputs.read("#if 0\nout vec4 color;\n#else\nout vec4 color;\n#endif"));
    }

    @Test void malformedCommentPrefixesAreScannedWithoutRepeatedBacktracking() {
        String malformed = "/* out vec4 fake; ".repeat(65536);
        assertTimeout(java.time.Duration.ofSeconds(5), () -> {
            assertThrows(IOException.class, () -> ShaderFragmentOutputs.read(malformed));
            assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7), ShaderDrawTargets.read(malformed, false));
        });
    }
}
