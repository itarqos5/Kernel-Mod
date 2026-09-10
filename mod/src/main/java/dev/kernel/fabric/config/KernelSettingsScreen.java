package dev.kernel.fabric.config;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

//? if >=26 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//? } else {
/*import net.minecraft.client.gui.GuiGraphics;
*///? }

/** Native widgets keep keyboard focus, translated labels, tooltips and narration available. */
public final class KernelSettingsScreen extends Screen {
    private final Screen parent;
    private final List<RendererFeature> features = Arrays.stream(RendererFeature.values()).filter(KernelRendererSettings::supported).toList();
    private RendererConfig pending = KernelRendererSettings.saved();
    private int page;
    private StringWidget status;
    private boolean saveFailed;

    public KernelSettingsScreen(Screen parent) { super(Component.translatable("kernel.settings.title")); this.parent = parent; }

    @Override
    protected void init() {
        int contentWidth = Math.min(320, width - 24);
        int left = (width - contentWidth) / 2;
        addRenderableOnly(new StringWidget(left, 10, contentWidth, 20, title, font));
        addRenderableOnly(new StringWidget(left, 32, contentWidth, 12,
            Component.translatable("kernel.settings.subtitle").withStyle(ChatFormatting.GRAY), font));
        int rows = Math.max(1, (height - 136) / 24);
        int pages = Math.max(1, (features.size() + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        int first = page * rows;
        for (int row = 0; row < rows && first + row < features.size(); row++) {
            RendererFeature feature = features.get(first + row);
            Button option = addRenderableWidget(Button.builder(optionLabel(feature), button -> {
                pending = pending.with(feature, !pending.enabled(feature));
                saveFailed = false;
                button.setMessage(optionLabel(feature));
                updateStatus();
            }).bounds(left, 52 + row * 24, contentWidth, 20).build());
            option.setTooltip(Tooltip.create(Component.translatable(feature.translationKey() + ".description")
                .append("\n").append(Component.translatable("kernel.settings.current",
                    KernelRendererSettings.enabled(feature) ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF))));
        }
        Button previous = addRenderableWidget(Button.builder(Component.translatable("kernel.settings.previous"), button -> {
            page--; rebuildWidgets();
        }).bounds(left, height - 76, 80, 20).build());
        Button next = addRenderableWidget(Button.builder(Component.translatable("kernel.settings.next"), button -> {
            page++; rebuildWidgets();
        }).bounds(left + contentWidth - 80, height - 76, 80, 20).build());
        previous.active = page > 0; next.active = page + 1 < pages;
        addRenderableOnly(new StringWidget(left + 80, height - 76, contentWidth - 160, 20,
            Component.translatable("kernel.settings.page", page + 1, pages), font));
        status = addRenderableOnly(new StringWidget(left, height - 52, contentWidth, 12, Component.empty(), font));
        updateStatus();
        int buttonWidth = (contentWidth - 8) / 3;
        addRenderableWidget(Button.builder(Component.translatable("kernel.settings.defaults"), button -> {
            pending = RendererConfig.defaults(); saveFailed = false; rebuildWidgets();
        }).bounds(left, height - 28, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("kernel.settings.save"), button -> save())
            .bounds(left + buttonWidth + 4, height - 28, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
            .bounds(left + (buttonWidth + 4) * 2, height - 28, buttonWidth, 20).build());
    }

    private Component optionLabel(RendererFeature feature) {
        return Component.translatable("kernel.settings.value", Component.translatable(feature.translationKey()),
            pending.enabled(feature) ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }

    private void updateStatus() {
        status.setMessage(Component.translatable(saveFailed ? "kernel.settings.save_failed"
            : KernelRendererSettings.restartRequired(pending) ? "kernel.settings.restart" : "kernel.settings.current_launch")
            .withStyle(saveFailed ? ChatFormatting.RED : ChatFormatting.GRAY));
    }

    private void save() {
        try { KernelRendererSettings.save(pending); onClose(); }
        catch (IOException | RuntimeException exception) {
            org.slf4j.LoggerFactory.getLogger("Kernel").error("Cannot save Kernel renderer settings", exception);
            saveFailed = true; updateStatus();
        }
    }

    @Override public void onClose() {
        //? if >=26.2 {
        minecraft.gui.setScreen(parent);
        //? } else {
        /*minecraft.setScreen(parent);
        *///? }
    }

    //? if >=26 {
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractBackground(graphics, mouseX, mouseY, partialTick);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
    //? } else {
    /*@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    *///? }
}
