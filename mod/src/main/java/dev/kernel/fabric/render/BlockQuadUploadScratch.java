package dev.kernel.fabric.render;

/**
 * Reusable four-vertex lighting inputs for the legacy block-model upload path.
 */
public final class BlockQuadUploadScratch {
    private final float[] brightness = new float[4];
    private final int[] light = new int[4];

    public float[] brightness(float first, float second, float third, float fourth) {
        this.brightness[0] = first;
        this.brightness[1] = second;
        this.brightness[2] = third;
        this.brightness[3] = fourth;
        return this.brightness;
    }

    public int[] light(int first, int second, int third, int fourth) {
        this.light[0] = first;
        this.light[1] = second;
        this.light[2] = third;
        this.light[3] = fourth;
        return this.light;
    }
}
