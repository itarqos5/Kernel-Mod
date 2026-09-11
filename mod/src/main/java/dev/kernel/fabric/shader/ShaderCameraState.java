package dev.kernel.fabric.shader;

import java.io.IOException;

/** Owned camera samples; horizontal rebasing preserves small motions far from the world origin. */
final class ShaderCameraState {
    private final float[] position = new float[3], previous = new float[3], fraction = new float[3], previousFraction = new float[3];
    private final int[] integer = new int[3], previousInteger = new int[3];
    private double x, y, z, lastX, lastY, lastZ, originX, originZ, nextOriginX, nextOriginZ;
    private int width, height, lastWidth, lastHeight;
    private boolean captured, prepared, history;

    void beginWorld() { captured = prepared = false; }
    void resetHistory() { history = false; beginWorld(); }
    boolean capture(double x, double y, double z) {
        beginWorld();
        if (!valid(x) || !valid(y) || !valid(z)) return false;
        this.x = x; this.y = y; this.z = z; captured = true;
        return true;
    }
    private static boolean valid(double value) {
        return Double.isFinite(value) && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
    }
    /** Returns true when temporal images and matrices must also discard their previous frame. */
    boolean prepare(int width, int height) throws IOException {
        if (!captured || width <= 0 || height <= 0) throw new IOException("This shader requires the current world camera");
        double dx = x - lastX, dy = y - lastY, dz = z - lastZ;
        boolean keep = history && lastWidth == width && lastHeight == height && dx * dx + dy * dy + dz * dz <= 1_000_000;
        nextOriginX = rebase(x, keep ? originX : 0);
        nextOriginZ = rebase(z, keep ? originZ : 0);
        position[0] = (float) (x - nextOriginX); position[1] = (float) y; position[2] = (float) (z - nextOriginZ);
        double px = keep ? lastX : x, py = keep ? lastY : y, pz = keep ? lastZ : z;
        // Re-express both frames in the same origin; crossing a rebase boundary is not camera motion.
        previous[0] = (float) (px - nextOriginX); previous[1] = (float) py; previous[2] = (float) (pz - nextOriginZ);
        split(x, integer, fraction, 0); split(y, integer, fraction, 1); split(z, integer, fraction, 2);
        split(px, previousInteger, previousFraction, 0); split(py, previousInteger, previousFraction, 1); split(pz, previousInteger, previousFraction, 2);
        this.width = width; this.height = height; prepared = true;
        return !keep;
    }
    private static double rebase(double value, double origin) {
        return Math.abs(value - origin) > 30_000 ? Math.floor(value / 30_000 + .5) * 30_000 : origin;
    }
    private static void split(double value, int[] integral, float[] fractional, int axis) {
        double floor = Math.floor(value);
        integral[axis] = (int) floor;
        fractional[axis] = Math.min((float) (value - floor), Math.nextDown(1.0f));
    }
    float[] vector(String name) {
        requirePrepared();
        return switch (name) {
            case "cameraPosition" -> position; case "previousCameraPosition" -> previous;
            case "cameraPositionFract" -> fraction; case "previousCameraPositionFract" -> previousFraction;
            default -> throw new IllegalArgumentException("Unknown camera vector: " + name);
        };
    }
    int[] integer(String name) {
        requirePrepared();
        return switch (name) {
            case "cameraPositionInt" -> integer; case "previousCameraPositionInt" -> previousInteger;
            default -> throw new IllegalArgumentException("Unknown camera integer: " + name);
        };
    }
    float altitude() { requirePrepared(); return (float) y; }
    private void requirePrepared() { if (!prepared) throw new IllegalStateException("Camera uniforms are not prepared"); }
    void complete() {
        requirePrepared();
        lastX = x; lastY = y; lastZ = z; originX = nextOriginX; originZ = nextOriginZ;
        lastWidth = width; lastHeight = height; history = true; beginWorld();
    }
}
