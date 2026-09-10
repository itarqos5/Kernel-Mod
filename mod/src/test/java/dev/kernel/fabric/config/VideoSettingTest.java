package dev.kernel.fabric.config;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VideoSettingTest {
    @Test void DraftDoesNotMutateLiveOptionsAndAppliesOnlyChanges() {
        List<Integer> applied = new ArrayList<>();
        var setting = new VideoSetting<>("video", Component.literal("Distance"), List.of(6, 8, 12), true, 8,
            v -> Component.literal(v.toString()), applied::add);
        setting.commit(); assertTrue(applied.isEmpty());
        setting.position(1); assertEquals(12, setting.value); assertTrue(applied.isEmpty());
        setting.commit(); setting.commit(); assertEquals(List.of(12), applied);
        assertFalse(setting.changed());
    }
    @Test void UnlistedExistingValueSurvivesUntilTheUserChangesIt() {
        List<Integer> applied = new ArrayList<>();
        var setting = new VideoSetting<>("video", Component.literal("Distance"), List.of(6, 8, 12), true, 48,
            v -> Component.literal(v.toString()), applied::add);
        setting.commit(); assertTrue(applied.isEmpty()); assertEquals(48, setting.value);
        setting.cycle(1); assertEquals(6, setting.value);
        setting.cycle(-1); assertEquals(12, setting.value);
    }
}
