package dev.kernel.fabric.shader;

import dev.kernel.fabric.config.*;
import dev.kernel.fabric.shader.pack.ModrinthShaders;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
//? if <=1.21.5 {
/*import net.minecraft.client.gui.GuiGraphics;
*///? }

/** Native, keyboard-accessible shader browser. Downloads and imports never execute on the render thread. */
public final class ShaderScreen extends Screen {
    private final KernelSettingsScreen parent;
    private final ExecutorService searchWorker = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "Kernel Modrinth search"); thread.setDaemon(true); return thread;
    });
    private boolean remote, searching;
    private String query = "", searchError = "";
    private ModrinthShaders.Search results = new ModrinthShaders.Search(List.of(), 0, 0);
    private CompletableFuture<ModrinthShaders.Search> search;
    private EditBox searchBox;
    private int page, rows = 1;
    private long revision = -1;

    public ShaderScreen(KernelSettingsScreen parent) { super(text("title")); this.parent = parent; KernelShaders.refresh(); }
    private static Component text(String key, Object... arguments) { return KernelTranslations.text("kernel.shaders." + key, arguments); }
    private static Component literal(String value) { return Component.literal(value); }
    @Override protected void init() {
        int total = Math.min(700, width - 24), left = (width - total) / 2;
        int sidebar = Math.min(104, Math.max(76, total / 5)), x = left + sidebar + 12, w = total - sidebar - 12;
        rows = Math.max(1, (height - 164) / 30);
        int count = remote ? results.projects().size() : KernelShaders.installed().size();
        page = Math.clamp(page, 0, Math.max(0, (count - 1) / rows));
        addRenderableOnly((graphics, mouseX, mouseY, delta) -> {
            graphics.fill(left - 4, 10, left + total + 4, 40, 0x9008090B);
            KernelUi.icon(graphics, left + 2, 13, 24);
            KernelUi.text(graphics, font, literal("K E R N E L"), left + 34, 15, 0xFFF3F4F6);
            KernelUi.text(graphics, font, text("title"), left + 34, 28, 0xFFAEB3B9);
            graphics.fill(x, 44, x + w, 45, 0x50FFFFFF);
            String active = KernelShaders.active().isEmpty() ? text("off").getString() : KernelShaders.active();
            KernelUi.text(graphics, font, literal(font.plainSubstrByWidth(text("active", active).getString(), w)), x, 50, 0xFFB8BEC5);
            String status = !searchError.isEmpty() ? searchError : searching ? text("searching").getString() : KernelShaders.message();
            KernelUi.text(graphics, font, literal(font.plainSubstrByWidth(status, total)), left, height - 54,
                KernelShaders.failed() || !searchError.isEmpty() ? 0xFFFF9B9B : 0xFFB8BEC5);
            KernelUi.text(graphics, font, literal(font.plainSubstrByWidth(text("scope").getString(), total)), left, height - 42, 0xFF989FA8);
            if (count == 0 && !searching) KernelUi.text(graphics, font, text(remote ? "no_results" : "empty"), x + 4, 100, 0xFFB8BEC5);
        });
        var categories = List.of("video", "graphics", "optimizations", "other", "shaders");
        for (int i = 0; i < categories.size(); i++) {
            String category = categories.get(i);
            addRenderableWidget(new KernelButton(left, 50 + i * 26, sidebar, 24,
                KernelTranslations.text("kernel.video.tab." + category), button -> { if (!category.equals("shaders")) parent.showCategory(category); },
                () -> category.equals("shaders"), false));
        }
        int toolsY = 66;
        if (remote) {
            searchBox = addRenderableWidget(new EditBox(font, x + 2, toolsY + 2, Math.max(30, w - 126), 18, text("query")));
            searchBox.setMaxLength(200); searchBox.setValue(query); searchBox.setResponder(value -> query = value);
            var button = addRenderableWidget(new KernelButton(x + w - 120, toolsY, 58, 22, text("search"), ignored -> requestSearch(0)));
            button.active = !searching;
            addRenderableWidget(new KernelButton(x + w - 58, toolsY, 58, 22, text("installed"), ignored -> { remote = false; page = 0; rebuildWidgets(); }));
        } else {
            addRenderableWidget(new KernelButton(x, toolsY, Math.max(72, w / 2 - 2), 22, text("modrinth"), ignored -> {
                remote = true; page = 0; rebuildWidgets(); if (results.projects().isEmpty()) requestSearch(0);
            }));
            var button = addRenderableWidget(new KernelButton(x + w / 2 + 2, toolsY, w - w / 2 - 2, 22, text("refresh"), ignored -> KernelShaders.refresh()));
            button.active = !KernelShaders.busy();
        }
        for (int row = 0; row < rows && page * rows + row < count; row++) {
            int index = page * rows + row, y = 94 + row * 30;
            String title = remote ? results.projects().get(index).title() : KernelShaders.installed().get(index);
            String subtitle = remote ? results.projects().get(index).description() : title.equals(KernelShaders.active()) ? text("enabled").getString() : text("ready").getString();
            addRenderableOnly((graphics, mouseX, mouseY, delta) -> {
                graphics.fill(x, y, x + w, y + 28, 0x9008090B);
                KernelUi.text(graphics, font, literal(font.plainSubstrByWidth(title, Math.max(20, w - 80))), x + 6, y + 4, 0xFFF3F4F6);
                KernelUi.text(graphics, font, literal(font.plainSubstrByWidth(subtitle, Math.max(20, w - 80))), x + 6, y + 16, 0xFF989FA8);
            });
            boolean isRemote = remote;
            var project = remote ? results.projects().get(index) : null;
            var button = addRenderableWidget(new KernelButton(x + w - 68, y + 3, 64, 22, text(remote ? "install" : "enable"), ignored -> {
                if (isRemote) KernelShaders.install(project); else KernelShaders.select(title);
            }, () -> title.equals(KernelShaders.active()), false));
            button.active = !KernelShaders.busy(); button.setTooltip(Tooltip.create(literal(title + "\n" + subtitle)));
        }
        boolean previousPage = page > 0 || remote && results.offset() > 0;
        boolean nextPage = (page + 1) * rows < count || remote && results.offset() + results.projects().size() < results.total();
        var previous = addRenderableWidget(new KernelButton(x, height - 80, 32, 18, KernelTranslations.text("kernel.settings.previous"), ignored -> {
            if (page > 0) { page--; rebuildWidgets(); } else requestSearch(Math.max(0, results.offset() - 12));
        }).visual(literal("<"))); previous.active = previousPage && !searching;
        var next = addRenderableWidget(new KernelButton(x + w - 32, height - 80, 32, 18, KernelTranslations.text("kernel.settings.next"), ignored -> {
            if ((page + 1) * rows < count) { page++; rebuildWidgets(); } else requestSearch(results.offset() + 12);
        }).visual(literal(">"))); next.active = nextPage && !searching;
        var off = addRenderableWidget(new KernelButton(left, height - 28, sidebar, 22, text("disable"), ignored -> KernelShaders.disable()));
        off.active = !KernelShaders.busy();
        var cancel = addRenderableWidget(new KernelButton(x, height - 28, 70, 22, text("cancel"), ignored -> KernelShaders.cancel()));
        cancel.active = KernelShaders.busy();
        var details = addRenderableWidget(new KernelButton(x + 74, height - 28, Math.max(40, w - 148), 22, text("details"), ignored -> {}));
        String detail = !searchError.isEmpty() ? searchError : KernelShaders.message();
        details.setTooltip(Tooltip.create(literal(detail.length() > 1200 ? detail.substring(0, 1200) : detail)));
        addRenderableWidget(new KernelButton(x + w - 70, height - 28, 70, 22, KernelTranslations.text("gui.done"), ignored -> onClose()));
        revision = KernelShaders.revision();
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
    @Override public void tick() {
        super.tick();
        if (searching && search.isDone()) {
            searching = false;
            try { results = search.join(); page = 0; }
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
