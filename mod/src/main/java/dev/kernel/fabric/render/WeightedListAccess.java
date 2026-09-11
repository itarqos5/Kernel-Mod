package dev.kernel.fabric.render;

/** Native constructor-computed total; custom weighted lists retain their own dispatch. */
public interface WeightedListAccess {
    int kernel$totalWeight();
    java.util.List<?> kernel$entries();
}
