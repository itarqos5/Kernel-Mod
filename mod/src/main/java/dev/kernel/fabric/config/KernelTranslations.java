package dev.kernel.fabric.config;

import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Bare Fabric Loader does not install a mod resource pack; native translation fallbacks keep labels usable. */
public final class KernelTranslations {
    private static final Map<String, String> FALLBACKS = load();

    private KernelTranslations() {}

    public static MutableComponent text(String key, Object... arguments) {
        return Component.translatableWithFallback(key, FALLBACKS.getOrDefault(key, key), arguments);
    }

    private static Map<String, String> load() {
        try (var input = KernelTranslations.class.getResourceAsStream("/assets/kernel/lang/en_us.json")) {
            if (input == null) throw new IllegalStateException("Missing Kernel English translations");
            var json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> values = new HashMap<>();
            for (var entry : json.entrySet()) values.put(entry.getKey(), entry.getValue().getAsString());
            return Map.copyOf(values);
        } catch (Exception exception) {
            LoggerFactory.getLogger("Kernel").warn("Cannot read Kernel translation fallbacks", exception);
            return Map.of();
        }
    }
}
