package net.fabricmc.loader.impl.launch.knot;

/** Unsigned forwarding fixture, isolated from the real signed Fabric dependency. */
public final class KnotClient {
    public static String[] receivedArguments;

    private KnotClient() {
    }

    public static void main(String[] arguments) {
        receivedArguments = arguments;
    }
}
