package dev.kernel.fabric.config;

import com.mojang.blaze3d.platform.VideoMode;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ParticleStatus;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import dev.kernel.fabric.frame.FrameSync;
import dev.kernel.fabric.world.WorldConfig;
import dev.kernel.fabric.world.WorldFeature;
import dev.kernel.fabric.world.WorldSettings;
import dev.kernel.fabric.resource.ResourceConfig;
import dev.kernel.fabric.resource.ResourceSettings;
//? if <1.21.11 {
/*import net.minecraft.client.GraphicsStatus;
*///? }
//? if <=1.21.5 {
/*import net.minecraft.client.gui.GuiGraphics;
*///? }

/** Draft-based video controls. The native screen keeps input, narration and background lifecycle ownership. */
public final class KernelSettingsScreen extends Screen {
    private static final List<String> TABS = List.of("video", "graphics", "optimizations", "other", "shaders");
    private final Screen parent;
    private final List<VideoSetting<?>> settings = new ArrayList<>();
    private final List<RendererFeature> features = Arrays.stream(RendererFeature.values()).filter(KernelRendererSettings::supported).toList();
    private RendererConfig pending = KernelRendererSettings.saved();
    private WorldConfig pendingWorld = WorldSettings.saved();
    private ResourceConfig pendingResources = ResourceSettings.saved();
    private String tab = "video";
    private int page;
    private int pages;
    private boolean saveFailed;
    private boolean pendingFrameSync = FrameSync.enabled();
    private VideoSetting<Integer> frameLimit;
    private VideoSetting<Boolean> vsync;
    private VideoSetting<Integer> distance;
    private VideoSetting<Integer> simulation;
    private VideoSetting<CloudStatus> clouds;
    private VideoSetting<ParticleStatus> particles;

    public KernelSettingsScreen(Screen parent) { super(KernelTranslations.text("kernel.settings.title")); this.parent = parent; }

    private void createSettings() {
        var options = minecraft.options;
        var window = minecraft.getWindow();
        var monitor = window.findBestMonitor();
        List<Optional<VideoMode>> modes = new ArrayList<>(); modes.add(Optional.empty());
        if (monitor != null) {
            //? if >=26.2 {
            for (VideoMode mode : monitor.videoModes()) modes.add(Optional.of(mode));
            //? } else {
            /*for (int i = 0; i < monitor.getModeCount(); i++) modes.add(Optional.of(monitor.getMode(i)));
            *///? }
        }
        settings.add(new VideoSetting<>("video", tr("resolution"), modes, false, window.getPreferredFullscreenVideoMode(),
            mode -> mode.<Component>map(value -> Component.literal(value.getWidth() + " x " + value.getHeight() + " @ " + value.getRefreshRate()))
                .orElse(tr("desktop")), window::setPreferredFullscreenVideoMode));
        bool("video", "fullscreen", options.fullscreen());
        //? if >=26.2 {
        bool("video", "exclusive", options.exclusiveFullscreen());
        //? }
        frameLimit = integer("video", "fps", options.framerateLimit(), 10, 260, 10, value -> value == 260 ? tr("unlimited") : Component.literal(value + " fps"));
        vsync = bool("video", "vsync", options.enableVsync());
        distance = integer("video", "distance", options.renderDistance(), 2, 32, 1, value -> tr("chunks", value));
        simulation = integer("video", "simulation", options.simulationDistance(), 5, 32, 1, value -> tr("chunks", value));
        decimal("video", "entity_distance", options.entityDistanceScaling(), 0.5, 5.0, 0.25);
        //? if <1.21.11 {
        /*add("graphics", "quality", options.graphicsMode(), List.of(GraphicsStatus.FAST, GraphicsStatus.FANCY), false,
            value -> tr(value == GraphicsStatus.FAST ? "fast" : value == GraphicsStatus.FANCY ? "fancy" : "fabulous"));
        *///? }
        bool("graphics", "lighting", options.ambientOcclusion());
        clouds = add("graphics", "clouds", options.cloudStatus(), List.of(CloudStatus.OFF, CloudStatus.FAST, CloudStatus.FANCY), false,
            value -> value == CloudStatus.OFF ? CommonComponents.OPTION_OFF : tr(value == CloudStatus.FAST ? "fast" : "fancy"));
        particles = add("graphics", "particles", options.particles(), List.of(ParticleStatus.ALL, ParticleStatus.DECREASED, ParticleStatus.MINIMAL), false,
            value -> tr(value == ParticleStatus.ALL ? "all" : value == ParticleStatus.DECREASED ? "decreased" : "minimal"));
        bool("graphics", "shadows", options.entityShadows());
        integer("graphics", "mipmaps", options.mipmapLevels(), 0, 4, 1, value -> Component.literal(value.toString()));
        //? if >=1.21.11 {
        bool("graphics", "leaves", options.cutoutLeaves());
        //? }
        decimal("graphics", "brightness", options.gamma(), 0, 1, 0.05);
        integer("other", "fov", options.fov(), 30, 110, 1, value -> Component.literal(value.toString()));
        integer("other", "gui_scale", options.guiScale(), 0, Math.max(1, window.calculateScale(0, false)), 1, value -> value == 0 ? tr("auto") : Component.literal(value.toString()));
        bool("other", "bobbing", options.bobView());
        decimal("other", "screen_effects", options.screenEffectScale(), 0, 1, 0.05);
    }

    private <T> VideoSetting<T> add(String category, String key, OptionInstance<T> source, List<T> choices, boolean slider, Function<T, Component> format) {
        var setting = VideoSetting.option(category, "kernel.video." + key, source, choices, slider, format);
        settings.add(setting); return setting;
    }
    private VideoSetting<Boolean> bool(String category, String key, OptionInstance<Boolean> source) {
        return add(category, key, source, List.of(false, true), false, value -> value ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }
    private VideoSetting<Integer> integer(String category, String key, OptionInstance<Integer> source, int min, int max, int step, Function<Integer, Component> display) {
        return add(category, key, source, IntStream.iterate(min, value -> value <= max, value -> value + step).boxed().toList(), true, display);
    }
    private void decimal(String category, String key, OptionInstance<Double> source, double min, double max, double step) {
        add(category, key, source, IntStream.rangeClosed(0, (int) Math.round((max - min) / step)).mapToObj(i -> min + i * step).toList(), true,
            value -> Component.literal(Math.round(value * 100) + "%"));
    }
    private static Component tr(String key, Object... args) { return KernelTranslations.text("kernel.video." + key, args); }

    @Override protected void init() {
        if (settings.isEmpty()) createSettings();
        int totalWidth = Math.min(700, width - 24);
        int left = (width - totalWidth) / 2;
        int sidebar = Math.min(104, Math.max(76, totalWidth / 5));
        int contentX = left + sidebar + 12;
        int contentWidth = totalWidth - sidebar - 12;
        int top = 50, rowHeight = 26;
        int rows = Math.max(1, (height - top - 72) / rowHeight);
        List<VideoSetting<?>> visibleSettings = settings.stream().filter(setting -> setting.tab.equals(tab)).toList();
        WorldFeature[] worldFeatures = WorldFeature.values();
        int count = tab.equals("optimizations") ? features.size() + worldFeatures.length + 1 : visibleSettings.size() + (tab.equals("video") ? 1 : 0);
        pages = Math.max(1, (count + rows - 1) / rows); page = Math.min(page, pages - 1);
        int first = page * rows;
        addRenderableOnly((graphics, mouseX, mouseY, delta) -> {
            graphics.fill(left - 4, 10, left + totalWidth + 4, 40, 0x9008090B);
            KernelUi.icon(graphics, left + 2, 13, 24);
            KernelUi.text(graphics, font, Component.literal("K E R N E L"), left + 34, 15, 0xFFF3F4F6);
            KernelUi.text(graphics, font, tr("heading"), left + 34, 28, 0xFFAEB3B9);
            graphics.fill(contentX, 44, contentX + contentWidth, 45, 0x50FFFFFF);
            int textWidth = Math.max(20, contentWidth - 80);
            String status = KernelTranslations.text(saveFailed ? "kernel.settings.save_failed" : restartRequired()
                ? "kernel.settings.restart" : hasChanges() ? "kernel.settings.pending" : "kernel.settings.applied").getString();
            KernelUi.text(graphics, font, Component.literal(font.plainSubstrByWidth(status, totalWidth)), left, height - 43, saveFailed ? 0xFFFF9B9B : 0xFFB8BEC5);
            if (pages > 1) KernelUi.text(graphics, font, KernelTranslations.text("kernel.settings.page", page + 1, pages), contentX + contentWidth / 2 - 12, height - 64, 0xFFB8BEC5);
            if (tab.equals("other")) {
                String gpu = KernelHardwareSettings.renderer();
                KernelUi.text(graphics, font, Component.literal(font.plainSubstrByWidth(gpu, textWidth)), contentX, top + rows * rowHeight + 2, 0xFFB8BEC5);
            }
        });
        for (int i = 0; i < TABS.size(); i++) {
            String category = TABS.get(i);
            addRenderableWidget(new KernelButton(left, top + i * 26, sidebar, 24, tr("tab." + category), button -> {
                if (category.equals("shaders")) {
                    //? if >=26.2 {
                    minecraft.gui.setScreen(new dev.kernel.fabric.shader.ShaderScreen(this));
                    //? } else {
                    /*minecraft.setScreen(new dev.kernel.fabric.shader.ShaderScreen(this));
                    *///? }
                } else { tab = category; page = 0; rebuildWidgets(); }
            }, () -> tab.equals(category), false));
        }
        KernelButton recommend = addRenderableWidget(new KernelButton(left, height - 28, sidebar, 22, tr("recommended"), button -> {
            var preset = KernelHardwareSettings.recommendation();
            distance.value = preset.renderDistance(); simulation.value = preset.simulationDistance();
            clouds.value = preset.detailedClouds() ? CloudStatus.FANCY : CloudStatus.FAST;
            particles.value = preset.detailedClouds() ? ParticleStatus.ALL : ParticleStatus.DECREASED;
            saveFailed = false; rebuildWidgets();
        }));
        var preset = KernelHardwareSettings.recommendation();
        recommend.setTooltip(Tooltip.create(tr("recommendation_description", Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory() / (1024 * 1024), preset.renderDistance(), preset.simulationDistance())));
        for (int row = 0; row < rows && first + row < count; row++) {
            int y = top + row * rowHeight;
            if (tab.equals("optimizations") && first + row == features.size() + worldFeatures.length) addResourceSetting(contentX, y, contentWidth);
            else if (tab.equals("optimizations") && first + row >= features.size()) addWorldSetting(worldFeatures[first + row - features.size()], contentX, y, contentWidth);
            else if (tab.equals("optimizations")) addFeature(features.get(first + row), contentX, y, contentWidth);
            else if (tab.equals("video") && first + row == 0) addFrameSync(contentX, y, contentWidth);
            else addSetting(visibleSettings.get(first + row - (tab.equals("video") ? 1 : 0)), contentX, y, contentWidth);
        }
        if (pages > 1) {
            var previous = addRenderableWidget(new KernelButton(contentX, height - 69, 36, 18, KernelTranslations.text("kernel.settings.previous"), button -> { page--; rebuildWidgets(); }).visual(Component.literal("<")));
            previous.active = page > 0;
            previous.setTooltip(Tooltip.create(KernelTranslations.text("kernel.settings.previous")));
            var next = addRenderableWidget(new KernelButton(contentX + contentWidth - 36, height - 69, 36, 18, KernelTranslations.text("kernel.settings.next"), button -> { page++; rebuildWidgets(); }).visual(Component.literal(">")));
            next.active = page + 1 < pages;
            next.setTooltip(Tooltip.create(KernelTranslations.text("kernel.settings.next")));
        }
        int buttonWidth = Math.min(80, (contentWidth - 8) / 3), end = left + totalWidth;
        addRenderableWidget(new KernelButton(end - 3 * buttonWidth - 8, height - 28, buttonWidth, 22, CommonComponents.GUI_CANCEL, button -> onClose()));
        addRenderableWidget(new KernelButton(end - 2 * buttonWidth - 4, height - 28, buttonWidth, 22, KernelTranslations.text("kernel.settings.apply"), button -> apply(false)));
        addRenderableWidget(new KernelButton(end - buttonWidth, height - 28, buttonWidth, 22, CommonComponents.GUI_DONE, button -> apply(true)));
    }

    private void row(Component label, int x, int y, int width, int controlWidth) {
        addRenderableOnly((graphics, mouseX, mouseY, delta) -> {
            graphics.fill(x, y, x + width, y + 24, 0xAC08090B);
            KernelUi.text(graphics, font, Component.literal(font.plainSubstrByWidth(label.getString(), Math.max(10, width - controlWidth - 16))), x + 8, y + 8, 0xFFF3F4F6);
        });
    }
    private void addSetting(VideoSetting<?> setting, int x, int y, int width) {
        int controls = Math.max(100, width * 45 / 100);
        row(setting.label, x, y, width, controls);
        int controlX = x + width - controls;
        boolean managed = pendingFrameSync && (setting == frameLimit || setting == vsync);
        Tooltip tooltip = Tooltip.create(managed ? KernelTranslations.text("kernel.frame.managed") : setting.label);
        if (setting.slider && setting.choices.size() > 1) {
            var slider = addRenderableWidget(new KernelSlider(controlX, y + 1, controls, setting.position(), setting::valueText, setting::narration, value -> {
                setting.position(value); saveFailed = false;
            }));
            slider.setTooltip(tooltip); slider.active = !managed;
        } else {
            var previous = addRenderableWidget(new KernelButton(controlX, y + 1, 18, 22, setting.narration(), button -> { setting.cycle(-1); button.setMessage(setting.narration()); saveFailed = false; }).visual(Component.literal("<")));
            previous.setTooltip(tooltip); previous.active = !managed && setting.choices.size() > 1;
            addRenderableOnly((graphics, mouseX, mouseY, delta) -> {
                String value = font.plainSubstrByWidth(setting.valueText().getString(), controls - 40);
                KernelUi.text(graphics, font, Component.literal(value), controlX + (controls - font.width(value)) / 2, y + 8, 0xFFF3F4F6);
            });
            var next = addRenderableWidget(new KernelButton(x + width - 18, y + 1, 18, 22, setting.narration(), button -> { setting.cycle(1); button.setMessage(setting.narration()); saveFailed = false; }).visual(Component.literal(">")));
            next.setTooltip(tooltip); next.active = !managed && setting.choices.size() > 1;
        }
    }
    private void addFrameSync(int x, int y, int width) {
        Component label = KernelTranslations.text("kernel.frame.setting");
        row(label, x, y, width, 68);
        Component state = pendingFrameSync ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
        var button = addRenderableWidget(new KernelButton(x + width - 64, y + 1, 64, 22,
            Component.empty().append(label).append(": ").append(state), pressed -> {
                pendingFrameSync = !pendingFrameSync; saveFailed = false; rebuildWidgets();
            }, () -> pendingFrameSync, false).visual(state));
        button.setTooltip(Tooltip.create(KernelTranslations.text("kernel.frame.description")));
    }
    private void addFeature(RendererFeature feature, int x, int y, int width) {
        Component label = KernelTranslations.text(feature.translationKey());
        row(label, x, y, width, 68);
        var button = addRenderableWidget(new KernelButton(x + width - 64, y + 1, 64, 22, KernelTranslations.text("kernel.settings.value", label, featureLabel(feature)), pressed -> {
            pending = pending.with(feature, !pending.enabled(feature)); pressed.setMessage(KernelTranslations.text("kernel.settings.value", label, featureLabel(feature)));
            ((KernelButton) pressed).visual(featureLabel(feature)); saveFailed = false;
        }, () -> pending.enabled(feature), false).visual(featureLabel(feature)));
        button.setTooltip(Tooltip.create(KernelTranslations.text(feature.translationKey() + ".description").append("\n")
            .append(KernelTranslations.text("kernel.settings.current", KernelRendererSettings.enabled(feature) ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF))));
    }
    private Component featureLabel(RendererFeature feature) { return pending.enabled(feature) ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF; }
    private void addWorldSetting(WorldFeature feature, int x, int y, int width) {
        String key = "kernel.world." + feature.key();
        Component label = KernelTranslations.text(key);
        row(label, x, y, width, 68);
        boolean enabled = pendingWorld.enabled(feature);
        Component state = enabled ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
        var button = addRenderableWidget(new KernelButton(x + width - 64, y + 1, 64, 22,
            KernelTranslations.text("kernel.settings.value", label, state), pressed -> {
                pendingWorld = pendingWorld.with(feature, !enabled);
                saveFailed = false; rebuildWidgets();
            }, () -> enabled, false).visual(state));
        button.active = !WorldSettings.lithiumPresent();
        boolean active = WorldSettings.active(feature);
        button.setTooltip(Tooltip.create(KernelTranslations.text(WorldSettings.lithiumPresent() ? "kernel.world.lithium" : key + ".description")
            .append("\n").append(KernelTranslations.text("kernel.settings.current", active ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF))));
    }
    private void addResourceSetting(int x, int y, int width) {
        Component label = KernelTranslations.text("kernel.resource.readers");
        row(label, x, y, width, 68);
        boolean enabled = pendingResources.compactReaders();
        Component state = enabled ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
        var button = addRenderableWidget(new KernelButton(x + width - 64, y + 1, 64, 22,
            KernelTranslations.text("kernel.settings.value", label, state), pressed -> {
                pendingResources = new ResourceConfig(!enabled); saveFailed = false; rebuildWidgets();
            }, () -> enabled, false).visual(state));
        button.setTooltip(Tooltip.create(KernelTranslations.text("kernel.resource.readers.description").append("\n")
            .append(KernelTranslations.text("kernel.settings.current", ResourceSettings.compactReadersActive() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF))));
    }
    private boolean restartRequired() { return KernelRendererSettings.restartRequired(pending) || WorldSettings.restartRequired(pendingWorld)
        || ResourceSettings.restartRequired(pendingResources); }
    private boolean hasChanges() { return pendingFrameSync != FrameSync.enabled() || !pending.equals(KernelRendererSettings.saved())
        || !pendingWorld.equals(WorldSettings.saved()) || !pendingResources.equals(ResourceSettings.saved()) || settings.stream().anyMatch(VideoSetting::changed); }

    private void apply(boolean close) {
        try {
            // Save restart-only settings first. If that fails, leave all live video settings untouched.
            KernelRendererSettings.save(pending);
            WorldSettings.save(pendingWorld);
            ResourceSettings.save(pendingResources);
            FrameSync.save(pendingFrameSync);
            int mipmaps = minecraft.options.mipmapLevels().get();
            int scale = minecraft.options.guiScale().get();
            for (var setting : settings) setting.commit();
            minecraft.getWindow().changeFullscreenVideoMode();
            minecraft.options.save();
            if (minecraft.options.mipmapLevels().get() != mipmaps) {
                minecraft.updateMaxMipLevel(minecraft.options.mipmapLevels().get()); minecraft.delayTextureReload();
            }
            if (minecraft.options.guiScale().get() != scale) {
                //? if >=26.1 {
                minecraft.resizeGui();
                //? } else {
                /*minecraft.resizeDisplay();
                *///? }
            }
            saveFailed = false;
            if (close) onClose(); else rebuildWidgets();
        } catch (IOException | RuntimeException exception) {
            org.slf4j.LoggerFactory.getLogger("Kernel").error("Cannot apply Kernel video settings", exception); saveFailed = true;
        }
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        int nextPage = Math.clamp(page - (int) Math.signum(vertical), 0, pages - 1);
        if (nextPage != page) { page = nextPage; rebuildWidgets(); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public void onClose() {
        //? if >=26.2 {
        minecraft.gui.setScreen(parent);
        //? } else {
        /*minecraft.setScreen(parent);
        *///? }
    }
    public void showCategory(String category) {
        if (category != null) { tab = category; page = 0; }
        //? if >=26.2 {
        minecraft.gui.setScreen(this);
        //? } else {
        /*minecraft.setScreen(this);
        *///? }
    }
    // Since 1.21.6, Screen's wrapper draws the background before the screen renderer.
    //? if <=1.21.5 {
    /*@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick); super.render(graphics, mouseX, mouseY, partialTick);
    }
    *///? }
}
