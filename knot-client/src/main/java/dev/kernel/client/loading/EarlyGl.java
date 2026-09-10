package dev.kernel.client.loading;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.JNI;

/** Five context-local GL calls, without initializing a second LWJGL OpenGL dispatch table in the JVM. */
final class EarlyGl {
    private static final int SCISSOR_TEST = 0x0C11, COLOR_BUFFER_BIT = 0x4000;
    private final long enable = address("glEnable"), disable = address("glDisable");
    private final long scissor = address("glScissor"), clearColor = address("glClearColor"), clear = address("glClear");

    private static long address(String name) {
        long pointer = GLFW.glfwGetProcAddress(name);
        if (pointer == 0) throw new IllegalStateException("Early context does not expose " + name);
        return pointer;
    }

    void draw(int[] commands, int height) {
        // OpenGL uses APIENTRY (__stdcall on Windows), hence JNI.call rather than JNI.invoke.
        JNI.callV(SCISSOR_TEST, enable);
        for (int i = 1; i < commands[0]; i += 5) {
            int color = commands[i + 4];
            JNI.callV(commands[i], height - commands[i + 1] - commands[i + 3], commands[i + 2], commands[i + 3], scissor);
            JNI.callV((color >>> 16 & 255) / 255F, (color >>> 8 & 255) / 255F, (color & 255) / 255F, 1F, clearColor);
            JNI.callV(COLOR_BUFFER_BIT, clear);
        }
        JNI.callV(SCISSOR_TEST, disable);
        JNI.callV(0F, 0F, 0F, 0F, clearColor);
    }
}
