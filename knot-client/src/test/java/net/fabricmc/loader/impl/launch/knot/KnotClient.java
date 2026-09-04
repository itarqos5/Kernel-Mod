package net.fabricmc.loader.impl.launch.knot;

public final class KnotClient {
    public static String[] receivedArguments;

    private KnotClient() {
    }

    public static void main(String[] arguments) {
        receivedArguments = arguments;
    }
}
