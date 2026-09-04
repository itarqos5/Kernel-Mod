package io.github.itarqos5.kernel.fabric;

import io.github.itarqos5.kernel.fabric.bootstrap.KnotClientInstaller;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Fabric entry point for Kernel.
 *
 * <p>The Minecraft-facing optimization layer remains empty. At startup, Kernel only attempts to install its
 * launcher handoff for the next launch.</p>
 */
public final class KernelFabric implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Kernel");

    @Override
    public void onInitializeClient() {
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
