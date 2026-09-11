package dev.kernel.fabric.verification;

import dev.kernel.fabric.resource.ResourceConfig;
import dev.kernel.fabric.resource.ResourceSettings;
import dev.kernel.fabric.config.KernelTranslations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

final class ResourceSettingsProbe {
    static void verify(Minecraft minecraft, Screen parent) throws java.io.IOException {
        var saved = ResourceSettings.saved();
        String label = KernelTranslations.text("kernel.resource.readers").getString();
        boolean active = ResourceSettings.compactReadersActive();
        open(minecraft, parent);
        GuiProbe.click(GuiProbe.findSetting(minecraft, label));
        GuiProbe.click(GuiProbe.find(GuiProbe.screen(minecraft), "gui.cancel"));
        if (!ResourceSettings.saved().equals(saved)) throw new AssertionError("Resource Cancel persisted draft");
        open(minecraft, parent);
        GuiProbe.click(GuiProbe.findSetting(minecraft, label));
        GuiProbe.click(GuiProbe.find(GuiProbe.screen(minecraft), "kernel.settings.apply"));
        if (ResourceSettings.saved().equals(saved) || ResourceSettings.compactReadersActive() != active)
            throw new AssertionError("Resource Apply failed or activated without restart");
        GuiProbe.click(GuiProbe.findSetting(minecraft, label));
        GuiProbe.click(GuiProbe.find(GuiProbe.screen(minecraft), "gui.done"));
        if (GuiProbe.screen(minecraft) != parent || !ResourceConfig.load(minecraft.gameDirectory.toPath()
            .resolve("config/kernel-resources.properties")).equals(saved)) throw new AssertionError("Resource Done persistence");
    }
    private static void open(Minecraft minecraft, Screen parent) {
        GuiProbe.click(GuiProbe.find(parent, "kernel.settings.open"));
        GuiProbe.click(GuiProbe.find(GuiProbe.screen(minecraft), "kernel.video.tab.optimizations"));
    }
}
