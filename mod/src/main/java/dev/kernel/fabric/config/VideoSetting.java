package dev.kernel.fabric.config;

import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** A draft retains an exact existing value even if it is outside the choices shown in this screen. */
final class VideoSetting<T> {
    final String tab;
    /** The group heading this setting appears under, so related controls stay together while scrolling. */
    final String section;
    final Component label;
    /** The pack of explanatory text shown while this row is hovered or focused. */
    final Component description;
    final List<T> choices;
    final boolean slider;
    final Function<T, Component> display;
    final Consumer<T> apply;
    T saved;
    T value;

    VideoSetting(String tab, String section, Component label, Component description, List<T> choices, boolean slider,
                 T current, Function<T, Component> display, Consumer<T> apply) {
        this.tab = tab; this.section = section; this.label = label; this.description = description;
        this.choices = List.copyOf(choices); this.slider = slider;
        this.saved = current; this.value = current; this.display = display; this.apply = apply;
    }

    static <T> VideoSetting<T> option(String tab, String section, String key, OptionInstance<T> option, List<T> choices,
                                      boolean slider, Function<T, Component> display) {
        return new VideoSetting<>(tab, section, KernelTranslations.text(key), description(key), choices,
            slider, option.get(), display, option::set);
    }

    /** Falls back to the label when a pack or resource pack has not translated a description. */
    static Component description(String key) {
        Component description = KernelTranslations.text(key + ".description");
        return description.getString().equals(key + ".description") ? KernelTranslations.text(key) : description;
    }

    Component valueText() { return display.apply(value); }
    Component narration() { return Component.empty().append(label).append(": ").append(valueText()); }
    boolean changed() { return !Objects.equals(saved, value); }
    void commit() { if (changed()) { apply.accept(value); saved = value; } }
    void cycle(int direction) { if (!choices.isEmpty()) value = choices.get(Math.floorMod(choices.indexOf(value) + direction, choices.size())); }
    double position() { return choices.size() < 2 ? 0 : Math.max(0, choices.indexOf(value)) / (double) (choices.size() - 1); }
    void position(double position) { if (!choices.isEmpty()) value = choices.get((int) Math.round(position * (choices.size() - 1))); }
}
