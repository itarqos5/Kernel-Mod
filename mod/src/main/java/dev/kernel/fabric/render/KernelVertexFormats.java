package dev.kernel.fabric.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import dev.kernel.fabric.shader.pack.ShaderWorldAttributes;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * The terrain vertex format extended with the attributes a shader pack asked for.
 *
 * <p>Minecraft's block format carries position, colour, texture and lightmap coordinates and a normal.
 * The Iris format defines further attributes that no Minecraft format has, so a pack that reads one can
 * only be drawn from a format Kernel builds. Building it from the game's own block format rather than
 * from a written-out element list means a version that changes that format is followed rather than
 * contradicted.
 *
 * <p>Only the attributes a pack's own programs declare are added. These cost bytes on every terrain
 * vertex in view, and a pack that reads none pays nothing: the format then is the game's own object, so
 * every identity comparison Minecraft makes against it still holds and its fast vertex path still runs.
 *
 * <p>Element ids are claimed from whatever the game has left free rather than fixed, because the id
 * space is shared with anything else that registers an element and a fixed choice would collide.
 *
 * <p>The vertex format API is rewritten twice across the supported targets: 1.21.11 and earlier register
 * an element by id, usage and count, 26.1 replaces the usage with a flag, and 26.2 drops the registry
 * entirely for named elements carrying their own offset and GPU format. Kernel extends the format only
 * on the targets whose world stage runs at all, which are the ones declaring their matrices as plain
 * uniforms rather than in a uniform block; on the rest {@link #terrain} is the game's own format and
 * nothing here is reached. Following all three APIs for versions that refuse world programs anyway
 * would be carrying code no pack can use.
 */
public final class KernelVertexFormats {
    /** Two shorts: the pack's block identity, and a second component reserved but written as zero. */
    private static final int ENTITY_COUNT = 2;
    /** Three bytes of offset to the block centre, with one byte of padding to keep the vertex aligned. */
    private static final int MID_BLOCK_COUNT = 3;

    private static final Map<ShaderWorldAttributes, VertexFormatElement> ELEMENTS = new EnumMap<>(ShaderWorldAttributes.class);
    private static final Map<Set<ShaderWorldAttributes>, VertexFormat> FORMATS = new java.util.concurrent.ConcurrentHashMap<>();

    private KernelVertexFormats() {}

    /**
     * Claims an element id for each attribute Kernel can write.
     *
     * <p>Registration is global and permanent, so this runs once during client start and never in
     * response to a pack. A pack changes which elements a format uses, never which ones exist.
     */
    public static synchronized void register() {
        //? if >1.21.4 && <=1.21.10 {
        /*if (!ELEMENTS.isEmpty()) return;
        ELEMENTS.put(ShaderWorldAttributes.MC_ENTITY, claim(VertexFormatElement.Type.SHORT, ENTITY_COUNT));
        ELEMENTS.put(ShaderWorldAttributes.AT_MID_BLOCK, claim(VertexFormatElement.Type.BYTE, MID_BLOCK_COUNT));
        *///? }
    }

    /** The element Kernel writes for one attribute, or null when it writes none. */
    public static VertexFormatElement element(ShaderWorldAttributes attribute) { return ELEMENTS.get(attribute); }

    /**
     * The terrain format for a set of demanded attributes.
     *
     * <p>Returns the game's own block format when nothing is demanded, so the common case is not merely
     * equal to vanilla's but identical to it.
     */
    public static VertexFormat terrain(Set<ShaderWorldAttributes> demanded) {
        var wanted = writable(demanded);
        if (wanted.isEmpty()) return DefaultVertexFormat.BLOCK;
        return FORMATS.computeIfAbsent(java.util.EnumSet.copyOf(wanted), KernelVertexFormats::build);
    }

    /** The demanded attributes Kernel has an element for, in a stable order. */
    private static Set<ShaderWorldAttributes> writable(Set<ShaderWorldAttributes> demanded) {
        var wanted = java.util.EnumSet.noneOf(ShaderWorldAttributes.class);
        if (demanded == null) return wanted;
        for (var attribute : demanded) if (ELEMENTS.containsKey(attribute)) wanted.add(attribute);
        return wanted;
    }

    private static VertexFormat build(Set<ShaderWorldAttributes> wanted) {
        //? if >=1.21.11 || <=1.21.4 {
        // No element is ever registered on these targets, so nothing reaches this.
        return DefaultVertexFormat.BLOCK;
        //? } else {
        /*VertexFormat source = DefaultVertexFormat.BLOCK;
        var builder = VertexFormat.builder();
        int declared = 0;
        for (var element : source.getElements()) {
            builder.add(source.getElementName(element), element);
            declared += element.byteSize();
        }
        // Reproduce whatever padding the game's own format ends with, so the elements Kernel appends
        // begin where vanilla's vertex ended rather than inside it.
        if (source.getVertexSize() > declared) builder.padding(source.getVertexSize() - declared);
        int added = 0;
        for (var attribute : wanted) {
            var element = ELEMENTS.get(attribute);
            builder.add(attribute.glslName(), element);
            added += element.byteSize();
        }
        // Keep the vertex a multiple of four bytes. An unaligned stride costs more on every read than
        // the padding costs in memory.
        int overhang = added % 4;
        if (overhang != 0) builder.padding(4 - overhang);
        return builder.build();
        *///? }
    }

    //? if >1.21.4 && <=1.21.10 {
    /*private static VertexFormatElement claim(VertexFormatElement.Type type, int count) {
        for (int id = 0; id < VertexFormatElement.MAX_COUNT; id++) {
            if (VertexFormatElement.byId(id) != null) continue;
            return VertexFormatElement.register(id, 0, type, VertexFormatElement.Usage.GENERIC, count);
        }
        throw new IllegalStateException("No vertex element ids are left for Kernel's shader attributes");
    }
    *///? }
}
