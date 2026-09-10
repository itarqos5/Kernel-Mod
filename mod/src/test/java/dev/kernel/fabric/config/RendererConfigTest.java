package dev.kernel.fabric.config;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class RendererConfigTest {
    @TempDir Path directory;

    @Test void missingFileUsesDefaultsWithoutWritingAndEditsDoNotMutateTheActiveSnapshot() {
        Path path = directory.resolve("renderer.properties");
        RendererConfig initial = RendererConfig.load(path).config();
        assertEquals(RendererConfig.defaults(), initial);
        assertFalse(Files.exists(path));
        RendererConfig edited = initial.with(RendererFeature.CHUNK_QUEUE, false);
        assertTrue(initial.enabled(RendererFeature.CHUNK_QUEUE));
        assertFalse(edited.enabled(RendererFeature.CHUNK_QUEUE));
        assertNotEquals(initial, edited);
    }

    @Test void atomicRoundTripPreservesUnknownKeysAndDoesNotLeaveTemporaryFiles() throws Exception {
        Path path = directory.resolve("nested/renderer.properties");
        RendererConfig config = RendererConfig.defaults().with(RendererFeature.VERTEX, false);
        config.save(path);
        Files.writeString(path, Files.readString(path) + "\nfuture_setting=éclair\n");
        config.with(RendererFeature.QUAD_SORTING, false).save(path);
        assertEquals(config.with(RendererFeature.QUAD_SORTING, false), RendererConfig.load(path).config());
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(path)) { properties.load(reader); }
        assertEquals("éclair", properties.getProperty("future_setting"));
        try (var siblings = Files.list(path.getParent())) { assertEquals(1, siblings.count()); }
    }

    @Test void invalidSettingsDisableAffectedOptimizationsAndMalformedFilesArePreserved() throws Exception {
        Path path = directory.resolve("renderer.properties");
        Files.writeString(path, "vertex=maybe\npose=FALSE\nmodel=true\n");
        var loaded = RendererConfig.load(path);
        assertFalse(loaded.config().enabled(RendererFeature.VERTEX));
        assertFalse(loaded.config().enabled(RendererFeature.POSE));
        assertTrue(loaded.config().enabled(RendererFeature.MODEL));
        assertEquals(1, loaded.diagnostics().size());
        String malformed = "vertex=\\uZZZZ\n";
        Files.writeString(path, malformed);
        loaded = RendererConfig.load(path);
        for (RendererFeature feature : RendererFeature.values()) assertFalse(loaded.config().enabled(feature));
        assertThrows(java.io.IOException.class, () -> RendererConfig.defaults().save(path));
        assertEquals(malformed, Files.readString(path));
        assertFalse(RendererConfig.load(directory).diagnostics().isEmpty());
    }

    @Test void everyRendererMixinHasAControlAndEveryControlHasTranslatedCopy() throws Exception {
        EnumSet<RendererFeature> found = EnumSet.noneOf(RendererFeature.class);
        try (var input = getClass().getResourceAsStream("/kernel.mixins.json")) {
            var json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var name : json.getAsJsonArray("client")) {
                String mixin = name.getAsString();
                RendererFeature feature = RendererFeature.forMixin(mixin);
                if (mixin.startsWith("render.")) { assertNotNull(feature, mixin); found.add(feature); }
                else assertNull(feature, mixin);
            }
        }
        assertEquals(EnumSet.allOf(RendererFeature.class), found);
        try (var input = getClass().getResourceAsStream("/assets/kernel/lang/en_us.json")) {
            var json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            for (RendererFeature feature : RendererFeature.values()) {
                assertTrue(json.has(feature.translationKey()));
                assertTrue(json.has(feature.translationKey() + ".description"));
            }
        }
        assertTrue(RendererFeature.CHUNK_QUEUE.supports("26.2"));
        assertFalse(RendererFeature.CHUNK_UPLOAD.supports("26.2"));
        assertTrue(RendererFeature.CHUNK_UPLOAD.supports("1.21.11"));
    }
}
