package dev.kernel.fabric.mixin.config;

import dev.kernel.fabric.config.KernelButton;
import dev.kernel.fabric.config.KernelSettingsScreen;
import dev.kernel.fabric.config.KernelTranslations;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({TitleScreen.class, PauseScreen.class})
public abstract class MenuSettingsButtonMixin extends Screen {
    protected MenuSettingsButtonMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void kernel$addButton(CallbackInfo callback) {
        String optionsLabel = Component.translatable("menu.options").getString();
        Button options = children().stream().filter(Button.class::isInstance).map(Button.class::cast)
            .filter(button -> button.getMessage().getString().equals(optionsLabel)).findFirst().orElse(null);
        if (options == null || options.getWidth() < 60) return;
        int x = options.getX() - 24, y = options.getY();
        final int left = x;
        boolean occupied = x < 4 || children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .anyMatch(widget -> widget.visible && widget.getX() < left + 20 && widget.getRight() > left
                && widget.getY() < y + 20 && widget.getBottom() > y);
        if (occupied) {
            // Preserve neighboring language/accessibility/mod buttons by taking space from Options itself.
            x = options.getX(); options.setX(x + 24); options.setWidth(options.getWidth() - 24);
        }
        KernelButton button = addRenderableWidget(new KernelButton(x, y, 20, 20, KernelTranslations.text("kernel.settings.open"), pressed -> {
            //? if >=26.2 {
            minecraft.gui.setScreen(new KernelSettingsScreen(this));
            //? } else {
            /*minecraft.setScreen(new KernelSettingsScreen(this));
            *///? }
        }, () -> false, true));
        button.setTooltip(Tooltip.create(KernelTranslations.text("kernel.settings.open")));
    }
}
