package dev.kernel.client;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

final class KernelKnotClientTest {
    @Test
    void forwardsArgumentsToFabricKnotClient() throws Throwable {
        String[] arguments = {"--version", "test-version", "--demo"};

        URL main = KernelKnotClient.class.getProtectionDomain().getCodeSource().getLocation();
        URL fixtures = Path.of(System.getProperty("kernel.fallbackSmokeClasses")).toUri().toURL();
        try (var isolated = new URLClassLoader(new URL[]{main, fixtures}, ClassLoader.getPlatformClassLoader())) {
            isolated.loadClass("dev.kernel.client.KernelKnotClient").getMethod("main", String[].class)
                .invoke(null, (Object) arguments);
            Class<?> fabric = isolated.loadClass("net.fabricmc.loader.impl.launch.knot.KnotClient");
            assertArrayEquals(arguments, (String[]) fabric.getField("receivedArguments").get(null));
        }
    }
}
