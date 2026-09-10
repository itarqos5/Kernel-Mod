package dev.kernel.fabric.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Immutable startup snapshot. Saving controls the next launch; it never changes applied Mixins. */
public final class RendererConfig {
    public record Loaded(RendererConfig config, List<String> diagnostics) {}
    private final Map<RendererFeature, Boolean> values;

    private RendererConfig(Map<RendererFeature, Boolean> values) { this.values = Map.copyOf(values); }

    public static RendererConfig defaults() {
        EnumMap<RendererFeature, Boolean> values = new EnumMap<>(RendererFeature.class);
        for (RendererFeature feature : RendererFeature.values()) values.put(feature, true);
        return new RendererConfig(values);
    }

    public boolean enabled(RendererFeature feature) { return values.get(feature); }

    public RendererConfig with(RendererFeature feature, boolean enabled) {
        EnumMap<RendererFeature, Boolean> changed = new EnumMap<>(RendererFeature.class);
        changed.putAll(values); changed.put(feature, enabled);
        return new RendererConfig(changed);
    }

    public static Loaded load(Path path) {
        RendererConfig config = defaults();
        List<String> diagnostics = new ArrayList<>();
        try {
            Properties properties = read(path);
            for (RendererFeature feature : RendererFeature.values()) {
                String value = properties.getProperty(feature.key());
                if (value == null) continue;
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    diagnostics.add("Invalid value for " + feature.key() + "; that optimization is disabled.");
                }
                config = config.with(feature, value.equalsIgnoreCase("true"));
            }
        } catch (IOException | IllegalArgumentException exception) {
            for (RendererFeature feature : RendererFeature.values()) config = config.with(feature, false);
            diagnostics.add("Cannot read renderer settings; Kernel renderer optimizations are disabled: " + exception.getMessage());
        }
        return new Loaded(config, List.copyOf(diagnostics));
    }

    public void save(Path path) throws IOException {
        Path target = path.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Properties properties;
        try { properties = read(target); }
        catch (IllegalArgumentException exception) { throw new IOException("Renderer settings contain invalid property escapes", exception); }
        for (RendererFeature feature : RendererFeature.values()) properties.setProperty(feature.key(), Boolean.toString(enabled(feature)));
        Path temporary = Files.createTempFile(target.getParent(), "kernel-renderer-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(writer, "Kernel renderer settings. Changes take effect after restarting Minecraft.");
            }
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static Properties read(Path path) throws IOException {
        Properties properties = new Properties();
        if (Files.notExists(path)) return properties;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { properties.load(reader); }
        return properties;
    }

    @Override public boolean equals(Object other) { return other instanceof RendererConfig config && values.equals(config.values); }
    @Override public int hashCode() { return values.hashCode(); }
}
