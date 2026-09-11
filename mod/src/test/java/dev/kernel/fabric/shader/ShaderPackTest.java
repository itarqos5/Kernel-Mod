package dev.kernel.fabric.shader;

import dev.kernel.fabric.shader.pack.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.net.URI;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class ShaderPackTest {
    @TempDir Path temporary;
    private Path zip(String name, Map<String, String> files) throws Exception {
        Path path = temporary.resolve(name);
        try (var output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var entry : files.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey())); output.write(entry.getValue().getBytes(StandardCharsets.UTF_8)); output.closeEntry();
            }
        }
        return path;
    }
    @Test void includesStayWithinThePackAndPreserveSourceLocationsAndComments() throws Exception {
        Path path = zip("nested.zip", Map.of(
            "Wrapper/shaders/final.fsh", "#version 330 core\n/* old comment\n*/ #include \"/lib/shared.glsl\" /* new comment\nignored\n*/\nvoid main() {}",
            "Wrapper/shaders/lib/shared.glsl", "#include \"../common.glsl\"\n",
            "Wrapper/shaders/common.glsl", "const float V = 1.0;\n"));
        try (var pack = new ShaderPackArchive(path)) {
            var expanded = pack.expand("final.fsh");
            assertTrue(expanded.source().startsWith("#version 330 core\n"));
            assertTrue(expanded.source().contains("*/\n#line 1 1"));
            assertTrue(expanded.source().contains("#line 4 0 /*\nignored\n*/"));
            assertTrue(expanded.source().contains("const float V = 1.0;"));
            assertEquals(3, expanded.sourceFiles().size());
            assertEquals(List.of("common.glsl", "final.fsh", "lib/shared.glsl"), new ArrayList<>(pack.files()));
        }
    }
    @Test void invalidArchivesCannotEscapeAndOversizedSourcesAreRejected() throws Exception {
        for (String bad : List.of("../outside.txt", "/absolute", "C:/absolute", "shaders\\other.glsl")) {
            var path = zip("bad.zip", Map.of("shaders/final.fsh", "void main() {}", bad, "bad"));
            assertThrows(java.io.IOException.class, () -> new ShaderPackArchive(path));
        }
        var duplicate = zip("duplicate.zip", Map.of("shaders/final.fsh", "", "shaders/./final.fsh", ""));
        assertThrows(java.io.IOException.class, () -> new ShaderPackArchive(duplicate));
        var escape = zip("escape.zip", Map.of("shaders/final.fsh", "#include \"../outside.glsl\"", "outside.glsl", "bad"));
        try (var pack = new ShaderPackArchive(escape)) { assertThrows(java.io.IOException.class, () -> pack.expand("final.fsh")); }
        var oversized = zip("large.zip", Map.of("shaders/final.fsh", "x".repeat(4 * 1024 * 1024 + 1)));
        try (var pack = new ShaderPackArchive(oversized)) { assertThrows(java.io.IOException.class, () -> pack.source("final.fsh")); }
    }
    @Test void conditionalIncludesRetainDriverPreprocessingAndBoundRecursion() throws Exception {
        var path = zip("conditional.zip", Map.of("shaders/final.fsh", "#version 330 core\n#if 0\n#include \"missing.glsl\"\n#endif\n#include \"guard.glsl\"\n",
            "shaders/guard.glsl", "#ifndef GUARD\n#define GUARD\n#include \"guard.glsl\"\nconst float V = 1.0;\n#endif\n"));
        try (var archive = new ShaderPackArchive(path)) {
            var expanded = archive.expand("final.fsh");
            String source = expanded.source();
            assertTrue(source.contains("#if 0\n#line 1 1\n#error Kernel missing include: missing.glsl\n#line 4 0\n#endif"));
            assertTrue(source.contains("#error Kernel include depth exceeded: guard.glsl"));
            assertTrue(source.length() < 16000);
            assertEquals(3, expanded.sourceFiles().size());
            assertThrows(java.io.IOException.class, () -> archive.expand("missing-root.fsh"));
        }
    }
    @Test void continuationsPrecedeCommentsAndPreservePhysicalLineNumbers() throws Exception {
        var path = zip("continued.zip", Map.of("shaders/final.fsh",
            "#version 330 core\n#inc\\\nlude \\\n\"lib.glsl\"\n// continued comment\\\n#include \"not-read.glsl\"\nvoid main() {}\n",
            "shaders/lib.glsl", "const float V = 1.0;\n"));
        try (var archive = new ShaderPackArchive(path)) {
            String source = archive.expand("final.fsh").source();
            assertTrue(source.contains("const float V = 1.0;\n"));
            assertTrue(source.contains("#line 5 0\n// continued comment#include \"not-read.glsl\"\n\n"));
            assertFalse(source.contains("missing include"));
        }
        // A newly exposed backslash/newline pair must not be spliced a second time.
        path = zip("single-pass.zip", Map.of("shaders/final.fsh", "// note\\\\\n\n#include \"lib.glsl\"", "shaders/lib.glsl", "const int ACTIVE=1;"));
        try (var archive = new ShaderPackArchive(path)) { assertTrue(archive.expand("final.fsh").source().contains("const int ACTIVE=1;")); }
    }
    @Test void expansionLimitsApplyEvenToBranchesThatTheDriverMayDisable() throws Exception {
        var path = zip("many-lines.zip", Map.of("shaders/final.fsh", "#if 0\n" + "\n".repeat(262145) + "#endif"));
        try (var archive = new ShaderPackArchive(path)) { assertThrows(java.io.IOException.class, () -> archive.expand("final.fsh")); }
        path = zip("expanded.zip", Map.of("shaders/final.fsh", "#include \"large.glsl\"\n".repeat(6), "shaders/large.glsl", " ".repeat(3 * 1024 * 1024)));
        try (var archive = new ShaderPackArchive(path)) { assertThrows(java.io.IOException.class, () -> archive.expand("final.fsh")); }
    }
    @Test void installationChecksIntegrityAndNeverOverwritesAnExistingPackOrImportSource() throws Exception {
        Path source = zip("original.zip", Map.of("shaders/final.fsh", "void main() {}"));
        byte[] bytes = Files.readAllBytes(source);
        Path destination = temporary.resolve("shaderpacks");
        var installer = new ShaderPackInstaller(destination, (uri, path, expected, digest, progress) -> {
            Files.write(path, bytes); digest.update(bytes); progress.accept(bytes.length); return bytes.length;
        });
        Path imported = installer.importZip(source, ignored -> {});
        assertArrayEquals(bytes, Files.readAllBytes(source));
        assertEquals(imported, installer.importZip(source, ignored -> {}));
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(bytes));
        var download = new ModrinthShaders.Download("test", "version", "original.zip", URI.create("https://cdn.modrinth.com/data/test/original.zip"), bytes.length, hash, Instant.EPOCH, true);
        assertEquals(imported, installer.download(download, ignored -> {}));
        Files.writeString(imported, "Unrelated content");
        assertNotEquals(imported, installer.download(download, ignored -> {}));
        assertEquals("Unrelated content", Files.readString(imported));
        var corrupt = new ModrinthShaders.Download("test", "version", "original.zip", download.url(), bytes.length, "0".repeat(128), Instant.EPOCH, true);
        assertThrows(java.io.IOException.class, () -> installer.download(corrupt, ignored -> {}));
        try (var files = Files.list(destination)) { assertFalse(files.anyMatch(path -> path.toString().endsWith(".part"))); }
    }
    @Test void packPreparationRejectsUnimplementedStagesAndKeepsPassOrder() throws Exception {
        String shader = "#version 120\n/* DRAWBUFFERS:0 */\nvoid main() { gl_FragData[0] = vec4(1); }";
        Path supported = zip("passes.zip", Map.of("shaders/composite2.fsh", shader, "shaders/composite.fsh", shader, "shaders/final.fsh", shader));
        var pack = PreparedShaderPack.read(supported);
        assertEquals(List.of("composite", "composite2", "final"), pack.passes().stream().map(PreparedShaderPack.Pass::name).toList());
        assertTrue(pack.passes().getFirst().fragment().contains("kernel_fragColor0 = vec4(1)"));
        var unsupported = zip("terrain.zip", Map.of("shaders/final.fsh", shader, "shaders/gbuffers_terrain.vsh", "void main() {}"));
        assertThrows(java.io.IOException.class, () -> PreparedShaderPack.read(unsupported));
        var multiple = zip("mrt.zip", Map.of("shaders/composite.fsh", shader.replace("DRAWBUFFERS:0", "RENDERTARGETS:3,15")
            .replace("gl_FragData[0] = vec4(1);", "gl_FragData[0] = vec4(1); gl_FragData[1] = vec4(0);")));
        var prepared = PreparedShaderPack.read(multiple).passes().getFirst();
        assertEquals(List.of(3, 15), prepared.drawTargets());
        assertTrue(prepared.fragment().contains("layout(location = 1) out vec4 kernel_fragColor1;"));
        for (String directive : List.of("const int colortex7Format = RGBA8I;", "const bool gaux4Clear = UNKNOWN;",
            "const vec4 gcolorClearColor = vec4(0);", "const bool compositeMipmapEnabled = true;", "GAUX4FORMAT")) {
            var configured = zip("configured.zip", Map.of("shaders/composite.fsh", shader + "\n/* " + directive + " */"));
            assertThrows(java.io.IOException.class, () -> PreparedShaderPack.read(configured));
        }
    }
    @Test void bufferSettingsAreCollectedFromBothProgramStagesAndExpandedIncludes() throws Exception {
        var files = new HashMap<String, String>();
        files.put("shaders/final.fsh", "#version 120\n#include \"buffers.glsl\"\nvoid main(){gl_FragColor=vec4(1);}");
        files.put("shaders/final.vsh", "#version 120\n/* const int gdepthFormat = R32F; */\nvoid main(){gl_Position=gl_Vertex;}");
        files.put("shaders/buffers.glsl", "/* const int colortex0Format = RG16; */\n/* const bool colortex7Clear = false; */");
        var pack = PreparedShaderPack.read(zip("settings.zip", files));
        assertEquals(ShaderColorFormat.RG16, pack.buffers().buffers().get(0).format());
        assertEquals(ShaderColorFormat.R32F, pack.buffers().buffers().get(1).format());
        assertFalse(pack.buffers().buffers().get(7).clear());
        files.put("shaders/final.vsh", "/* const int colortex0Format = RGBA8; */");
        assertThrows(java.io.IOException.class, () -> PreparedShaderPack.read(zip("conflict.zip", files)));
    }
    @Test void shaderSelectionSurvivesUnknownSettingsAndRejectsPaths() throws Exception {
        Path file = temporary.resolve("kernel-shaders.properties");
        assertEquals("", ShaderConfig.load(file).selected());
        Files.writeString(file, "future_key=keep\nselected=old.zip\n");
        new ShaderConfig("new pack.zip").save(file);
        assertEquals("new pack.zip", ShaderConfig.load(file).selected());
        assertTrue(Files.readString(file).contains("future_key=keep"));
        assertThrows(IllegalArgumentException.class, () -> new ShaderConfig("../secret.zip"));
        Files.writeString(file, "selected=../secret.zip\n");
        assertThrows(java.io.IOException.class, () -> ShaderConfig.load(file));
    }
}
