package dev.kernel.fabric.config;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//? } else {
/*import net.minecraft.client.gui.GuiGraphics;
*///? }
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//? } else {
/*import net.minecraft.resources.ResourceLocation;
*///? }
//? if >=1.21.6 {
import net.minecraft.client.renderer.RenderPipelines;
//? } else {
/*import net.minecraft.client.renderer.RenderType;
*///? }

/** Small native drawing adapter. The icon is owned by TextureManager, including shutdown cleanup. */
public final class KernelUi {
    //? if >=1.21.11 {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath("kernel", "dynamic/settings-icon");
    //? } else {
    /*private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("kernel", "dynamic/settings-icon");
    *///? }
    private static boolean iconAttempted;
    private static boolean iconReady;
    private static int iconWidth;
    private static int iconHeight;

    private KernelUi() {}

    /**
     * Splits plain text into lines that fit a width.
     *
     * <p>This uses only font measurement so that it behaves the same on every supported version, where
     * the drawing interfaces differ. Text past {@code maxLines} is dropped with an ellipsis.
     */
    public static java.util.List<String> wrap(Font font, String text, int width, int maxLines) {
        var lines = new java.util.ArrayList<String>();
        if (width <= 0 || maxLines <= 0) return lines;
        String remaining = text.strip();
        while (!remaining.isEmpty() && lines.size() < maxLines) {
            if (font.width(remaining) <= width) { lines.add(remaining); return lines; }
            String fitted = font.plainSubstrByWidth(remaining, width);
            if (fitted.isEmpty()) fitted = remaining.substring(0, 1);
            int wrap = lines.size() + 1 < maxLines ? fitted.lastIndexOf(' ') : -1;
            // Only break on a space when one is far enough in that the line is not left nearly empty.
            int cut = wrap > fitted.length() / 3 ? wrap : fitted.length();
            if (lines.size() + 1 == maxLines) {
                String last = font.plainSubstrByWidth(remaining, Math.max(1, width - font.width("…")));
                lines.add(last + "…");
                return lines;
            }
            lines.add(remaining.substring(0, cut).strip());
            remaining = remaining.substring(cut).strip();
        }
        return lines;
    }

    //? if >=26.1 {
    public static void text(GuiGraphicsExtractor graphics, Font font, Component label, int x, int y, int color) {
        graphics.text(font, label, x, y, color);
    }
    public static void icon(GuiGraphicsExtractor graphics, int x, int y, int size) {
    //? } else {
    /*public static void text(GuiGraphics graphics, Font font, Component label, int x, int y, int color) {
        graphics.drawString(font, label, x, y, color);
    }
    public static void icon(GuiGraphics graphics, int x, int y, int size) {
    *///? }
        if (!iconAttempted) {
            iconAttempted = true;
            try (var input = KernelUi.class.getResourceAsStream("/assets/kernel/icon.png")) {
                if (input == null) throw new java.io.IOException("Missing Kernel icon");
                NativeImage pixels = NativeImage.read(input);
                iconWidth = pixels.getWidth(); iconHeight = pixels.getHeight();
                //? if >=1.21.5 {
                DynamicTexture texture = new DynamicTexture(() -> "Kernel settings icon", pixels);
                //? } else {
                /*DynamicTexture texture = new DynamicTexture(pixels);
                *///? }
                Minecraft.getInstance().getTextureManager().register(ICON, texture);
                iconReady = true;
            } catch (Exception exception) {
                org.slf4j.LoggerFactory.getLogger("Kernel").warn("Cannot load Kernel settings icon", exception);
            }
        }
        if (iconReady) {
            //? if >=1.21.6 {
            graphics.blit(RenderPipelines.GUI_TEXTURED, ICON, x, y, 0, 0, size, size, iconWidth, iconHeight, iconWidth, iconHeight);
            //? } else {
            /*graphics.blit(RenderType::guiTextured, ICON, x, y, 0, 0, size, size, iconWidth, iconHeight, iconWidth, iconHeight);
            *///? }
        } else {
            text(graphics, Minecraft.getInstance().font, Component.literal("K"), x + size / 2 - 3, y + size / 2 - 4, 0xFFFFFFFF);
        }
    }
}
