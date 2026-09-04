package io.github.itarqos5.kernel.agent;

import java.lang.instrument.Instrumentation;

/**
 * Early JVM entry point for Kernel.
 *
 * <p>The initial scaffold deliberately installs no transformers and changes no JVM state.</p>
 */
public final class KernelAgent {
    private KernelAgent() {
    }

    public static void premain(String arguments, Instrumentation instrumentation) {
        // Intentionally empty until bootstrap behavior is explicitly approved.
    }

    public static void agentmain(String arguments, Instrumentation instrumentation) {
        // Intentionally empty until attach behavior is explicitly approved.
    }
}
