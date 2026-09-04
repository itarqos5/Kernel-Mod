package dev.kernel.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

final class KernelKnotClientTest {
    @Test
    void forwardsArgumentsToFabricKnotClient() throws Throwable {
        String[] arguments = {"--version", "test-version", "--demo"};

        KernelKnotClient.main(arguments);

        assertArrayEquals(arguments, net.fabricmc.loader.impl.launch.knot.KnotClient.receivedArguments);
    }
}
