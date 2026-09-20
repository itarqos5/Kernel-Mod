package dev.kernel.fabric.shader.pack;

import java.io.IOException;
import java.util.Objects;

/**
 * The three identity maps a pack may ship, read together because they share one format.
 *
 * <p>A pack declares these to give blocks, entities and items the numeric identities its programs read
 * from {@code mc_Entity}. Kernel keeps them as parsed rules rather than resolved values, because
 * resolution depends on the registries of the moment and has to be redone whenever those change.
 */
public record ShaderIdentifierMaps(ShaderIdentifierRules blocks, ShaderIdentifierRules entities, ShaderIdentifierRules items) {
    public ShaderIdentifierMaps {
        Objects.requireNonNull(blocks);
        Objects.requireNonNull(entities);
        Objects.requireNonNull(items);
    }

    public static ShaderIdentifierMaps empty() {
        return new ShaderIdentifierMaps(ShaderIdentifierRules.empty(), ShaderIdentifierRules.empty(), ShaderIdentifierRules.empty());
    }

    /** True when the pack gave nothing an identity, so nothing has to be resolved or written. */
    public boolean isEmpty() { return blocks.isEmpty() && entities.isEmpty() && items.isEmpty(); }

    /** Reads whichever of the three files the pack ships; a missing one is an empty map, not a failure. */
    public static ShaderIdentifierMaps read(ShaderPackArchive archive) throws IOException {
        return new ShaderIdentifierMaps(
            ShaderIdentifierRules.parse(source(archive, "block.properties"), ShaderIdentifierRules.BLOCK),
            ShaderIdentifierRules.parse(source(archive, "entity.properties"), ShaderIdentifierRules.ENTITY),
            ShaderIdentifierRules.parse(source(archive, "item.properties"), ShaderIdentifierRules.ITEM));
    }

    private static String source(ShaderPackArchive archive, String name) throws IOException {
        return archive.contains(name) ? archive.source(name) : null;
    }
}
