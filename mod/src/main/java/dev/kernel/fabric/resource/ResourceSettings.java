package dev.kernel.fabric.resource;

import java.io.IOException;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class ResourceSettings {
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("kernel-resources.properties");
    private static final ResourceConfig ACTIVE = load();
    private static ResourceConfig saved = ACTIVE;
    private ResourceSettings() {}
    private static ResourceConfig load() {
        try { return ResourceConfig.load(PATH); }
        catch (IOException | RuntimeException exception) {
            org.slf4j.LoggerFactory.getLogger("Kernel").warn("Cannot read resource settings; leaving resource optimizations disabled", exception);
            return new ResourceConfig(false);
        }
    }
    public static boolean compactReadersActive() { return ACTIVE.compactReaders(); }
    public static ResourceConfig saved() { return saved; }
    public static boolean restartRequired(ResourceConfig draft) { return !ACTIVE.equals(draft); }
    public static void save(ResourceConfig config) throws IOException { config.save(PATH); saved = config; }
}
