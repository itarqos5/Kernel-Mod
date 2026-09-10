package dev.kernel.fabric.config;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public final class KernelRendererSettings {
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("kernel-renderer.properties");
    private static final RendererConfig.Loaded LOADED = RendererConfig.load(PATH);
    private static final RendererConfig ACTIVE = LOADED.config();
    private static RendererConfig saved = ACTIVE;
    private static final String GAME_VERSION = FabricLoader.getInstance().getModContainer("minecraft")
        .orElseThrow().getMetadata().getVersion().getFriendlyString();

    static {
        for (String message : LOADED.diagnostics()) LoggerFactory.getLogger("Kernel").warn(message);
    }

    private KernelRendererSettings() {}
    public static RendererConfig active() { return ACTIVE; }
    public static RendererConfig saved() { return saved; }
    public static boolean supported(RendererFeature feature) { return feature.supports(GAME_VERSION); }
    public static boolean enabled(RendererFeature feature) { return supported(feature) && ACTIVE.enabled(feature); }
    public static boolean restartRequired(RendererConfig config) {
        for (RendererFeature feature : RendererFeature.values()) {
            if (supported(feature) && config.enabled(feature) != ACTIVE.enabled(feature)) return true;
        }
        return false;
    }
    public static void save(RendererConfig config) throws IOException { config.save(PATH); saved = config; }
}
