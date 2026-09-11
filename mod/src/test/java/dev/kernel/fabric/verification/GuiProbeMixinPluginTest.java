package dev.kernel.fabric.verification;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GuiProbeMixinPluginTest {
    @Test void everyRegisteredProbeHasTheExpectedScope() throws Exception {
        var scopes = Map.of("GuiProbeMixin", 0, "BiomeProbeMixin", 1, "ShaderWorldProbeMixin", 2,
            "ShaderDepthProbeMixin", 2, "ShaderProjectionProbeMixin", 2, "ShaderCameraProbeMixin", 2,
            "ChunkWorkProbeMixin", 4, "ChunkUniformProbeMixin", 2);
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/gui-probe/kernel-gui-probe.mixins.json"), StandardCharsets.UTF_8)) {
            var config = JsonParser.parseReader(reader).getAsJsonObject();
            assertEquals(GuiProbeMixinPlugin.class.getName(), config.get("plugin").getAsString());
            var mixins = config.getAsJsonArray("client");
            assertEquals(scopes.size(), mixins.size());
            for (int enabled = 0; enabled < 8; enabled++) {
                var plugin = new GuiProbeMixinPlugin((enabled & 1) != 0, (enabled & 2) != 0, (enabled & 4) != 0);
                for (var entry : mixins) {
                    String name = entry.getAsString();
                    int scope = scopes.get(name);
                    assertEquals(scope == 0 || (scope & enabled) != 0,
                        plugin.shouldApplyMixin("unused", config.get("package").getAsString() + "." + name), name);
                }
            }
        }
    }
    @Test void anUnscopedProbeFailsExplicitly() {
        var plugin = new GuiProbeMixinPlugin(false, false, false);
        assertThrows(IllegalArgumentException.class, () -> plugin.shouldApplyMixin("unused", "UnexpectedProbeMixin"));
    }
}
