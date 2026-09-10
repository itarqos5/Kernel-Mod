package dev.kernel.fabric.mixin.startup;

import dev.kernel.fabric.bootstrap.StartupWindowBridge;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//? } else {
/*import net.minecraft.client.gui.GuiGraphics;
*///? }

/** Keep reload completion/error callbacks native. Replace the visible frame and skip only the final fade. */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Shadow @Final private ReloadInstance reload;
    @Shadow private long fadeOutStart;
    @Unique private boolean kernel$completedFrame;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void kernel$begin(CallbackInfo callback) { StartupWindowBridge.beginResources(); }

    //? if >=26.1 {
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    //? } else {
    /*@Inject(method = "render", at = @At("HEAD"))
    *///? }
    private void kernel$readyFrame(CallbackInfo callback) {
        kernel$completedFrame = fadeOutStart >= 0;
        if (StartupWindowBridge.adopted() && kernel$completedFrame) {
            fadeOutStart = 0; // Completion already ran; the remaining two seconds are presentation only.
            StartupWindowBridge.finish();
        }
    }

    //? if >=26.1 {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void kernel$draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo callback) {
    //? } else {
    /*@Inject(method = "render", at = @At("TAIL"))
    private void kernel$draw(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo callback) {
    *///? }
        if (!StartupWindowBridge.adopted() || kernel$completedFrame) return;
        int[] commands = StartupWindowBridge.frame(graphics.guiWidth(), graphics.guiHeight(), reload.getActualProgress());
        if (commands == null) return;
        //? if >=1.21.6 {
        graphics.nextStratum();
        //? }
        for (int i = 1; i < commands[0]; i += 5) {
            graphics.fill(commands[i], commands[i + 1], commands[i] + commands[i + 2], commands[i + 1] + commands[i + 3], commands[i + 4]);
        }
    }
}
