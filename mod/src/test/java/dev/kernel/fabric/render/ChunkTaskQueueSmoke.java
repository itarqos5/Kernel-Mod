package dev.kernel.fabric.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.knot.Knot;

public final class ChunkTaskQueueSmoke {
    public static void main(String[] arguments) throws Exception {
        ClassLoader loader = new Knot(EnvType.CLIENT).init(arguments);
        loader.loadClass("dev.kernel.fabric.render.ChunkTaskQueueSmokeChecks").getMethod("run").invoke(null);
    }
}
