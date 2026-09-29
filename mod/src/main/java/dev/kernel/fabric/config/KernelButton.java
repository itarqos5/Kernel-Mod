package dev.kernel.fabric.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.util.function.BooleanSupplier;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//? } else {
/*import net.minecraft.client.gui.GuiGraphics;
*///? }

/** Native button input, focus, tooltips and narration with Kernel's translucent white treatment. */
public final class KernelButton extends Button {
    private final BooleanSupplier selected;
    private final boolean icon;
    private Component visualLabel;
    private boolean tab;

    public KernelButton(int x, int y, int width, int height, Component label, OnPress action) {
        this(x, y, width, height, label, action, () -> false, false);
    }

    public KernelButton(int x, int y, int width, int height, Component label, OnPress action, BooleanSupplier selected, boolean icon) {
        super(x, y, width, height, label, action, DEFAULT_NARRATION);
        this.selected = selected; this.icon = icon;
    }

    public KernelButton visual(Component label) { visualLabel = label; return this; }

    /**
     * Marks this button as one of the tabs along the top of the frame.
     *
     * <p>A tab carries its accent along the edge it shares with the page below it rather than down its
     * left side, so the selected tab reads as the top of the body instead of as one more list row.
     */
    public KernelButton tab() { tab = true; return this; }

    //? if >=26.1 {
    @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    //? } elif >=1.21.11 {
    /*@Override protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
    *///? } else {
    /*@Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
    *///? }
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        graphics.fill(x, y, x + w, y + h, active ? 0xAC08090B : 0x7008090B);
        if (icon) KernelUi.icon(graphics, x + 2, y + 2, Math.min(w, h) - 4);
        if (active && (isHoveredOrFocused() || selected.getAsBoolean())) {
            graphics.fill(x, y, x + w, y + h, isHoveredOrFocused() ? 0x38FFFFFF : 0x20FFFFFF);
            if (tab) graphics.fill(x, y + h - 2, x + w, y + h, 0xFFF3F4F6);
            else graphics.fill(x, y, x + 2, y + h, 0xFFF3F4F6);
        }
        if (isFocused()) {
            graphics.fill(x, y, x + w, y + 1, 0xFFEEEEEE);
            graphics.fill(x, y + h - 1, x + w, y + h, 0xFFEEEEEE);
        }
        if (!icon) {
            var font = Minecraft.getInstance().font;
            var text = font.plainSubstrByWidth((visualLabel == null ? getMessage() : visualLabel).getString(), Math.max(1, w - 10));
            KernelUi.text(graphics, font, Component.literal(text), x + (w - font.width(text)) / 2,
                y + (h - 8) / 2, active ? 0xFFF3F4F6 : 0xFF777B80);
        }
    }
}
