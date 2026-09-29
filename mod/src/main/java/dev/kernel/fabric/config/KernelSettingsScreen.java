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
import java.util.LinkedHashMap;
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
    /** Index of the first list entry drawn, so every placed row is whole and none is clipped. */
    private int scroll;
    private int visibleEntries = 1;
    private int entryCount;
    private boolean saveFailed;
    private boolean pendingFrameSync = FrameSync.enabled();
    private Component detailTitle = Component.empty();
    private Component detailText = Component.empty();
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
        settings.add(new VideoSetting<>("video", "display", tr("resolution"), VideoSetting.description("kernel.video.resolution"),
            modes, false, window.getPreferredFullscreenVideoMode(),
            mode -> mode.<Component>map(value -> Component.literal(value.getWidth() + " x " + value.getHeight() + " @ " + value.getRefreshRate()))
                .orElse(tr("desktop")), window::setPreferredFullscreenVideoMode));
        bool("video", "display", "fullscreen", options.fullscreen());
        //? if >=26.2 {
        bool("video", "display", "exclusive", options.exclusiveFullscreen());
        //? }
        frameLimit = integer("video", "pacing", "fps", options.framerateLimit(), 10, 260, 10, value -> value == 260 ? tr("unlimited") : Component.literal(value + " fps"));
        vsync = bool("video", "pacing", "vsync", options.enableVsync());
        distance = integer("video", "distance", "distance", options.renderDistance(), 2, 32, 1, value -> tr("chunks", value));
        simulation = integer("video", "distance", "simulation", options.simulationDistance(), 5, 32, 1, value -> tr("chunks", value));
        decimal("video", "distance", "entity_distance", options.entityDistanceScaling(), 0.5, 5.0, 0.25);
        //? if <1.21.11 {
        /*add("graphics", "quality", "quality", options.graphicsMode(), List.of(GraphicsStatus.FAST, GraphicsStatus.FANCY), false,
            value -> tr(value == GraphicsStatus.FAST ? "fast" : value == GraphicsStatus.FANCY ? "fancy" : "fabulous"));
        *///? }
        bool("graphics", "quality", "lighting", options.ambientOcclusion());
        integer("graphics", "quality", "mipmaps", options.mipmapLevels(), 0, 4, 1, value -> Component.literal(value.toString()));
        //? if >=1.21.11 {
        bool("graphics", "quality", "leaves", options.cutoutLeaves());
        //? }
        clouds = add("graphics", "detail", "clouds", options.cloudStatus(), List.of(CloudStatus.OFF, CloudStatus.FAST, CloudStatus.FANCY), false,
            value -> value == CloudStatus.OFF ? CommonComponents.OPTION_OFF : tr(value == CloudStatus.FAST ? "fast" : "fancy"));
        particles = add("graphics", "detail", "particles", options.particles(), List.of(ParticleStatus.ALL, ParticleStatus.DECREASED, ParticleStatus.MINIMAL), false,
            value -> tr(value == ParticleStatus.ALL ? "all" : value == ParticleStatus.DECREASED ? "decreased" : "minimal"));
        bool("graphics", "detail", "shadows", options.entityShadows());
        decimal("graphics", "appearance", "brightness", options.gamma(), 0, 1, 0.05);
        integer("other", "view", "fov", options.fov(), 30, 110, 1, value -> Component.literal(value.toString()));
        bool("other", "view", "bobbing", options.bobView());
        decimal("other", "view", "screen_effects", options.screenEffectScale(), 0, 1, 0.05);
        integer("other", "interface", "gui_scale", options.guiScale(), 0, Math.max(1, window.calculateScale(0, false)), 1, value -> value == 0 ? tr("auto") : Component.literal(value.toString()));
    }

    private <T> VideoSetting<T> add(String category, String section, String key, OptionInstance<T> source, List<T> choices, boolean slider, Function<T, Component> format) {
        var setting = VideoSetting.option(category, section, "kernel.video." + key, source, choices, slider, format);
        settings.add(setting); return setting;
    }
    private VideoSetting<Boolean> bool(String category, String section, String key, OptionInstance<Boolean> source) {
        return add(category, section, key, source, List.of(false, true), false, value -> value ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }
    private VideoSetting<Integer> integer(String category, String section, String key, OptionInstance<Integer> source, int min, int max, int step, Function<Integer, Component> display) {
        return add(category, section, key, source, IntStream.iterate(min, value -> value <= max, value -> value + step).boxed().toList(), true, display);
    }
    private void decimal(String category, String section, String key, OptionInstance<Double> source, double min, double max, double step) {
        add(category, section, key, source, IntStream.rangeClosed(0, (int) Math.round((max - min) / step)).mapToObj(i -> min + i * step).toList(), true,
            value -> Component.literal(Math.round(value * 100) + "%"));
    }
    private static Component tr(String key, Object... args) { return KernelTranslations.text("kernel.video." + key, args); }

    /** Places one row of the list once its position inside the viewport is known. */
    @FunctionalInterface private interface Placer { void place(int x, int y, int width); }

    /** A section heading, or a control row together with the text shown while it is hovered. */
    private record Entry(Component label, Component description, Placer placer) {
        boolean heading() { return placer == null; }
        int height() { return placer == null ? 18 : 24; }
    }

    private List<Entry> entries() {
        var entries = new ArrayList<Entry>();
        if (tab.equals("optimizations")) {
            entries.add(heading("renderer"));
            for (var feature : features) entries.add(featureEntry(feature));
            entries.add(heading("world"));
            for (var feature : WorldFeature.values()) entries.add(worldEntry(feature));
            entries.add(heading("resources"));
            entries.add(resourceEntry());
            return entries;
        }
        var sections = new LinkedHashMap<String, List<Entry>>();
        if (tab.equals("video")) sections.computeIfAbsent("pacing", ignored -> new ArrayList<>()).add(frameSyncEntry());
        for (var setting : settings) {
            if (!setting.tab.equals(tab)) continue;
            sections.computeIfAbsent(setting.section, ignored -> new ArrayList<>()).add(settingEntry(setting));
        }
        for (var section : sections.entrySet()) {
            entries.add(heading(section.getKey()));
            entries.addAll(section.getValue());
        }
        return entries;
    }

    private Entry heading(String key) { return new Entry(tr("section." + key), Component.empty(), null); }

    @Override protected void init() {
        if (settings.isEmpty()) createSettings();
        KernelLayout layout = KernelLayout.of(width, height);
        int totalWidth = layout.width();
        int left = layout.left();
        int listX = left;
        int scrollbarWidth = layout.trackWidth();
        int listWidth = layout.listWidth();
        int listTop = layout.contentTop();
        int actionsY = layout.actionsY();
        int listBottom = layout.contentBottom();

        var entries = entries();
        entryCount = entries.size();
        // Fit as many whole entries as the viewport allows, then clamp the first index to what remains.
        // Headings are shorter than controls, so clamping can change how many entries fit; measuring a
        // second time from the clamped position keeps the last row inside the viewport.
        int available = Math.max(24, listBottom - listTop);
        for (int pass = 0; pass < 2; pass++) {
            int used = 0, fits = 0;
            for (int index = Math.min(scroll, Math.max(0, entryCount - 1)); index < entryCount; index++) {
                if (used + entries.get(index).height() > available) break;
                used += entries.get(index).height(); fits++;
            }
            visibleEntries = Math.max(1, fits);
            scroll = Math.clamp(scroll, 0, Math.max(0, entryCount - visibleEntries));
        }

        detailTitle = Component.empty(); detailText = Component.empty();
        addRenderableOnly((graphics, mouseX, mouseY, delta) -> {
            // Renderables draw in the order they were added, so clearing here lets the rows below
            // claim the detail panel for this frame and lets it empty again once the cursor leaves.
            detailTitle = Component.empty(); detailText = Component.empty();
            graphics.fill(left - 4, layout.headerTop(), left + totalWidth + 4, layout.headerBottom(), 0x9008090B);
            graphics.fill(left - 4, layout.headerBottom() - 1, left + totalWidth + 4, layout.headerBottom(), 0x30FFFFFF);
            KernelUi.icon(graphics, left + 2, layout.headerTop() + 4, 26);
            KernelUi.text(graphics, font, Component.literal("K E R N E L"), left + 36, layout.headerTop() + 7, 0xFFF3F4F6);
            KernelUi.text(graphics, font, tr("tab." + tab), left + 36, layout.headerTop() + 21, 0xFFAEB3B9);
        });

        for (int i = 0; i < TABS.size(); i++) {
            String category = TABS.get(i);
            var button = addRenderableWidget(new KernelButton(layout.tabX(i, TABS.size()), layout.tabTop(),
                layout.tabWidth(i, TABS.size()), layout.tabHeight(), tr("tab." + category), pressed -> {
                if (category.equals("shaders")) {
                    //? if >=26.2 {
                    minecraft.gui.setScreen(new dev.kernel.fabric.shader.ShaderScreen(this));
                    //? } else {
                    /*minecraft.setScreen(new dev.kernel.fabric.shader.ShaderScreen(this));
                    *///? }
                } else { tab = category; scroll = 0; rebuildWidgets(); }
            }, () -> tab.equals(category), false).tab());
            // Shader packs are written for OpenGL and have no Vulkan form, so the page is closed rather
            // than opened onto a renderer that could never run anything listed there.
            if (category.equals("shaders") && !dev.kernel.fabric.shader.ShaderBackend.supported()) {
                button.active = false;
                button.setTooltip(Tooltip.create(KernelTranslations.text("kernel.shaders.backend",
                    dev.kernel.fabric.shader.ShaderBackend.name())));
            }
        }
        int recommendWidth = Math.clamp(font.width(tr("recommended")) + 12, 56, 96);
        KernelButton recommend = addRenderableWidget(new KernelButton(left, actionsY, recommendWidth, layout.actionHeight(), tr("recommended"), button -> {
            var preset = KernelHardwareSettings.recommendation();
            distance.value = preset.renderDistance(); simulation.value = preset.simulationDistance();
            clouds.value = preset.detailedClouds() ? CloudStatus.FANCY : CloudStatus.FAST;
            particles.value = preset.detailedClouds() ? ParticleStatus.ALL : ParticleStatus.DECREASED;
            saveFailed = false; rebuildWidgets();
        }));
        var preset = KernelHardwareSettings.recommendation();
        recommend.setTooltip(Tooltip.create(tr("recommendation_description", Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory() / (1024 * 1024), preset.renderDistance(), preset.simulationDistance())));

        int y = listTop;
        for (int index = scroll; index < entryCount && index < scroll + visibleEntries; index++) {
            Entry entry = entries.get(index);
            int rowY = y;
            if (entry.heading()) {
                Component label = entry.label();
                addRenderableOnly((graphics, mouseX, mouseY, delta) -> {
                    KernelUi.text(graphics, font, label, listX + 2, rowY + 6, 0xFF8A9199);
                    graphics.fill(listX + 2 + font.width(label) + 6, rowY + 9, listX + listWidth, rowY + 10, 0x26FFFFFF);
                });
            } else {
                Component label = entry.label(), description = entry.description();
                addRenderableOnly((graphics, mouseX, mouseY, delta) -> {
                    boolean hovered = mouseX >= listX && mouseX < listX + listWidth && mouseY >= rowY && mouseY < rowY + 24;
                    graphics.fill(listX, rowY, listX + listWidth, rowY + 23, hovered ? 0xC4101216 : 0xAC08090B);
                    if (hovered) {
                        graphics.fill(listX, rowY, listX + 2, rowY + 23, 0xFFF3F4F6);
                        detailTitle = label; detailText = description;
                    }
                });
                entry.placer().place(listX, rowY, listWidth);
            }
            y += entry.height();
        }

        int trackX = layout.trackX();
        int detailX = layout.detailX(), detailWidth = layout.detailWidth();
        int buttonWidth = layout.buttonWidth(3), end = layout.right();
        addRenderableOnly((graphics, mouseX, mouseY, delta) -> {
            if (entryCount > visibleEntries) {
                graphics.fill(trackX, listTop, trackX + scrollbarWidth, listBottom, 0x40000000);
                int span = Math.max(1, listBottom - listTop);
                int thumb = Math.max(12, span * visibleEntries / entryCount);
                int offset = (span - thumb) * scroll / Math.max(1, entryCount - visibleEntries);
                graphics.fill(trackX, listTop + offset, trackX + scrollbarWidth, listTop + offset + thumb, 0x80FFFFFF);
            }
            // The description stands beside the list rather than under it, so it can run to as many
            // lines as the body is tall without taking rows away from the list.
            graphics.fill(detailX, listTop, detailX + detailWidth, listBottom, 0x9008090B);
            graphics.fill(detailX, listTop, detailX + 1, listBottom, 0x20FFFFFF);
            boolean idle = detailTitle.getString().isEmpty();
            var title = KernelUi.wrap(font, (idle ? tr("detail.idle") : detailTitle).getString(), detailWidth - 12, 2);
            for (int line = 0; line < title.size(); line++)
                KernelUi.text(graphics, font, Component.literal(title.get(line)), detailX + 6, listTop + 7 + line * 10, 0xFFF3F4F6);
            int bodyTop = listTop + 9 + title.size() * 10;
            // The panel closes with what Apply would do next, and with the device string on the tab that
            // reports it. Both are measured before the description, so the description gives way to them
            // rather than pushing them off the bottom of a short window.
            String status = KernelTranslations.text(saveFailed ? "kernel.settings.save_failed" : restartRequired()
                ? "kernel.settings.restart" : hasChanges() ? "kernel.settings.pending" : "kernel.settings.applied").getString();
            var footer = KernelUi.wrap(font, status, detailWidth - 12, 3);
            var device = tab.equals("other")
                ? KernelUi.wrap(font, KernelHardwareSettings.renderer(), detailWidth - 12, 2) : List.<String>of();
            int footerTop = listBottom - 6 - footer.size() * 10;
            int deviceTop = footerTop - 4 - device.size() * 10;
            var lines = KernelUi.wrap(font, idle ? tr("detail.hint").getString() : detailText.getString(),
                detailWidth - 12, Math.max(1, (deviceTop - bodyTop - 4) / 10));
            for (int line = 0; line < lines.size(); line++)
                KernelUi.text(graphics, font, Component.literal(lines.get(line)), detailX + 6, bodyTop + line * 10, 0xFFAEB3B9);
            for (int line = 0; line < device.size(); line++)
                KernelUi.text(graphics, font, Component.literal(device.get(line)), detailX + 6, deviceTop + line * 10, 0xFF8A9199);
            graphics.fill(detailX + 6, footerTop - 4, detailX + detailWidth - 6, footerTop - 3, 0x20FFFFFF);
            for (int line = 0; line < footer.size(); line++)
                KernelUi.text(graphics, font, Component.literal(footer.get(line)), detailX + 6, footerTop + line * 10,
                    saveFailed ? 0xFFFF9B9B : restartRequired() || hasChanges() ? 0xFFFFE08A : 0xFFB8BEC5);
        });

        addRenderableWidget(new KernelButton(end - 3 * buttonWidth - 8, actionsY, buttonWidth, layout.actionHeight(), CommonComponents.GUI_CANCEL, button -> onClose()));
        addRenderableWidget(new KernelButton(end - 2 * buttonWidth - 4, actionsY, buttonWidth, layout.actionHeight(), KernelTranslations.text("kernel.settings.apply"), button -> apply(false)));
        addRenderableWidget(new KernelButton(end - buttonWidth, actionsY, buttonWidth, layout.actionHeight(), CommonComponents.GUI_DONE, button -> apply(true)));
    }

    private Entry settingEntry(VideoSetting<?> setting) {
        return new Entry(setting.label, setting.description, (x, y, width) -> addSetting(setting, x, y, width));
    }
    private void addSetting(VideoSetting<?> setting, int x, int y, int width) {
        int controls = Math.max(100, width * 42 / 100);
        int controlX = x + width - controls;
        label(setting.label, x, y, width - controls - 12);
        boolean managed = pendingFrameSync && (setting == frameLimit || setting == vsync);
        Tooltip tooltip = Tooltip.create(managed ? KernelTranslations.text("kernel.frame.managed") : setting.description);
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
                KernelUi.text(graphics, font, Component.literal(value), controlX + (controls - font.width(value)) / 2, y + 8,
                    managed ? 0xFF777B80 : 0xFFF3F4F6);
            });
            var next = addRenderableWidget(new KernelButton(x + width - 18, y + 1, 18, 22, setting.narration(), button -> { setting.cycle(1); button.setMessage(setting.narration()); saveFailed = false; }).visual(Component.literal(">")));
            next.setTooltip(tooltip); next.active = !managed && setting.choices.size() > 1;
        }
    }

    /** Draws the row label; the row background and hover highlight belong to the list itself. */
    private void label(Component text, int x, int y, int width) {
        addRenderableOnly((graphics, mouseX, mouseY, delta) ->
            KernelUi.text(graphics, font, Component.literal(font.plainSubstrByWidth(text.getString(), Math.max(10, width))), x + 8, y + 8, 0xFFF3F4F6));
    }

    private Entry frameSyncEntry() {
        return new Entry(KernelTranslations.text("kernel.frame.setting"), KernelTranslations.text("kernel.frame.description"),
            (x, y, width) -> addFrameSync(x, y, width));
    }
    private void addFrameSync(int x, int y, int width) {
        Component text = KernelTranslations.text("kernel.frame.setting");
        label(text, x, y, width - 80);
        Component state = pendingFrameSync ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
        var button = addRenderableWidget(new KernelButton(x + width - 64, y + 1, 64, 22,
            Component.empty().append(text).append(": ").append(state), pressed -> {
                pendingFrameSync = !pendingFrameSync; saveFailed = false; rebuildWidgets();
            }, () -> pendingFrameSync, false).visual(state));
        button.setTooltip(Tooltip.create(KernelTranslations.text("kernel.frame.description")));
    }

    private Entry featureEntry(RendererFeature feature) {
        return new Entry(KernelTranslations.text(feature.translationKey()), KernelTranslations.text(feature.translationKey() + ".description"),
            (x, y, width) -> addFeature(feature, x, y, width));
    }
    private void addFeature(RendererFeature feature, int x, int y, int width) {
        Component text = KernelTranslations.text(feature.translationKey());
        label(text, x, y, width - 80);
        var button = addRenderableWidget(new KernelButton(x + width - 64, y + 1, 64, 22, KernelTranslations.text("kernel.settings.value", text, featureLabel(feature)), pressed -> {
            pending = pending.with(feature, !pending.enabled(feature)); pressed.setMessage(KernelTranslations.text("kernel.settings.value", text, featureLabel(feature)));
            ((KernelButton) pressed).visual(featureLabel(feature)); saveFailed = false;
        }, () -> pending.enabled(feature), false).visual(featureLabel(feature)));
        button.setTooltip(Tooltip.create(KernelTranslations.text(feature.translationKey() + ".description").append("\n")
            .append(KernelTranslations.text("kernel.settings.current", KernelRendererSettings.enabled(feature) ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF))));
    }
    private Component featureLabel(RendererFeature feature) { return pending.enabled(feature) ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF; }

    private Entry worldEntry(WorldFeature feature) {
        String key = "kernel.world." + feature.key();
        return new Entry(KernelTranslations.text(key),
            KernelTranslations.text(WorldSettings.lithiumPresent() ? "kernel.world.lithium" : key + ".description"),
            (x, y, width) -> addWorldSetting(feature, x, y, width));
    }
    private void addWorldSetting(WorldFeature feature, int x, int y, int width) {
        String key = "kernel.world." + feature.key();
        Component text = KernelTranslations.text(key);
        label(text, x, y, width - 80);
        boolean enabled = pendingWorld.enabled(feature);
        Component state = enabled ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
        var button = addRenderableWidget(new KernelButton(x + width - 64, y + 1, 64, 22,
            KernelTranslations.text("kernel.settings.value", text, state), pressed -> {
                pendingWorld = pendingWorld.with(feature, !enabled);
                saveFailed = false; rebuildWidgets();
            }, () -> enabled, false).visual(state));
        button.active = !WorldSettings.lithiumPresent();
        boolean active = WorldSettings.active(feature);
        button.setTooltip(Tooltip.create(KernelTranslations.text(WorldSettings.lithiumPresent() ? "kernel.world.lithium" : key + ".description")
            .append("\n").append(KernelTranslations.text("kernel.settings.current", active ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF))));
    }

    private Entry resourceEntry() {
        return new Entry(KernelTranslations.text("kernel.resource.readers"), KernelTranslations.text("kernel.resource.readers.description"),
            (x, y, width) -> addResourceSetting(x, y, width));
    }
    private void addResourceSetting(int x, int y, int width) {
        Component text = KernelTranslations.text("kernel.resource.readers");
        label(text, x, y, width - 80);
        boolean enabled = pendingResources.compactReaders();
        Component state = enabled ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
        var button = addRenderableWidget(new KernelButton(x + width - 64, y + 1, 64, 22,
            KernelTranslations.text("kernel.settings.value", text, state), pressed -> {
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

    /**
     * Moves the list by whole entries, so every drawn row stays complete.
     *
     * <p>Returns false once the list is already at that end, which lets a caller walking the whole list
     * recognise that there is nothing further to reach.
     */
    public boolean scrollBy(int entries) {
        int next = Math.clamp(scroll + entries, 0, Math.max(0, entryCount - visibleEntries));
        if (next == scroll) return false;
        scroll = next; rebuildWidgets(); return true;
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (scrollBy(-(int) Math.signum(vertical) * 2)) return true;
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
        if (category != null) { tab = category; scroll = 0; }
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
