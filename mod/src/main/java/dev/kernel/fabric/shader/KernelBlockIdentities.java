package dev.kernel.fabric.shader;

import dev.kernel.fabric.shader.pack.ShaderIdentifierRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The numeric identity a shader pack gives each block state, resolved against the current registries.
 *
 * <p>A pack's {@code block.properties} names blocks; the chunk builder needs a number per vertex. The
 * gap between them is a registry lookup and a state match, neither of which may happen while meshing:
 * a section build runs on worker threads, many times a second, for every block it visits. So the whole
 * map is resolved once into a flat table indexed by block state id, and meshing does one array read.
 *
 * <p>The table is rebuilt rather than patched whenever the pack or the registries change. Registries
 * are rebuilt when a world loads with different data packs, and a stale table would give blocks the
 * identities of whatever occupied those ids before.
 *
 * <p>Blocks a pack does not name keep identity zero, which is what a pack's own programs expect for
 * "not one of mine".
 */
public final class KernelBlockIdentities {
    private static final int[] NONE = new int[0];
    /** Indexed by block state id; zero where the pack named nothing. */
    private static volatile int[] identities = NONE;
    private static volatile ShaderIdentifierRules declared = ShaderIdentifierRules.empty();
    /** The registry size the table was built against, so a registry rebuild can be noticed. */
    private static volatile int builtFor = -1;

    private KernelBlockIdentities() {}

    /** True when a pack named at least one block that this game actually has. */
    public static boolean active() { return identities.length != 0; }

    /**
     * The identity a pack gave this block state, or zero when it named none.
     *
     * <p>Called once per block visited by a section build, so it does no work beyond an array read.
     */
    public static int identity(BlockState state) {
        int[] table = identities;
        if (table.length == 0) return 0;
        int id = Block.getId(state);
        return id >= 0 && id < table.length ? table[id] : 0;
    }

    /** Adopts a pack's rules, or clears them when shaders are switched off. */
    public static void adopt(ShaderIdentifierRules rules) {
        declared = rules == null ? ShaderIdentifierRules.empty() : rules;
        builtFor = -1;
        rebuild();
    }

    /**
     * Rebuilds the table if the registries have changed since it was built.
     *
     * <p>Cheap enough to call on a world change: it compares one integer and returns unless the
     * registry has actually been rebuilt underneath the table.
     */
    public static void refresh() {
        if (declared.isEmpty() || Block.BLOCK_STATE_REGISTRY.size() == builtFor) return;
        rebuild();
    }

    private static void rebuild() {
        var rules = declared;
        if (rules.isEmpty()) { identities = NONE; builtFor = -1; return; }
        // Group by the name a rule names, so the registry is walked once rather than once per rule.
        var byName = new HashMap<String, List<ShaderIdentifierRules.Rule>>();
        for (var rule : rules.rules())
            byName.computeIfAbsent(rule.namespace() + ":" + rule.path(), name -> new ArrayList<>()).add(rule);
        int size = Block.BLOCK_STATE_REGISTRY.size();
        var table = new int[size];
        for (Block block : BuiltInRegistries.BLOCK) {
            var key = BuiltInRegistries.BLOCK.getKey(block);
            if (key == null) continue;
            var matching = byName.get(key.getNamespace() + ":" + key.getPath());
            // The overwhelming majority of blocks are not named by any pack, so this is the fast path.
            if (matching == null) continue;
            var properties = block.getStateDefinition().getProperties();
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                int id = Block.getId(state);
                if (id < 0 || id >= size) continue;
                // Read the state's properties at most once, and only when some rule constrains them.
                Map<String, String> held = null;
                for (var rule : matching) {
                    if (!rule.everyState()) {
                        if (held == null) held = stateOf(state, properties);
                        if (!rule.matches(held)) continue;
                    }
                    // First rule in the pack's own order wins, so a pack's file reads top to bottom.
                    table[id] = rule.value();
                    break;
                }
            }
        }
        identities = table;
        builtFor = size;
    }

    /** The property values of one state, as the names a pack writes in its own file. */
    private static Map<String, String> stateOf(BlockState state, java.util.Collection<Property<?>> properties) {
        var held = new HashMap<String, String>(properties.size() * 2);
        for (Property<?> property : properties) held.put(property.getName(), valueName(property, state.getValue(property)));
        return held;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String valueName(Property property, Comparable value) { return property.getName(value); }
}
