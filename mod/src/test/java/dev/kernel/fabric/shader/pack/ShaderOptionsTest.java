package dev.kernel.fabric.shader.pack;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ShaderOptionsTest {
    private static ShaderOption option(List<ShaderOption> options, String name) {
        return options.stream().filter(candidate -> candidate.name().equals(name)).findFirst().orElseThrow();
    }

    @Test void bothShippedStatesOfABooleanOptionAreRecognisedWithTheirComments() {
        String source = """
            #define BLOOM // Glow around bright pixels
            //#define MOTION_BLUR //Smear movement
            #define NO_COMMENT
            """;
        var options = ShaderOptions.discover(source);
        assertEquals(List.of("BLOOM", "MOTION_BLUR", "NO_COMMENT"), options.stream().map(ShaderOption::name).toList());
        assertEquals("true", option(options, "BLOOM").defaultValue());
        assertEquals("Glow around bright pixels", option(options, "BLOOM").comment());
        assertEquals("false", option(options, "MOTION_BLUR").defaultValue());
        assertEquals("Smear movement", option(options, "MOTION_BLUR").comment());
        assertEquals("", option(options, "NO_COMMENT").comment());
        for (var declared : options) assertEquals(ShaderOption.Kind.BOOLEAN, declared.kind());
    }

    @Test void valueOptionsKeepTheirDeclaredListAndTheirOwnDefault() {
        String source = """
            #define SHADOW_QUALITY 2 //[1 2 4 8] Shadow sharpness
            const float SUN_SIZE = 1.0; //[0.5 1.0 2.0]
            const int STEPS = 16; //[4 8 16]
            """;
        var options = ShaderOptions.discover(source);
        assertEquals(List.of("SHADOW_QUALITY", "SUN_SIZE", "STEPS"), options.stream().map(ShaderOption::name).toList());
        assertEquals(ShaderOption.Kind.VALUE, option(options, "SHADOW_QUALITY").kind());
        assertEquals(List.of("2", "1", "4", "8"), option(options, "SHADOW_QUALITY").values());
        assertEquals("2", option(options, "SHADOW_QUALITY").defaultValue());
        assertEquals("Shadow sharpness", option(options, "SHADOW_QUALITY").comment());
        assertEquals(List.of("1.0", "0.5", "2.0"), option(options, "SUN_SIZE").values());
        assertEquals("", option(options, "SUN_SIZE").comment());
    }

    @Test void applyingValuesRewritesDeclarationsInPlaceAndKeepsTheLineCount() {
        String source = """
            #version 330 core
            #define BLOOM // Glow
            //#define MOTION_BLUR
            #define SHADOW_QUALITY 2 //[1 2 4]
            const int STEPS = 16; //[4 8 16]
            void main() {}
            """;
        String applied = ShaderOptions.apply(source, Map.of("BLOOM", "false", "MOTION_BLUR", "true",
            "SHADOW_QUALITY", "4", "STEPS", "8"));
        assertEquals(source.split("\n", -1).length, applied.split("\n", -1).length);
        assertTrue(applied.contains("//#define BLOOM // Glow"), applied);
        assertTrue(applied.contains("\n#define MOTION_BLUR\n"), applied);
        assertTrue(applied.contains("#define SHADOW_QUALITY 4 //[1 2 4]"), applied);
        assertTrue(applied.contains("const int STEPS = 8; //[4 8 16]"), applied);
        assertTrue(applied.startsWith("#version 330 core\n"));
    }

    @Test void storedValuesThePackDoesNotDeclareLeaveItsOwnDefaultsInPlace() {
        String source = "#define BLOOM\n#define SHADOW_QUALITY 2 //[1 2 4]\n";
        String applied = ShaderOptions.apply(source, Map.of("BLOOM", "maybe", "SHADOW_QUALITY", "64", "ABSENT", "true"));
        assertEquals(source, applied);
    }

    @Test void declarationsInsideBlockCommentsAreNeitherOfferedNorRewritten() {
        String source = """
            /* documentation
            #define DOCUMENTED
            */
            #define REAL
            """;
        assertEquals(List.of("REAL"), ShaderOptions.discover(source).stream().map(ShaderOption::name).toList());
        assertEquals(source.replace("#define REAL", "//#define REAL"), ShaderOptions.apply(source, Map.of("DOCUMENTED", "false", "REAL", "false")));
    }

    @Test void reservedNamesAndMacroFunctionsAreNotOptions() {
        String source = """
            #define KERNEL 1
            #define MC_GL_VENDOR_AMD
            #define MAX(a, b) ((a) > (b) ? (a) : (b))
            const int colortex4Format = RGBA16F;
            const bool colortex4MipmapEnabled = true;
            #define REAL
            """;
        assertEquals(List.of("REAL"), ShaderOptions.discover(source).stream().map(ShaderOption::name).toList());
    }

    @Test void unusableValueListsAreIgnoredRatherThanGuessed() {
        String source = """
            #define A 1 //[]
            #define B 1 //[1]
            #define C 4 //[1 2 3]
            #define D 1 //[1 2 !!!]
            #define E 1 //[1 2]
            """;
        // A and B declare too few values and D lists an unusable token, so neither becomes an option.
        var options = ShaderOptions.discover(source);
        assertEquals(List.of("C", "E"), options.stream().map(ShaderOption::name).toList());
        // A value the pack ships but leaves out of its own list stays selectable, and stays the default.
        assertEquals(List.of("4", "1", "2", "3"), option(options, "C").values());
        assertEquals("4", option(options, "C").defaultValue());
    }

    @Test void windowsLineEndingsSurviveDiscoveryAndRewriting() {
        String source = "#define BLOOM // Glow\r\n#define SHADOW 2 //[1 2 4]\r\n";
        var options = ShaderOptions.discover(source);
        assertEquals(List.of("BLOOM", "SHADOW"), options.stream().map(ShaderOption::name).toList());
        String applied = ShaderOptions.apply(source, Map.of("BLOOM", "false", "SHADOW", "4"));
        assertEquals("//#define BLOOM // Glow\r\n#define SHADOW 4 //[1 2 4]\r\n", applied);
    }

    @Test void theNumberOfDiscoveredOptionsIsBounded() {
        var source = new StringBuilder();
        for (int index = 0; index < ShaderOptions.MAX_OPTIONS + 50; index++) source.append("#define OPTION").append(index).append('\n');
        assertEquals(ShaderOptions.MAX_OPTIONS, ShaderOptions.discover(source.toString()).size());
    }

    @Test void cyclingWrapsThroughTheDeclaredValuesFromAnUnknownCurrentValue() {
        var option = new ShaderOption("Q", ShaderOption.Kind.VALUE, "2", List.of("2", "1", "4"), "", false);
        assertEquals("1", option.cycle("2", 1));
        assertEquals("4", option.cycle("2", -1));
        assertEquals("1", option.cycle("unknown", 1));
    }
}
