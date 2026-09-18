package dev.kernel.fabric.shader;

import dev.kernel.fabric.shader.pack.PreparedShaderPack;
import dev.kernel.fabric.shader.pack.ShaderOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class ShaderPackStagesTest {
    @TempDir Path temporary;

    /** Runs work with the incomplete world stage opted into, restoring the property afterwards. */
    private static void withWorldStage(org.junit.jupiter.api.function.Executable work) throws Throwable {
        String previous = System.getProperty(PreparedShaderPack.WORLD_STAGE_PROPERTY);
        System.setProperty(PreparedShaderPack.WORLD_STAGE_PROPERTY, "true");
        try { work.execute(); }
        finally {
            if (previous == null) System.clearProperty(PreparedShaderPack.WORLD_STAGE_PROPERTY);
            else System.setProperty(PreparedShaderPack.WORLD_STAGE_PROPERTY, previous);
        }
    }
    private static final String TRIVIAL = "#version 330 core\nout vec4 c;\nvoid main() { c = vec4(1.0); }\n";

    private Path zip(String name, Map<String, String> files) throws Exception {
        Path path = temporary.resolve(name);
        try (var output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var entry : files.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return path;
    }

    @Test void deferredPassesRunBeforeCompositeAndFinalPasses() throws Exception {
        Path path = zip("ordered.zip", Map.of(
            "shaders/final.fsh", TRIVIAL,
            "shaders/composite1.fsh", TRIVIAL,
            "shaders/composite.fsh", TRIVIAL,
            "shaders/deferred2.fsh", TRIVIAL,
            "shaders/deferred.fsh", TRIVIAL));
        var pack = PreparedShaderPack.read(path);
        assertEquals(List.of("deferred", "deferred2", "composite", "composite1", "final"),
            pack.passes().stream().map(PreparedShaderPack.Pass::name).toList());
    }

    @Test void aDimensionFolderReplacesOnlyTheProgramsItShips() throws Exception {
        Path path = zip("dimensions.zip", Map.of(
            "shaders/composite.fsh", TRIVIAL.replace("1.0", "0.1"),
            "shaders/final.fsh", TRIVIAL,
            "shaders/world-1/composite.fsh", TRIVIAL.replace("1.0", "0.5")));
        var overworld = PreparedShaderPack.read(path, PreparedShaderPack.OVERWORLD, Map.of());
        var nether = PreparedShaderPack.read(path, PreparedShaderPack.NETHER, Map.of());
        assertTrue(overworld.passes().getFirst().fragment().contains("vec4(0.1)"));
        assertTrue(nether.passes().getFirst().fragment().contains("vec4(0.5)"));
        // The dimension folder ships no final pass, so both dimensions keep the pack root one.
        assertEquals(List.of("composite", "final"), nether.passes().stream().map(PreparedShaderPack.Pass::name).toList());
        assertEquals(PreparedShaderPack.NETHER, nether.dimension());
    }

    @Test void dimensionFoldersAreChosenByDimensionName() {
        assertEquals(PreparedShaderPack.OVERWORLD, PreparedShaderPack.dimensionFolder("overworld"));
        assertEquals(PreparedShaderPack.NETHER, PreparedShaderPack.dimensionFolder("the_nether"));
        assertEquals(PreparedShaderPack.END, PreparedShaderPack.dimensionFolder("the_end"));
        assertEquals(PreparedShaderPack.OVERWORLD, PreparedShaderPack.dimensionFolder("some_mod_dimension"));
    }

    @Test void optionsAreOfferedInThePackOwnScreenOrderAndAppliedToItsSource() throws Exception {
        Path path = zip("options.zip", Map.of(
            "shaders/shaders.properties", "screen = SHADOW_QUALITY BLOOM\nsliders = SHADOW_QUALITY\n",
            "shaders/composite.fsh", """
                #version 330 core
                #define COMMON_GLSL
                #define BLOOM // Glow around bright pixels
                #define SHADOW_QUALITY 2 //[1 2 4]
                #define HIDDEN
                out vec4 c;
                void main() { c = vec4(1.0); }
                """,
            "shaders/final.fsh", TRIVIAL));
        var defaults = PreparedShaderPack.read(path);
        assertEquals(List.of("SHADOW_QUALITY", "BLOOM"), defaults.options().stream().map(ShaderOption::name).toList());
        assertTrue(defaults.options().getFirst().slider());
        assertFalse(defaults.options().get(1).slider());
        assertTrue(defaults.passes().getFirst().fragment().contains("#define BLOOM"));

        var chosen = PreparedShaderPack.read(path, PreparedShaderPack.OVERWORLD, Map.of("BLOOM", "false", "SHADOW_QUALITY", "4"));
        String source = chosen.passes().getFirst().fragment();
        assertTrue(source.contains("//#define BLOOM"), source);
        assertTrue(source.contains("#define SHADOW_QUALITY 4"), source);
    }

    @Test void aPackWithoutScreensOffersEverythingItDeclaresExceptIncludeGuards() throws Exception {
        Path path = zip("guards.zip", Map.of("shaders/final.fsh", """
            #version 330 core
            #define COMMON_GLSL
            #define LIGHTING_INCLUDED
            #define _PRIVATE
            #define BLOOM
            out vec4 c;
            void main() { c = vec4(1.0); }
            """));
        assertEquals(List.of("BLOOM"), PreparedShaderPack.read(path).options().stream().map(ShaderOption::name).toList());
    }

    @Test void aProgramThePackDisablesWithALiteralIsNotCompiled() throws Exception {
        Path path = zip("disabled.zip", Map.of(
            "shaders/shaders.properties", "program.composite1.enabled = false\n",
            "shaders/composite.fsh", TRIVIAL,
            "shaders/composite1.fsh", TRIVIAL,
            "shaders/final.fsh", TRIVIAL));
        assertEquals(List.of("composite", "final"),
            PreparedShaderPack.read(path).passes().stream().map(PreparedShaderPack.Pass::name).toList());
    }

    @Test void packsNeedingStagesKernelDoesNotRunAreRejectedAndSayWhichOnes() throws Exception {
        Path path = zip("world.zip", Map.of(
            "shaders/gbuffers_terrain.vsh", TRIVIAL,
            "shaders/gbuffers_terrain.fsh", TRIVIAL,
            "shaders/shadow.vsh", TRIVIAL,
            "shaders/composite.fsh", TRIVIAL,
            "shaders/final.fsh", TRIVIAL));
        var failure = assertThrows(java.io.IOException.class, () -> PreparedShaderPack.read(path));
        assertTrue(failure.getMessage().contains("shadow.vsh"), failure.getMessage());
        assertTrue(failure.getMessage().contains("does not run"), failure.getMessage());
    }

    @Test void worldProgramsAreKeptOnlyWhenThePackShipsBothStages() throws Throwable { withWorldStage(() -> {
        Path path = zip("gbuffers.zip", Map.of(
            "shaders/gbuffers_terrain.vsh", TRIVIAL,
            "shaders/gbuffers_terrain.fsh", TRIVIAL,
            "shaders/gbuffers_basic.fsh", TRIVIAL,
            "shaders/final.fsh", TRIVIAL));
        var pack = PreparedShaderPack.read(path);
        // Replacing one half of a Minecraft program pair would leave the varyings disagreeing.
        assertEquals(java.util.Set.of("gbuffers_terrain"), pack.worldPrograms().keySet());
        assertEquals("gbuffers_terrain", pack.worldPrograms().get("gbuffers_terrain").name());
        assertTrue(pack.worldPrograms().get("gbuffers_terrain").vertex().contains("void main"));
    });
    }

    @Test void aProgramThePackDisablesIsNotOfferedAsAWorldReplacement() throws Throwable { withWorldStage(() -> {
        Path path = zip("gbuffers-off.zip", Map.of(
            "shaders/shaders.properties", "program.gbuffers_terrain.enabled = false\n",
            "shaders/gbuffers_terrain.vsh", TRIVIAL,
            "shaders/gbuffers_terrain.fsh", TRIVIAL,
            "shaders/final.fsh", TRIVIAL));
        assertTrue(PreparedShaderPack.read(path).worldPrograms().isEmpty());
    });
    }

    @Test void aPackWithNoWorldProgramsReplacesNothing() throws Throwable { withWorldStage(() -> {
        Path path = zip("post-only.zip", Map.of("shaders/final.fsh", TRIVIAL));
        assertTrue(PreparedShaderPack.read(path).worldPrograms().isEmpty());
    });
    }

    @Test void aPackWithNoSupportedProgramsIsRejected() throws Exception {
        Path path = zip("empty.zip", Map.of("shaders/lib/shared.glsl", "const float V = 1.0;\n", "shaders/other.vsh", TRIVIAL));
        assertThrows(java.io.IOException.class, () -> PreparedShaderPack.read(path));
    }
}
