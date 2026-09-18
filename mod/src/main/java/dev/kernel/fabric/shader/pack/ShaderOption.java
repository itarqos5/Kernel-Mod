package dev.kernel.fabric.shader.pack;

import java.util.List;

/**
 * One pack-declared option discovered in shader source, using the Iris/OptiFine declaration syntax.
 *
 * <p>A {@code BOOLEAN} option is a bare {@code #define NAME} that the pack ships either enabled or
 * commented out. A {@code VALUE} option is a {@code #define NAME value} or {@code const int NAME = value;}
 * followed by a {@code //[a b c]} list of the values the pack accepts. Kernel never invents values that
 * the pack did not list.
 */
public record ShaderOption(String name, Kind kind, String defaultValue, List<String> values, String comment, boolean slider) {
    public enum Kind { BOOLEAN, VALUE }

    public ShaderOption {
        java.util.Objects.requireNonNull(name);
        java.util.Objects.requireNonNull(kind);
        java.util.Objects.requireNonNull(defaultValue);
        values = List.copyOf(values);
        comment = comment == null ? "" : comment;
        if (kind == Kind.BOOLEAN ? !values.equals(List.of("false", "true")) : values.size() < 2)
            throw new IllegalArgumentException("A value option requires at least two declared values");
        if (!values.contains(defaultValue)) throw new IllegalArgumentException("The default value is not one of the declared values");
    }

    /** Returns this option with the slider presentation requested by {@code shaders.properties}. */
    public ShaderOption asSlider(boolean requested) {
        return requested == slider ? this : new ShaderOption(name, kind, defaultValue, values, comment, requested);
    }

    public boolean accepts(String value) { return values.contains(value); }

    /** Returns the value one step after {@code current}, wrapping at the end of the declared list. */
    public String cycle(String current, int direction) {
        int index = values.indexOf(current);
        return values.get(Math.floorMod((index < 0 ? values.indexOf(defaultValue) : index) + direction, values.size()));
    }
}
