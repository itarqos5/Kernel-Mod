package dev.kernel.fabric.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.knot.Knot;

/** Boots the real loader and Mixins without invoking Minecraft.main or opening a window. */
public final class VertexSortingSmoke {
    public static void main(String[] arguments) throws Exception {
        ClassLoader gameLoader = new Knot(EnvType.CLIENT).init(arguments);
        gameLoader.loadClass("dev.kernel.fabric.render.VertexSortingSmokeChecks").getMethod("run").invoke(null);
    }
}
