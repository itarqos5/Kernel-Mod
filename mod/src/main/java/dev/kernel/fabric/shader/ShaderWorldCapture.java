package dev.kernel.fabric.shader;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;

/** Reads live native values on the world-rendering thread only when a pack requests them. */
final class ShaderWorldCapture {
    private ShaderWorldCapture() {}

    static ShaderWorldData capture(Minecraft minecraft, DeltaTracker deltaTracker) {
        var level = minecraft.level;
        if (level == null) throw new IllegalStateException("World shader inputs are unavailable outside a world");
        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);
        //? if >=26.1 {
        long time = level.getOverworldClockTime();
        //? } else {
        /*long time = level.getDayTime();
        *///? }
        //? if >=26.2 {
        var camera = minecraft.gameRenderer.mainCamera();
        //? } else {
        /*var camera = minecraft.gameRenderer.getMainCamera();
        *///? }
        //? if >=1.21.11 {
        int moonPhase = camera.attributeProbe().getValue(net.minecraft.world.attribute.EnvironmentAttributes.MOON_PHASE, partialTicks).index();
        //? } else {
        /*int moonPhase = level.getMoonPhase();
        *///? }
        // The angle the game turns its sky by. Newer targets expose it as a camera environment
        // attribute, the same way they expose the moon phase, rather than as a query on the level.
        //? if >=1.21.11 {
        float celestialAngle = camera.attributeProbe().getValue(net.minecraft.world.attribute.EnvironmentAttributes.SUN_ANGLE, partialTicks);
        //? } else {
        /*float celestialAngle = level.getTimeOfDay(partialTicks);
        *///? }
        return ShaderWorldData.from(time, moonPhase, level.getRainLevel(partialTicks), level.getThunderLevel(partialTicks),
            celestialAngle);
    }
}
