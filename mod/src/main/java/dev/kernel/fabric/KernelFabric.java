package dev.kernel.fabric;

import dev.kernel.fabric.bootstrap.KnotClientInstaller;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Fabric entry point for Kernel.
 *
 * <p>Renderer Mixins are applied before this entry point. Startup also attempts to install Kernel's launcher
 * handoff for the next launch.</p>
 */
public final class KernelFabric implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Kernel");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Kernel renderer optimizations active: allocation-reduced pose, model, vertex, baked-quad, and block-face paths.");

        try {
            KnotClientInstaller.InstallResult result = KnotClientInstaller.installForCurrentLaunch();

            switch (result.outcome()) {
                case INSTALLED -> LOGGER.info("Kernel Knot Client installed. It will run before Fabric on the next launch.");
                case ALREADY_INSTALLED -> LOGGER.debug("Kernel Knot Client is already installed for this launch profile.");
                case UNSUPPORTED_LAUNCHER -> LOGGER.warn("Kernel Knot Client was not installed: {}", result.detail());
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Kernel could not install its Knot Client. Minecraft will continue without the early bootstrap.", exception);
        }
    }
}
