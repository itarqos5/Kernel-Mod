package dev.kernel.fabric.world;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandle;
import java.util.Random;

/** Test-only construction skips the world-dependent constructor; allocateSlice reads no instance fields. */
public final class NoiseSliceSmokeChecks {
    private static volatile double[][] sink;
    public static void run(boolean expected) throws Throwable {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        Class<?> type = Class.forName("net.minecraft.world.level.levelgen.NoiseChunk$NoiseInterpolator");
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        Object instance = unsafe.allocateInstance(type);
        var method = type.getDeclaredMethod("allocateSlice", int.class, int.class); method.setAccessible(true);
        MethodHandle allocate = MethodHandles.lookup().unreflect(method).bindTo(instance);
        var random = new Random(173241);
        for (int i = 0; i < 128; i++) {
            int y = random.nextInt(129)-1, z = random.nextInt(17)-1;
            double[][] a = (double[][]) allocate.invokeExact(y,z), b = (double[][]) allocate.invokeExact(y,z);
            if (a.length != z+1 || a==b) throw new AssertionError("Slice shape or ownership changed");
            for (int row=0;row<a.length;row++) {
                if(a[row].length!=y+1 || a[row]==b[row]) throw new AssertionError("Row ownership changed");
                for(int other=0;other<row;other++) if(a[row]==a[other]) throw new AssertionError("Rows alias");
                for(double value:a[row]) if(Double.doubleToRawLongBits(value)!=0) throw new AssertionError("Slice is not positive-zero initialized");
            }
        }
        for (int[] bad: new int[][]{{-2,1},{1,-2},{Integer.MAX_VALUE,1},{1,Integer.MAX_VALUE}}) {
            try { sink=(double[][])allocate.invokeExact(bad[0],bad[1]); throw new AssertionError("Negative dimensions accepted"); }
            catch(NegativeArraySizeException correct) { }
        }
        // Array headers vary across JVM layouts; this separates one versus two complete sets of 5x49 rows.
        java.util.function.LongPredicate band = bytes -> expected ? bytes >= 1980 && bytes <= 2500 : bytes >= 3900 && bytes <= 4700;
        long[] measured = dev.kernel.fabric.verification.AllocationProbe.settle(
            () -> sink = (double[][]) allocate.invokeExact(48, 4), 20000, 10000,
            totals -> band.test(totals[0] / 10000),
            () -> sink = (double[][]) allocate.invokeExact(48, 4));
        long bytes = measured[0] / 10000;
        if(!band.test(bytes)) throw new AssertionError("Unexpected slice allocation: "+bytes+" bytes; enabled="+expected);
        System.out.println("Kernel noise slice: enabled="+expected+", "+bytes+" bytes/slice; shape, zero values, independent ownership and exceptions passed.");
    }
}
