package dev.kernel.fabric.mixin.config;

import dev.kernel.fabric.config.KernelSettingsScreen;
import dev.kernel.fabric.config.KernelTranslations;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sends Video Settings to Kernel's own page instead of Minecraft's.
 *
 * <p>The redirect happens as the native page starts filling its option list, which is late enough that
 * the screen is already the current one and early enough that nothing has been drawn. Minecraft assigns
 * {@code screen} before calling {@code init}, so replacing it from inside {@code init} sticks; the
 * abandoned page finishes building widgets nobody sees.
 *
 * <p>The native page is not removed from the game. Kernel's Other tab offers it, and that row asks for
 * it first, so exactly one opening survives the redirect. Without that one-shot the page would be
 * unreachable, and it still owns settings Kernel does not mirror.
 */
@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsScreenMixin extends OptionsSubScreen {
    protected VideoSettingsScreenMixin(Screen parent, Options options, Component title) { super(parent, options, title); }

    @Inject(method = "addOptions", at = @At("HEAD"), cancellable = true)
    private void kernel$openKernelInstead(CallbackInfo callback) {
        if (KernelSettingsScreen.claimNativeVideoRequest()) return;
        // The parent is this page's own parent, not this page, so Done returns where Video Settings
        // was opened from rather than to a page the player never saw.
        var kernel = new KernelSettingsScreen(lastScreen);
        //? if >=26.2 {
        minecraft.gui.setScreen(kernel);
        //? } else {
        /*minecraft.setScreen(kernel);
        *///? }
        callback.cancel();
    }

    @Inject(method = "addOptions", at = @At("TAIL"))
    private void kernel$addSettings(CallbackInfo callback) {
        list.addSmall(Button.builder(KernelTranslations.text("kernel.settings.open"), button -> {
            //? if >=26.2 {
            minecraft.gui.setScreen(new KernelSettingsScreen(this));
            //? } else {
            /*minecraft.setScreen(new KernelSettingsScreen(this));
            *///? }
        }).build(), null);
    }
}
