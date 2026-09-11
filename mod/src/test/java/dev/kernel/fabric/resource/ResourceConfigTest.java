package dev.kernel.fabric.resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ResourceConfigTest {
    @TempDir Path directory;
    @Test void missingConfigEnablesWithoutWriting() throws Exception {
        Path file = directory.resolve("resources.properties");
        assertTrue(ResourceConfig.load(file).compactReaders());
        assertFalse(Files.exists(file));
    }
    @Test void savePreservesUnknownUnicodeAndCleansTemporaryFile() throws Exception {
        Path file = directory.resolve("nested/resources.properties");
        new ResourceConfig(true).save(file);
        Files.writeString(file, Files.readString(file) + "\nfuture=éclair\n");
        new ResourceConfig(false).save(file);
        assertEquals(new ResourceConfig(false), ResourceConfig.load(file));
        assertTrue(Files.readString(file).contains("future=éclair"));
        try (var files = Files.list(file.getParent())) { assertEquals(1, files.count()); }
    }
    @Test void malformedPreferencesRemainRecoverable() throws Exception {
        Path file = directory.resolve("resources.properties");
        Files.writeString(file, "compact_readers=maybe\n");
        assertFalse(ResourceConfig.load(file).compactReaders());
        Files.writeString(file, "compact_readers=FALSE\n");
        assertFalse(ResourceConfig.load(file).compactReaders());
        String malformed = "future=\\uZZZZ\n";
        Files.writeString(file, malformed);
        assertThrows(IOException.class, () -> ResourceConfig.load(file));
        assertThrows(IOException.class, () -> new ResourceConfig(true).save(file));
        assertEquals(malformed, Files.readString(file));
        assertThrows(IOException.class, () -> ResourceConfig.load(directory));
    }
}
