package dev.kernel.fabric.world;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Path;

public final class WorldSettings {
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("kernel-world.properties");
    private static final WorldConfig ACTIVE = load();
    private static final boolean LITHIUM = FabricLoader.getInstance().isModLoaded("lithium");
    private static WorldConfig saved = ACTIVE;
    private WorldSettings() {}
    private static WorldConfig load() {
        try { return WorldConfig.load(PATH); }
        catch (IOException | RuntimeException exception) {
            org.slf4j.LoggerFactory.getLogger("Kernel").warn("Cannot read world settings; leaving world optimizations disabled", exception);
            return new WorldConfig(false, false);
        }
    }
    public static boolean biomeOffsetsActive() { return ACTIVE.biomeOffsets() && !LITHIUM; }
    public static boolean noiseSlicesActive() { return ACTIVE.noiseSlices() && !LITHIUM; }
    public static boolean lithiumPresent() { return LITHIUM; }
    public static WorldConfig saved() { return saved; }
    public static boolean restartRequired(WorldConfig draft) { return !LITHIUM && !draft.equals(ACTIVE); }
    public static void save(WorldConfig config) throws IOException { config.save(PATH); saved = config; }
}
