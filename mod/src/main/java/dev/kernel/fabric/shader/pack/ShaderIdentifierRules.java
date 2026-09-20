package dev.kernel.fabric.shader.pack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The {@code block.properties}, {@code entity.properties} and {@code item.properties} maps a pack ships.
 *
 * <p>These give game objects the numeric identities a pack's programs read from {@code mc_Entity}. A
 * line reads {@code block.<value> = <entry> <entry> ...}, where an entry names a game object and may
 * constrain it to particular states, as in {@code minecraft:grass_block:snowy=true}. One value may be
 * claimed by many entries and one file may claim many values.
 *
 * <p>Parsing is separated from resolution on purpose. Nothing here touches a registry or a game object,
 * so the rules can be read while a pack is prepared, off the render thread and without a world; turning
 * them into the flat lookup the chunk builder uses happens later, against the registries of the moment.
 *
 * <p>An entry naming something this game does not have is kept rather than rejected. Packs routinely
 * list blocks from mods that are not installed, and a pack is not wrong for doing so; it simply maps
 * nothing here. Lines that are not valid syntax at all are skipped, because refusing a whole pack over
 * one unreadable line would deny a pack that works.
 */
public record ShaderIdentifierRules(List<Rule> rules) {
    /** More rules than any real pack declares; the bound is against a hostile file, not a large one. */
    public static final int MAX_RULES = 16384;
    /** The largest identity a pack may claim. Values are read as floats by shader programs. */
    public static final int MAX_VALUE = 65535;
    public static final String BLOCK = "block", ENTITY = "entity", ITEM = "item";

    public ShaderIdentifierRules { rules = List.copyOf(rules); }

    public static ShaderIdentifierRules empty() { return new ShaderIdentifierRules(List.of()); }

    public boolean isEmpty() { return rules.isEmpty(); }

    /**
     * One entry of one line: the identity, what it names, and the states it is confined to.
     *
     * <p>An empty {@code states} map matches every state of the object. Otherwise every named property
     * must hold one of the listed values, so {@code minecraft:leaves:persistent=true,false} matches both
     * and {@code minecraft:leaves:persistent=true} matches one.
     */
    public record Rule(int value, String namespace, String path, Map<String, Set<String>> states) {
        public Rule {
            states = Map.copyOf(states);
        }
        /** True when this rule constrains nothing beyond the object's identity. */
        public boolean everyState() { return states.isEmpty(); }
        /** True when the given state properties satisfy every constraint this rule makes. */
        public boolean matches(Map<String, String> state) {
            for (var constraint : states.entrySet()) {
                String held = state.get(constraint.getKey());
                if (held == null || !constraint.getValue().contains(held)) return false;
            }
            return true;
        }
    }

    /**
     * Reads one properties file, keeping only the lines whose key carries the given prefix.
     *
     * @param source the file's text, or null when the pack does not ship it
     * @param prefix {@link #BLOCK}, {@link #ENTITY} or {@link #ITEM}
     */
    public static ShaderIdentifierRules parse(String source, String prefix) {
        if (source == null || source.isEmpty()) return empty();
        var rules = new ArrayList<Rule>();
        for (String line : source.split("\r\n|\r|\n")) {
            String text = line.strip();
            if (text.isEmpty() || text.startsWith("#") || text.startsWith("//")) continue;
            int equals = text.indexOf('=');
            if (equals <= 0) continue;
            int value = identity(text.substring(0, equals).strip(), prefix);
            if (value < 0) continue;
            for (String entry : text.substring(equals + 1).strip().split("[ \t]+")) {
                if (entry.isEmpty()) continue;
                if (rules.size() >= MAX_RULES) return new ShaderIdentifierRules(rules);
                Rule rule = rule(value, entry);
                if (rule != null) rules.add(rule);
            }
        }
        return new ShaderIdentifierRules(rules);
    }

    /** The numeric identity a key claims, or -1 when the key is not one of this file's. */
    private static int identity(String key, String prefix) {
        if (!key.startsWith(prefix) || key.length() <= prefix.length() + 1 || key.charAt(prefix.length()) != '.') return -1;
        String digits = key.substring(prefix.length() + 1);
        // Only plain decimal identities: a key such as block.1.2 is not this format.
        for (int index = 0; index < digits.length(); index++)
            if (digits.charAt(index) < '0' || digits.charAt(index) > '9') return -1;
        if (digits.isEmpty() || digits.length() > 5) return -1;
        int value = Integer.parseInt(digits);
        return value > MAX_VALUE ? -1 : value;
    }

    /** One entry, or null when it is not syntax this format defines. */
    private static Rule rule(int value, String entry) {
        String[] parts = entry.split(":", -1);
        if (parts.length == 0 || parts[0].isEmpty() || parts[0].indexOf('=') >= 0) return null;
        String namespace = "minecraft", path = parts[0];
        int next = 1;
        // A second colon-separated word without an '=' is a path, so the first word was the namespace.
        if (parts.length > 1 && !parts[1].isEmpty() && parts[1].indexOf('=') < 0) {
            namespace = parts[0];
            path = parts[1];
            next = 2;
        }
        if (path.isEmpty()) return null;
        var states = new LinkedHashMap<String, Set<String>>();
        for (int index = next; index < parts.length; index++) {
            String constraint = parts[index];
            int equals = constraint.indexOf('=');
            if (equals <= 0 || equals == constraint.length() - 1) return null;
            var held = new LinkedHashSet<String>();
            for (String held0 : constraint.substring(equals + 1).split(",", -1)) {
                if (held0.isEmpty()) return null;
                held.add(held0);
            }
            // A property named twice constrains twice; keep the union, as listing it twice asks for both.
            states.merge(constraint.substring(0, equals), held, (first, second) -> {
                var union = new LinkedHashSet<>(first);
                union.addAll(second);
                return union;
            });
        }
        return new Rule(value, namespace, path, states);
    }
}
