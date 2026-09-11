package dev.kernel.fabric.verification;

import dev.kernel.fabric.config.KernelTranslations;
import dev.kernel.fabric.world.WorldConfig;
import dev.kernel.fabric.world.WorldSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;

final class WorldSettingsProbe {
    static void verify(Minecraft minecraft, Screen parent) throws java.io.IOException {
        var saved = WorldSettings.saved();
        boolean active = WorldSettings.biomeOffsetsActive();
        open(minecraft, parent);
        GuiProbe.click(toggle(minecraft));
        GuiProbe.click(GuiProbe.find(GuiProbe.screen(minecraft), "gui.cancel"));
        if (!WorldSettings.saved().equals(saved)) throw new AssertionError("World-settings Cancel persisted draft");
        open(minecraft, parent);
        GuiProbe.click(toggle(minecraft));
        GuiProbe.click(GuiProbe.find(GuiProbe.screen(minecraft), "kernel.settings.apply"));
        if (WorldSettings.saved().biomeOffsets() == saved.biomeOffsets()) throw new AssertionError("World-settings Apply did not save");
        if (WorldSettings.biomeOffsetsActive() != active) throw new AssertionError("World setting changed active Mixins without restart");
        GuiProbe.click(toggle(minecraft));
        GuiProbe.click(GuiProbe.find(GuiProbe.screen(minecraft), "gui.done"));
        if (GuiProbe.screen(minecraft) != parent || !WorldConfig.load(minecraft.gameDirectory.toPath().resolve("config/kernel-world.properties")).equals(saved)) {
            throw new AssertionError("World-settings Done did not restore/persist the original draft");
        }
    }
    private static void open(Minecraft minecraft, Screen parent) {
        GuiProbe.click(GuiProbe.find(parent, "kernel.settings.open"));
        GuiProbe.click(GuiProbe.find(GuiProbe.screen(minecraft), "kernel.video.tab.optimizations"));
    }
    private static Button toggle(Minecraft minecraft) {
        String label = KernelTranslations.text("kernel.world.biome_offsets").getString() + ":";
        for (int page = 0; page < 32; page++) {
            var found = GuiProbe.screen(minecraft).children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().startsWith(label)).findFirst();
            if (found.isPresent()) return found.get();
            GuiProbe.click(GuiProbe.find(GuiProbe.screen(minecraft), "kernel.settings.next"));
        }
        throw new AssertionError("Missing biome offset control");
    }
}
