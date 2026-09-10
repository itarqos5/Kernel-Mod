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
    final Component label;
    final List<T> choices;
    final boolean slider;
    final Function<T, Component> display;
    final Consumer<T> apply;
    T saved;
    T value;

    VideoSetting(String tab, Component label, List<T> choices, boolean slider, T current, Function<T, Component> display, Consumer<T> apply) {
        this.tab = tab; this.label = label; this.choices = List.copyOf(choices); this.slider = slider;
        this.saved = current; this.value = current; this.display = display; this.apply = apply;
    }

    static <T> VideoSetting<T> option(String tab, String key, OptionInstance<T> option, List<T> choices, boolean slider, Function<T, Component> display) {
        return new VideoSetting<>(tab, KernelTranslations.text(key), choices,
            slider, option.get(), display, option::set);
    }
    Component valueText() { return display.apply(value); }
    Component narration() { return Component.empty().append(label).append(": ").append(valueText()); }
    boolean changed() { return !Objects.equals(saved, value); }
    void commit() { if (changed()) { apply.accept(value); saved = value; } }
    void cycle(int direction) { if (!choices.isEmpty()) value = choices.get(Math.floorMod(choices.indexOf(value) + direction, choices.size())); }
    double position() { return choices.size() < 2 ? 0 : Math.max(0, choices.indexOf(value)) / (double) (choices.size() - 1); }
    void position(double position) { if (!choices.isEmpty()) value = choices.get((int) Math.round(position * (choices.size() - 1))); }
}
