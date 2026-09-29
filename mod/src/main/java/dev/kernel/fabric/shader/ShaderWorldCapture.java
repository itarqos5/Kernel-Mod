package dev.kernel.fabric.shader;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LightLayer;

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

    /**
     * Reads what the world is doing to the viewer this frame.
     *
     * <p>Every value follows the rule the game applies for itself. Night vision in particular is the
     * game's own light-texture rule, conduit power included, rather than the effect alone: a pack that
     * brightens by this input should brighten exactly when the world does.
     */
    static ShaderViewerData captureViewer(Minecraft minecraft, DeltaTracker deltaTracker) {
        var level = minecraft.level;
        var player = minecraft.player;
        if (level == null || player == null)
            throw new IllegalStateException("Viewer shader inputs are unavailable outside a world");
        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);
        //? if >=26.2 {
        var camera = minecraft.gameRenderer.mainCamera();
        //? } else {
        /*var camera = minecraft.gameRenderer.getMainCamera();
        *///? }
        //? if >=1.21.11 {
        var eye = camera.blockPosition();
        //? } else {
        /*var eye = camera.getBlockPosition();
        *///? }
        var lighting = level.getLightEngine();
        int blockLight = lighting.getLayerListener(LightLayer.BLOCK).getLightValue(eye);
        int skyLight = lighting.getLayerListener(LightLayer.SKY).getLightValue(eye);
        // Newer targets resolve the sky's colour through the camera's environment attributes, the same
        // way they resolve the moon phase and the sun's angle.
        //? if >=1.21.11 {
        int sky = camera.attributeProbe().getValue(net.minecraft.world.attribute.EnvironmentAttributes.SKY_COLOR, partialTicks);
        //? } else {
        /*int sky = level.getSkyColor(camera.getPosition(), partialTicks);
        *///? }
        return new ShaderViewerData(ShaderViewerData.eyeInWater(camera.getFluidInCamera().name()),
            blend(player, MobEffects.BLINDNESS, partialTicks), blend(player, MobEffects.DARKNESS, partialTicks),
            nightVision(player, partialTicks), minecraft.options.gamma().get().floatValue(),
            blockLight, skyLight, ShaderViewerData.rgb(sky));
    }

    /** How far into or out of an effect the viewer is, which is how the game fades the effect itself. */
    private static float blend(LivingEntity viewer, Holder<MobEffect> effect, float partialTicks) {
        var active = viewer.getEffect(effect);
        return active == null ? 0.0f : Math.clamp(active.getBlendFactor(viewer, partialTicks), 0.0f, 1.0f);
    }

    /**
     * The game's own night-vision strength: the effect's scale where the effect is held, otherwise the
     * water vision conduit power grants, otherwise nothing.
     */
    private static float nightVision(net.minecraft.client.player.LocalPlayer player, float partialTicks) {
        if (player.hasEffect(MobEffects.NIGHT_VISION)) {
            //? if >=26.2 {
            return Math.clamp(GameRenderer.nightVisionScale(player, partialTicks), 0.0f, 1.0f);
            //? } else {
            /*return Math.clamp(GameRenderer.getNightVisionScale(player, partialTicks), 0.0f, 1.0f);
            *///? }
        }
        float water = player.getWaterVision();
        return water > 0.0f && player.hasEffect(MobEffects.CONDUIT_POWER) ? Math.clamp(water, 0.0f, 1.0f) : 0.0f;
    }
}
