package dev.kernel.client;

import java.util.Arrays;

/** No ASM or real Fabric dependency is on this smoke process's classpath. */
public final class AgentFallbackSmoke {
    public static void main(String[] arguments) throws Throwable {
        var active = KernelAgent.class.getDeclaredField("active");
        active.setAccessible(true);
        if (active.getBoolean(null)) throw new AssertionError("Agent should be inactive without ASM");
        String[] launchArguments = {"--demo", "value with spaces", "--version=test", ""};
        KernelKnotClient.main(launchArguments);
        if (!Arrays.equals(launchArguments, net.fabricmc.loader.impl.launch.knot.KnotClient.receivedArguments)) {
            throw new AssertionError("Fallback changed Fabric launch arguments");
        }
        System.out.println("Kernel agent dependency fallback passed on Java " + Runtime.version().feature());
    }
}
