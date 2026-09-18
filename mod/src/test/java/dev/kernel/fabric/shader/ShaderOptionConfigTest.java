package dev.kernel.fabric.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ShaderOptionConfigTest {
    @TempDir Path temporary;

    @Test void valuesSurviveASaveAndReload() throws Exception {
        Path file = ShaderOptionConfig.file(temporary, "BSL_v8.zip");
        assertNotNull(file);
        assertEquals("bsl_v8.txt", file.getFileName().toString());
        new ShaderOptionConfig(Map.of("BLOOM", "false", "SHADOW_QUALITY", "4")).save(file);
        assertEquals(Map.of("BLOOM", "false", "SHADOW_QUALITY", "4"), ShaderOptionConfig.load(file).values());
    }

    @Test void eachPackOwnsItsOwnFileAndUnusableNamesOwnNone() {
        assertNotEquals(ShaderOptionConfig.file(temporary, "one.zip"), ShaderOptionConfig.file(temporary, "two.zip"));
        assertEquals("complementary.reimagined.txt", ShaderOptionConfig.file(temporary, "Complementary Reimagined.zip").getFileName().toString());
        assertNull(ShaderOptionConfig.file(temporary, "....zip"));
        assertNull(ShaderOptionConfig.file(temporary, ".zip"));
    }

    @Test void malformedAndUnreadableFilesReadAsNoStoredValues() throws Exception {
        Path file = temporary.resolve("broken.txt");
        Files.writeString(file, "# comment\nBLOOM\n=4\nBAD NAME=1\nGOOD=2\nINJECT=va lue\n9START=1\n");
        assertEquals(Map.of("GOOD", "2"), ShaderOptionConfig.load(file).values());
        assertEquals(Map.of(), ShaderOptionConfig.load(temporary.resolve("absent.txt")).values());
        assertEquals(Map.of(), ShaderOptionConfig.load(null).values());
    }

    @Test void theFirstValueForANameWinsSoALaterDuplicateCannotOverrideIt() throws Exception {
        Path file = temporary.resolve("duplicate.txt");
        Files.writeString(file, "BLOOM=true\nBLOOM=false\n");
        assertEquals(Map.of("BLOOM", "true"), ShaderOptionConfig.load(file).values());
    }

    @Test void withAndWithoutProduceIndependentValues() {
        var base = ShaderOptionConfig.empty();
        var updated = base.with("BLOOM", "false");
        assertTrue(base.values().isEmpty());
        assertEquals(Map.of("BLOOM", "false"), updated.values());
        assertEquals(Map.of(), updated.without("BLOOM").values());
        assertSame(updated, updated.without("ABSENT"));
    }

    @Test void savingWithoutAUsableFileFails() {
        assertThrows(java.io.IOException.class, () -> ShaderOptionConfig.empty().save(null));
    }
}
