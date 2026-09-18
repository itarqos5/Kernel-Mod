package dev.kernel.fabric.shader;

import dev.kernel.fabric.config.*;
import dev.kernel.fabric.shader.pack.ModrinthShaders;
import dev.kernel.fabric.shader.pack.ShaderOption;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;

import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
//? if <=1.21.5 {
/*import net.minecraft.client.gui.GuiGraphics;
*///? }

/** Native, keyboard-accessible shader browser. Downloads and imports never execute on the render thread. */
public final class ShaderScreen extends Screen {
    /** Which list the main area shows: installed packs, Modrinth results, or the active pack options. */
    private enum View { PACKS, REMOTE, OPTIONS }
    private final KernelSettingsScreen parent;
    private final ExecutorService searchWorker = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "Kernel Modrinth search"); thread.setDaemon(true); return thread;
    });
    private View view = View.PACKS;
    private boolean searching;
    private String query = "", searchError = "";
    private ModrinthShaders.Search results = new ModrinthShaders.Search(List.of(), 0, 0);
    private CompletableFuture<ModrinthShaders.Search> search;
    private EditBox searchBox;
    private int scroll, visibleRows = 1, rowCount;
    private long revision = -1;
    private Component detailTitle = Component.empty();
    private Component detailText = Component.empty();

    public ShaderScreen(KernelSettingsScreen parent) { super(text("title")); this.parent = parent; KernelShaders.refresh(); }
    private static Component text(String key, Object... arguments) { return KernelTranslations.text("kernel.shaders." + key, arguments); }
    private static Component literal(String value) { return Component.literal(value); }

    /** One row of the main list, placed once its position inside the viewport is known. */
    @FunctionalInterface private interface Placer { void place(int x, int y, int width); }
    private record Row(Component label, Component description, int height, Placer placer) {}

    @Override protected void init() {
        int total = Math.min(760, width - 20), left = (width - total) / 2;
        int sidebar = Math.min(112, Math.max(80, total / 5));
        int listX = left + sidebar + 10, scrollbar = 4;
        int listWidth = total - sidebar - 10 - scrollbar - 4;
        int toolbarY = 56, listTop = 84;
        int actionsY = height - 28, detailHeight = 46;
        int detailY = actionsY - 8 - detailHeight, listBottom = detailY - 6;

        var rows = rows();
        rowCount = rows.size();
        // Pack rows and option rows differ in height, so measure again from the clamped position.
        int available = Math.max(24, listBottom - listTop);
        for (int pass = 0; pass < 2; pass++) {
            int used = 0, fits = 0;
            for (int index = Math.min(scroll, Math.max(0, rowCount - 1)); index < rowCount; index++) {
                if (used + rows.get(index).height() > available) break;
                used += rows.get(index).height(); fits++;
            }
            visibleRows = Math.max(1, fits);
            scroll = Math.clamp(scroll, 0, Math.max(0, rowCount - visibleRows));
        }

        addRenderableOnly((graphics, mouseX, mouseY, delta) -> {
            detailTitle = Component.empty(); detailText = Component.empty();
            graphics.fill(left - 4, 10, left + total + 4, 46, 0x9008090B);
            graphics.fill(left - 4, 45, left + total + 4, 46, 0x30FFFFFF);
            KernelUi.icon(graphics, left + 2, 15, 26);
            KernelUi.text(graphics, font, literal("K E R N E L"), left + 36, 17, 0xFFF3F4F6);
            String active = KernelShaders.active().isEmpty() ? text("off").getString() : KernelShaders.active();
            KernelUi.text(graphics, font, literal(font.plainSubstrByWidth(text("active", active).getString(), total - 44)), left + 36, 31, 0xFFAEB3B9);
        });

        var categories = List.of("video", "graphics", "optimizations", "other", "shaders");
        for (int i = 0; i < categories.size(); i++) {
            String category = categories.get(i);
            var tab = addRenderableWidget(new KernelButton(left, toolbarY + i * 26, sidebar, 24,
                KernelTranslations.text("kernel.video.tab." + category), button -> { if (!category.equals("shaders")) parent.showCategory(category); },
                () -> category.equals("shaders"), false));
            if (category.equals("shaders") && !ShaderBackend.supported()) tab.active = false;
        }

        addToolbar(listX, toolbarY, listWidth + scrollbar + 4);

        int y = listTop;
        for (int index = scroll; index < rowCount && index < scroll + visibleRows; index++) {
            Row row = rows.get(index);
            int rowY = y, rowHeight = row.height();
            Component label = row.label(), description = row.description();
            addRenderableOnly((graphics, mouseX, mouseY, delta) -> {
                boolean hovered = mouseX >= listX && mouseX < listX + listWidth && mouseY >= rowY && mouseY < rowY + rowHeight;
                graphics.fill(listX, rowY, listX + listWidth, rowY + rowHeight - 1, hovered ? 0xC4101216 : 0xAC08090B);
                if (hovered) {
                    graphics.fill(listX, rowY, listX + 2, rowY + rowHeight - 1, 0xFFF3F4F6);
                    detailTitle = label; detailText = description;
                }
            });
            row.placer().place(listX, rowY, listWidth);
            y += rowHeight;
        }

        int trackX = listX + listWidth + 4, trackBottom = y;
        addRenderableOnly((graphics, mouseX, mouseY, delta) -> {
            if (rowCount > visibleRows) {
                graphics.fill(trackX, listTop, trackX + scrollbar, trackBottom, 0x40000000);
                int span = Math.max(1, trackBottom - listTop);
                int thumb = Math.max(12, span * visibleRows / rowCount);
                int offset = (span - thumb) * scroll / Math.max(1, rowCount - visibleRows);
                graphics.fill(trackX, listTop + offset, trackX + scrollbar, listTop + offset + thumb, 0x80FFFFFF);
            }
            if (rowCount == 0 && !searching)
                KernelUi.text(graphics, font, text(view == View.REMOTE ? "no_results" : view == View.OPTIONS ? "no_options" : "empty"), listX + 6, listTop + 8, 0xFFB8BEC5);
            graphics.fill(listX, detailY, listX + listWidth + scrollbar + 4, detailY + detailHeight, 0x9008090B);
            boolean idle = detailTitle.getString().isEmpty();
            KernelUi.text(graphics, font, idle ? text("title") : detailTitle, listX + 6, detailY + 6, 0xFFF3F4F6);
            String body = idle ? text(view == View.OPTIONS ? "options_hint" : "scope").getString() : detailText.getString();
            var lines = KernelUi.wrap(font, body, listWidth + scrollbar - 8, 3);
            for (int line = 0; line < lines.size(); line++)
                KernelUi.text(graphics, font, literal(lines.get(line)), listX + 6, detailY + 18 + line * 10, 0xFFAEB3B9);
            String status = !searchError.isEmpty() ? searchError : searching ? text("searching").getString() : KernelShaders.message();
            KernelUi.text(graphics, font, literal(font.plainSubstrByWidth(status, listWidth - 160)), listX, actionsY + 7,
                KernelShaders.failed() || !searchError.isEmpty() ? 0xFFFF9B9B : 0xFFB8BEC5);
        });

        var off = addRenderableWidget(new KernelButton(left, actionsY, sidebar, 22, text("disable"), ignored -> KernelShaders.disable()));
        off.active = !KernelShaders.busy();
        int end = left + total, buttonWidth = 74;
        var cancel = addRenderableWidget(new KernelButton(end - 2 * buttonWidth - 4, actionsY, buttonWidth, 22, text("cancel"), ignored -> KernelShaders.cancel()));
        cancel.active = KernelShaders.busy();
        addRenderableWidget(new KernelButton(end - buttonWidth, actionsY, buttonWidth, 22, KernelTranslations.text("gui.done"), ignored -> onClose()));
        revision = KernelShaders.revision();
    }

    private void addToolbar(int x, int y, int width) {
        int third = Math.max(60, (width - 8) / 3);
        if (view == View.REMOTE) {
            searchBox = addRenderableWidget(new EditBox(font, x + 2, y + 2, Math.max(30, width - 2 * third - 10), 18, text("query")));
            searchBox.setMaxLength(200); searchBox.setValue(query); searchBox.setResponder(value -> query = value);
            var button = addRenderableWidget(new KernelButton(x + width - 2 * third - 4, y, third, 22, text("search"), ignored -> requestSearch(0)));
            button.active = !searching;
            addRenderableWidget(new KernelButton(x + width - third, y, third, 22, text("packs"), ignored -> switchTo(View.PACKS)));
            return;
        }
        var packs = addRenderableWidget(new KernelButton(x, y, third, 22, text("packs"), ignored -> switchTo(View.PACKS), () -> view == View.PACKS, false));
        packs.active = view != View.PACKS;
        var options = addRenderableWidget(new KernelButton(x + third + 4, y, third, 22, text("options"), ignored -> switchTo(View.OPTIONS), () -> view == View.OPTIONS, false));
        options.active = !KernelShaders.active().isEmpty();
        options.setTooltip(Tooltip.create(text("options_hint")));
        if (view == View.OPTIONS) {
            var reset = addRenderableWidget(new KernelButton(x + width - third, y, third, 22, text("reset"), ignored -> KernelShaders.resetOptions()));
            reset.active = !KernelShaders.busy() && KernelShaders.options().stream().anyMatch(KernelShaders::optionChanged);
        } else {
            addRenderableWidget(new KernelButton(x + width - third, y, third, 22, text("modrinth"), ignored -> {
                switchTo(View.REMOTE); if (results.projects().isEmpty()) requestSearch(0);
            }));
        }
    }

    private void switchTo(View next) { view = next; scroll = 0; rebuildWidgets(); }

    private List<Row> rows() {
        var rows = new ArrayList<Row>();
        if (view == View.OPTIONS) {
            for (var option : KernelShaders.options()) rows.add(optionRow(option));
            return rows;
        }
        if (view == View.REMOTE) {
            for (var project : results.projects()) rows.add(packRow(project.title(), project.description(), project));
            int shown = results.offset() + results.projects().size();
            if (shown < results.total()) rows.add(moreRow(shown, results.total()));
            return rows;
        }
        for (String pack : KernelShaders.installed()) {
            boolean enabled = pack.equals(KernelShaders.active());
            rows.add(packRow(pack, text(enabled ? "enabled" : "ready").getString(), null));
        }
        return rows;
    }

    /** The last row of a Modrinth result page, which fetches the next page in place. */
    private Row moreRow(int shown, int total) {
        Component label = text("more", shown, total);
        return new Row(label, text("more", shown, total), 30, (x, y, width) -> {
            var button = addRenderableWidget(new KernelButton(x + 6, y + 3, Math.max(80, width - 12), 22, label,
                ignored -> requestSearch(shown)));
            button.active = !searching;
        });
    }

    private Row packRow(String title, String subtitle, ModrinthShaders.Project project) {
        return new Row(literal(title), literal(subtitle), 30, (x, y, width) -> {
            addRenderableOnly((graphics, mouseX, mouseY, delta) -> {
                KernelUi.text(graphics, font, literal(font.plainSubstrByWidth(title, Math.max(20, width - 80))), x + 8, y + 5, 0xFFF3F4F6);
                KernelUi.text(graphics, font, literal(font.plainSubstrByWidth(subtitle, Math.max(20, width - 80))), x + 8, y + 16, 0xFF8A9199);
            });
            var button = addRenderableWidget(new KernelButton(x + width - 68, y + 3, 64, 22, text(project != null ? "install" : "enable"), ignored -> {
                if (project != null) KernelShaders.install(project); else KernelShaders.select(title);
            }, () -> title.equals(KernelShaders.active()), false));
            button.active = !KernelShaders.busy();
            button.setTooltip(Tooltip.create(literal(title + "\n" + subtitle)));
        });
    }

    private Row optionRow(ShaderOption option) {
        String declared = option.comment().isEmpty() ? KernelShaders.properties().description(option.name()) : option.comment();
        Component description = declared.isEmpty() ? literal(option.name()) : literal(declared);
        Component label = literal(option.name());
        return new Row(label, KernelShaders.optionChanged(option)
            ? Component.empty().append(description).append(" · ").append(text("modified")) : description, 24, (x, y, width) -> {
            int controls = Math.max(96, width * 40 / 100), controlX = x + width - controls;
            String current = KernelShaders.optionValue(option);
            addRenderableOnly((graphics, mouseX, mouseY, delta) -> KernelUi.text(graphics, font,
                literal(font.plainSubstrByWidth(option.name(), Math.max(10, width - controls - 16))), x + 8, y + 8,
                KernelShaders.optionChanged(option) ? 0xFFFFE08A : 0xFFF3F4F6));
            Component narration = Component.empty().append(label).append(": ").append(literal(current));
            // An option the pack itself marks as a slider gets a position indicator rather than a drag
            // handle: dragging would recompile the whole pipeline on every intermediate value.
            if (option.slider() && option.values().size() > 2) {
                int index = Math.max(0, option.values().indexOf(current));
                addRenderableOnly((graphics, mouseX, mouseY, delta) -> {
                    int trackLeft = controlX + 20, trackRight = x + width - 20;
                    graphics.fill(trackLeft, y + 19, trackRight, y + 20, 0xFF55585B);
                    graphics.fill(trackLeft, y + 19, trackLeft + (trackRight - trackLeft) * index / (option.values().size() - 1), y + 20, 0xFFF3F4F6);
                });
            }
            var previous = addRenderableWidget(new KernelButton(controlX, y + 1, 18, 22, narration,
                button -> KernelShaders.setOption(option.name(), option.cycle(current, -1))).visual(literal("<")));
            previous.active = !KernelShaders.busy();
            previous.setTooltip(Tooltip.create(description));
            addRenderableOnly((graphics, mouseX, mouseY, delta) -> {
                String value = font.plainSubstrByWidth(current, controls - 40);
                KernelUi.text(graphics, font, literal(value), controlX + (controls - font.width(value)) / 2, y + 8, 0xFFF3F4F6);
            });
            var next = addRenderableWidget(new KernelButton(x + width - 18, y + 1, 18, 22, narration,
                button -> KernelShaders.setOption(option.name(), option.cycle(current, 1))).visual(literal(">")));
            next.active = !KernelShaders.busy();
            next.setTooltip(Tooltip.create(description));
        });
    }

    private void requestSearch(int offset) {
        if (searching || searchWorker.isShutdown()) return;
        searching = true; searchError = ""; String value = query;
        search = CompletableFuture.supplyAsync(() -> {
            try { return KernelShaders.api().search(value, KernelShaders.gameVersion(), offset); }
            catch (java.io.IOException exception) { throw new java.util.concurrent.CompletionException(exception); }
        }, searchWorker);
        rebuildWidgets();
    }
    /** Moves the list by whole rows; false means this end of the list has already been reached. */
    public boolean scrollBy(int rows) {
        int next = Math.clamp(scroll + rows, 0, Math.max(0, rowCount - visibleRows));
        if (next == scroll) return false;
        scroll = next; rebuildWidgets(); return true;
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (scrollBy(-(int) Math.signum(vertical) * 2)) return true;
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public void tick() {
        super.tick();
        if (searching && search.isDone()) {
            searching = false;
            try { results = search.join(); scroll = 0; }
            catch (RuntimeException failure) { searchError = failure.getCause() == null ? failure.toString() : failure.getCause().getMessage(); }
            rebuildWidgets();
        } else if (revision != KernelShaders.revision()) rebuildWidgets();
    }
    @Override public void onFilesDrop(List<Path> files) { KernelShaders.importPacks(files); }
    @Override public void removed() { searchWorker.shutdownNow(); super.removed(); }
    @Override public void onClose() { parent.showCategory(null); }
    //? if <=1.21.5 {
    /*@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick); super.render(graphics, mouseX, mouseY, partialTick);
    }
    *///? }
}
