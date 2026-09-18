package dev.kernel.fabric.shader.pack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The subset of {@code shaders.properties} Kernel acts on.
 *
 * <p>Only directives Kernel can honour exactly are interpreted. Everything else in the file is kept as
 * raw text so later rendering stages can read it without changing this parser, and an unrecognised or
 * malformed directive never rejects the pack: it is simply not honoured.
 */
public record ShaderProperties(Map<String, String> raw, Set<String> sliders, List<String> screen,
                               Map<String, List<String>> subScreens, Map<String, Integer> columns,
                               Map<String, String> programToggles) {
    private static final int MAX_BYTES = 512 * 1024;
    private static final int MAX_KEYS = 4096;
    private static final int MAX_SCREEN_ENTRIES = 512;
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_.*-]{1,128}");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_.<>*-]{1,128}");
    /** The screen token that renders an empty cell. */
    public static final String EMPTY = "<empty>";

    public ShaderProperties {
        raw = Map.copyOf(raw);
        sliders = Set.copyOf(sliders);
        screen = List.copyOf(screen);
        subScreens = Map.copyOf(subScreens);
        columns = Map.copyOf(columns);
        programToggles = Map.copyOf(programToggles);
    }

    public static ShaderProperties empty() {
        return new ShaderProperties(Map.of(), Set.of(), List.of(), Map.of(), Map.of(), Map.of());
    }

    /** Reads {@code shaders.properties} from the pack, preferring the dimension folder when present. */
    public static ShaderProperties read(ShaderPackArchive archive, String dimension) throws IOException {
        String path = archive.resolve(dimension, "shaders.properties");
        return path == null ? empty() : parse(archive.text(path, MAX_BYTES));
    }

    public static ShaderProperties parse(String text) {
        var values = new LinkedHashMap<String, String>();
        for (String line : text.split("\\r\\n|\\r|\\n", -1)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) continue;
            int separator = separator(trimmed);
            if (separator < 0 || values.size() >= MAX_KEYS) continue;
            String key = trimmed.substring(0, separator).strip();
            if (!KEY.matcher(key).matches()) continue;
            values.putIfAbsent(key, trimmed.substring(separator + 1).strip());
        }
        var sliders = new LinkedHashSet<String>();
        for (String name : tokens(values.get("sliders"))) sliders.add(name);
        var subScreens = new LinkedHashMap<String, List<String>>();
        var columns = new LinkedHashMap<String, Integer>();
        var toggles = new LinkedHashMap<String, String>();
        for (var entry : values.entrySet()) {
            String key = entry.getKey();
            if (key.equals("screen.columns")) {
                Integer count = positiveInteger(entry.getValue());
                if (count != null && count <= 8) columns.put("", count);
            } else if (key.startsWith("screen.") && key.endsWith(".columns") && key.length() > 15) {
                Integer count = positiveInteger(entry.getValue());
                if (count != null && count <= 8) columns.put(key.substring(7, key.length() - 8), count);
            } else if (key.startsWith("screen.")) {
                subScreens.put(key.substring(7), tokens(entry.getValue()));
            } else if (key.startsWith("program.") && key.endsWith(".enabled") && key.length() > 16) {
                toggles.put(key.substring(8, key.length() - 8), entry.getValue());
            }
        }
        return new ShaderProperties(values, sliders, tokens(values.get("screen")), subScreens, columns, toggles);
    }

    /**
     * Decides whether a program stage runs.
     *
     * <p>Only a literal boolean, a single option name, or a negated option name is evaluated. A longer
     * expression is left enabled, matching the behaviour of a pack whose condition Kernel cannot read;
     * disabling a stage on a guess would silently drop rendering the pack expects.
     */
    public boolean programEnabled(String program, Map<String, String> options) {
        String condition = programToggles.get(program);
        if (condition == null) return true;
        boolean negated = condition.startsWith("!");
        String name = (negated ? condition.substring(1) : condition).strip();
        boolean value;
        if (name.equals("true")) value = true;
        else if (name.equals("false")) value = false;
        else if (TOKEN.matcher(name).matches() && options.containsKey(name)) value = options.get(name).equals("true");
        else return true;
        return negated != value;
    }

    /** Returns the option names this pack lays out on its screens, in order, without duplicates. */
    public List<String> layout() {
        var ordered = new ArrayList<String>();
        var seen = new LinkedHashSet<String>();
        var pending = new ArrayList<String>(screen);
        var visited = new LinkedHashSet<String>();
        while (!pending.isEmpty() && ordered.size() < MAX_SCREEN_ENTRIES) {
            String token = pending.remove(0);
            if (token.equals(EMPTY)) continue;
            if (token.startsWith("[") && token.endsWith("]")) {
                String name = token.substring(1, token.length() - 1);
                if (visited.add(name)) pending.addAll(0, subScreens.getOrDefault(name, List.of()));
            } else if (seen.add(token)) ordered.add(token);
        }
        return List.copyOf(ordered);
    }

    /**
     * Whether a {@code shaders.properties} key describes options or presentation rather than rendering.
     *
     * <p>These keys are owned by this class and are safe to skip elsewhere. Keys that change how a pass
     * actually draws stay unrecognised on purpose, so a pack that needs them is refused rather than
     * rendered differently from what it asked for.
     */
    public static boolean presentational(String key) {
        return key.equals("sliders") || key.equals("screen") || key.startsWith("screen.")
            || key.startsWith("option.") || key.startsWith("value.") || key.startsWith("lang.")
            || key.equals("profiles") || key.startsWith("profile.")
            || key.startsWith("program.") && key.endsWith(".enabled");
    }

    private static int separator(String line) {
        boolean escaped = false;
        for (int index = 0; index < line.length(); index++) {
            char value = line.charAt(index);
            if (escaped) { escaped = false; continue; }
            if (value == '\\') escaped = true;
            else if (value == '=' || value == ':') return index;
        }
        return -1;
    }

    private static List<String> tokens(String value) {
        if (value == null) return List.of();
        var result = new ArrayList<String>();
        for (String token : value.strip().split("[ \\t]+")) {
            if (token.isEmpty() || result.size() >= MAX_SCREEN_ENTRIES) continue;
            boolean sub = token.startsWith("[") && token.endsWith("]") && token.length() > 2;
            if (token.equals(EMPTY) || TOKEN.matcher(sub ? token.substring(1, token.length() - 1) : token).matches()) result.add(token);
        }
        return List.copyOf(result);
    }

    private static Integer positiveInteger(String value) {
        if (value == null || !value.matches("[0-9]{1,3}")) return null;
        int parsed = Integer.parseInt(value);
        return parsed > 0 ? parsed : null;
    }

    /** Returns the pack-declared description for an option, if the pack shipped one in English. */
    public String description(String option) {
        String comment = raw.get("option." + option.toLowerCase(Locale.ROOT) + ".comment");
        return comment == null ? raw.getOrDefault("option." + option + ".comment", "") : comment;
    }
}
