package dev.kernel.fabric.world;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.knot.Knot;

public final class WorldOptimizationSmoke {
    public static void main(String[] arguments) throws Exception {
        ClassLoader loader = new Knot(EnvType.CLIENT).init(arguments);
        loader.loadClass("dev.kernel.fabric.world.WorldOptimizationSmokeChecks").getMethod("run").invoke(null);
    }
}
