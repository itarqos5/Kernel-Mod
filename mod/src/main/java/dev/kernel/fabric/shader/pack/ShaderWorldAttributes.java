package dev.kernel.fabric.shader.pack;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The vertex attributes the Iris format defines beyond the ones Minecraft's own vertex formats carry.
 *
 * <p>None of these exist in a Minecraft vertex format, so Kernel has to write them itself while a chunk
 * section is meshed. That costs memory and upload bandwidth on every terrain vertex in the world, so
 * Kernel writes only the ones a pack's own programs actually declare rather than all of them always.
 *
 * <p>{@link #MC_ENTITY} and {@link #AT_MID_BLOCK} are written during chunk meshing: the identity a
 * pack's {@code block.properties} gives a block, and the offset from a vertex to the centre of its
 * block that waving foliage is built on. They are the cheapest of the five to produce and store, and
 * they cover what these attributes are mostly used for.
 *
 * <p>The other three stay refused by intent rather than for lack of room. {@link #MC_MID_TEX_COORD} and
 * {@link #AT_TANGENT} exist to drive normal and parallax mapping, which read the LabPBR normal and
 * specular atlases; Kernel does not supply those atlases, so a program given these would light itself
 * from textures that are not there. Supplying an input whose partner is missing produces a pack that
 * renders wrongly, which is worse than one that is declined with a reason.
 */
public enum ShaderWorldAttributes {
    /** The pack's own identity for the block a vertex belongs to, from {@code block.properties}. */
    MC_ENTITY("mc_Entity", true),
    /** The offset from a vertex to the centre of its block, in sixty-fourths of a block. */
    AT_MID_BLOCK("at_midBlock", true),
    /** The texture coordinate at the centre of a quad's sprite, for parallax mapping. */
    MC_MID_TEX_COORD("mc_midTexCoord", false),
    /** The surface tangent and its handedness, for normal mapping. */
    AT_TANGENT("at_tangent", false),
    /** The vertex's movement since the previous frame, for motion blur. */
    AT_VELOCITY("at_velocity", false);

    private final String glslName;
    private final boolean supplied;
    private final Pattern declaration;
    private final Pattern mention;

    ShaderWorldAttributes(String glslName, boolean supplied) {
        this.glslName = glslName;
        this.supplied = supplied;
        // A declaration, in either the legacy or the modern spelling a pack may use.
        this.declaration = Pattern.compile("(?m)^[ \\t]*(?:attribute|in)[ \\t]+[A-Za-z_][A-Za-z0-9_]*[ \\t]+" + glslName + "[ \\t]*;");
        this.mention = Pattern.compile("\\b" + glslName + "\\b");
    }

    /** The name a pack writes in its own program. */
    public String glslName() { return glslName; }

    /** True when Kernel writes this attribute during chunk meshing. */
    public boolean supplied() { return supplied; }

    /**
     * The attributes a vertex program declares, which are the ones Kernel would have to write.
     *
     * <p>Read from declarations rather than from any mention of the name, so a program that only
     * discusses one in a comment or reuses the name for something of its own is not charged for it.
     */
    public static Set<ShaderWorldAttributes> declaredIn(String vertexCode) {
        var declared = EnumSet.noneOf(ShaderWorldAttributes.class);
        if (vertexCode == null) return declared;
        for (var attribute : values()) if (attribute.declaration.matcher(vertexCode).find()) declared.add(attribute);
        return declared;
    }

    /**
     * The attributes a program needs that Kernel does not write, which is why it would be refused.
     *
     * <p>Deliberately broader than {@link #declaredIn}: any mention counts, because a program naming
     * one of these is built around an input Kernel cannot supply whatever shape the reference takes.
     */
    public static Set<ShaderWorldAttributes> unsupportedIn(String code) {
        var missing = EnumSet.noneOf(ShaderWorldAttributes.class);
        if (code == null) return missing;
        for (var attribute : values())
            if (!attribute.supplied && attribute.mention.matcher(code).find()) missing.add(attribute);
        return missing;
    }

    /** The names of a set of attributes, for a message explaining why a pack was declined. */
    public static String names(Set<ShaderWorldAttributes> attributes) {
        var text = new StringBuilder();
        for (var attribute : attributes) {
            if (!text.isEmpty()) text.append(", ");
            text.append(attribute.glslName);
        }
        return text.toString();
    }
}
