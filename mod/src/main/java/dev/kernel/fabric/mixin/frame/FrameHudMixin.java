package dev.kernel.fabric.mixin.frame;

import dev.kernel.fabric.config.KernelUi;
import dev.kernel.fabric.frame.FrameSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//? } else {
/*import net.minecraft.client.gui.GuiGraphics;
*///? }
//? if >=26.2 {
import net.minecraft.client.gui.Hud;
@Mixin(Hud.class)
//? } else {
/*import net.minecraft.client.gui.Gui;
@Mixin(Gui.class)
*///? }
public abstract class FrameHudMixin {
    //? if >=26.1 {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void kernel$fps(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo callback) {
    //? } else {
    /*@Inject(method = "render", at = @At("TAIL"))
    private void kernel$fps(GuiGraphics graphics, DeltaTracker delta, CallbackInfo callback) {
    *///? }
        if (!FrameSync.enabled()) return;
        var minecraft = Minecraft.getInstance();
        //? if >=26.2 {
        var debug = minecraft.gui.hud.getDebugOverlay();
        boolean hidden = minecraft.gui.hud.isHidden();
        //? } else {
        /*var debug = minecraft.gui.getDebugOverlay();
        boolean hidden = minecraft.options.hideGui;
        *///? }
        if (hidden || debug.showDebugScreen()) return;
        String first = FrameSync.pacedLabel(), second = FrameSync.estimateLabel();
        int width = Math.max(minecraft.font.width(first), minecraft.font.width(second));
        graphics.fill(2, 2, width + 10, 26, 0x9A090A0C);
        KernelUi.text(graphics, minecraft.font, Component.literal(first), 6, 5, 0xFFFFFFFF);
        KernelUi.text(graphics, minecraft.font, Component.literal(second), 6, 16, 0xFFBABFC5);
    }
}
