package dev.kernel.fabric.resource;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.knot.Knot;

public final class ResourceReaderSmoke {
    public static void main(String[] arguments) throws Exception {
        ClassLoader loader = new Knot(EnvType.CLIENT).init(arguments);
        loader.loadClass("dev.kernel.fabric.resource.ResourceReaderSmokeChecks").getMethod("run").invoke(null);
    }
}
