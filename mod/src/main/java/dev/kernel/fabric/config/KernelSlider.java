package dev.kernel.fabric.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//? } else {
/*import net.minecraft.client.gui.GuiGraphics;
*///? }

final class KernelSlider extends AbstractSliderButton {
    private final Supplier<Component> valueText;
    private final Supplier<Component> narration;
    private final DoubleConsumer change;

    KernelSlider(int x, int y, int width, double value, Supplier<Component> valueText, Supplier<Component> narration, DoubleConsumer change) {
        super(x, y, width, 22, narration.get(), value);
        this.valueText = valueText; this.narration = narration; this.change = change;
    }
    @Override protected void updateMessage() { if (narration != null) setMessage(narration.get()); }
    @Override protected void applyValue() { change.accept(value); updateMessage(); }

    //? if >=26.1 {
    @Override public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    //? } else {
    /*@Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
    *///? }
        int x = getX(), y = getY(), w = getWidth();
        if (isHoveredOrFocused()) graphics.fill(x, y, x + w, y + getHeight(), 0x28FFFFFF);
        graphics.fill(x + 5, y + 18, x + w - 5, y + 19, 0xFF55585B);
        int handle = x + 5 + (int) Math.round(value * (w - 10));
        graphics.fill(x + 5, y + 18, handle, y + 19, 0xFFF3F4F6);
        graphics.fill(handle - 1, y + 16, handle + 1, y + 21, 0xFFF3F4F6);
        var font = Minecraft.getInstance().font;
        Component label = Component.literal(font.plainSubstrByWidth(valueText.get().getString(), w - 10));
        KernelUi.text(graphics, font, label, x + (w - font.width(label)) / 2, y + 4, 0xFFF3F4F6);
    }
}
