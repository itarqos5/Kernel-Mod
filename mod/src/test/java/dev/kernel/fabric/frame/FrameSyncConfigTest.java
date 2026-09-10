package dev.kernel.fabric.frame;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

final class FrameSyncConfigTest {
    @TempDir Path directory;
    @Test void defaultsOnAndPreservesOtherDisplayPreferences() throws Exception {
        Path path = directory.resolve("display.properties");
        assertTrue(FrameSyncConfig.load(path).enabled());
        Files.writeString(path, "future_setting=keep\nframe_sync=false\n");
        assertFalse(FrameSyncConfig.load(path).enabled());
        new FrameSyncConfig(true).save(path);
        assertTrue(FrameSyncConfig.load(path).enabled());
        assertTrue(Files.readString(path).contains("future_setting=keep"));
    }
    @Test void malformedSettingsAreNotOverwritten() throws Exception {
        Path path = directory.resolve("display.properties");
        Files.writeString(path, "frame_sync=invalid\n");
        assertFalse(FrameSyncConfig.load(path).enabled());
        String malformed = "unrelated=\\uZZZZ\n";
        Files.writeString(path, malformed);
        assertThrows(IOException.class, () -> new FrameSyncConfig(true).save(path));
        assertEquals(malformed, Files.readString(path));
    }
}
