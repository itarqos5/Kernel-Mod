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
