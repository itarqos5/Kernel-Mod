package dev.kernel.fabric.render;

import com.mojang.blaze3d.vertex.VertexSorting;
//? if >=1.21.9 {
import com.mojang.blaze3d.vertex.CompactVectorArray;
//? }
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.stream.IntStream;

/** Loaded only through Knot's transforming game classloader. No JUnit launcher is involved. */
public final class VertexSortingSmokeChecks {
    public static void run() throws ClassNotFoundException {
        FrustumSmokeChecks.run(true);
        WeightedModelSmokeChecks.run(true);
        //? if <=1.21.4 {
        /*Class.forName("com.mojang.blaze3d.vertex.VertexBuffer", false, VertexSortingSmokeChecks.class.getClassLoader()).getDeclaredMethods();
        Class.forName("net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection", false, VertexSortingSmokeChecks.class.getClassLoader()).getDeclaredMethods();
        *///? }
        // Resolve every renderer target without initializing Minecraft or a graphics device. This
        // catches Mixin structural failures which helper-only unit tests cannot detect.
        for (String name : new String[] {
            "com.mojang.blaze3d.vertex.VertexConsumer", "com.mojang.blaze3d.vertex.PoseStack",
            "com.mojang.blaze3d.vertex.PoseStack$Pose", "net.minecraft.client.model.geom.ModelPart",
            "net.minecraft.client.model.geom.ModelPart$Cube", "net.minecraft.client.renderer.block.ModelBlockRenderer",
            "net.minecraft.world.level.block.Block", "net.minecraft.client.renderer.chunk.SectionRenderDispatcher",
            "net.minecraft.client.renderer.chunk.VisGraph"
        }) Class.forName(name, false, VertexSortingSmokeChecks.class.getClassLoader()).getDeclaredMethods();
        //? if >=26 {
        Class.forName("net.minecraft.client.renderer.block.FluidRenderer", false, VertexSortingSmokeChecks.class.getClassLoader()).getDeclaredMethods();
        //? } else {
        /*Class.forName("net.minecraft.client.renderer.block.LiquidBlockRenderer", false, VertexSortingSmokeChecks.class.getClassLoader()).getDeclaredMethods();
        *///? }
        //? if <26 {
        /*verifyConvenienceArrayOwnership();
        *///? }
        if (!(VertexSorting.DISTANCE_TO_ORIGIN instanceof KernelVertexSorting)
            || !(VertexSorting.ORTHOGRAPHIC_Z instanceof KernelVertexSorting)
            || !(VertexSorting.byDistance(1, 2, 3) instanceof KernelVertexSorting)) {
            throw new AssertionError("Kernel's sorting factory was not applied before static initialization");
        }
        float[] keys = new float[1025];
        for (int index = 0; index < keys.length; index++) keys[index] = (index * 17) % 13;
        keys[1] = Float.NaN;
        keys[17] = -0.0f;
        keys[201] = Float.intBitsToFloat(0xff800001);
        keys[202] = Float.NEGATIVE_INFINITY;
        //? if >=1.21.9 {
        CompactVectorArray points = new CompactVectorArray(keys.length);
        for (int index = 0; index < keys.length; index++) points.set(index, keys[index], 0, 0);
        //? } else {
        /*Vector3f[] points = new Vector3f[keys.length];
        for (int index = 0; index < keys.length; index++) points[index] = new Vector3f(keys[index], 0, 0);
        *///? }
        int[] calls = {0};
        VertexSorting sorting = VertexSorting.byDistance(point -> {
            calls[0]++;
            return point.x();
        });
        if (!(sorting instanceof KernelVertexSorting)) throw new AssertionError("Custom distance factory did not use Kernel");
        Integer[] reference = IntStream.range(0, keys.length).boxed().toArray(Integer[]::new);
        Arrays.sort(reference, (first, second) -> Float.compare(keys[second], keys[first]));
        int[] expected = Arrays.stream(reference).mapToInt(Integer::intValue).toArray();
        if (!Arrays.equals(expected, sorting.sort(points)) || calls[0] != keys.length) {
            throw new AssertionError("Transformed factory produced incorrect ordering or callback count");
        }
        VertexSorting custom = ignored -> new int[]{42};
        if (!Arrays.equals(new int[]{42}, custom.sort(points))) throw new AssertionError("Custom sorting implementation changed");
        System.out.println("Kernel vertex sorting: real Fabric/Mixin factory smoke test passed.");
    }

    //? if <26 {
    /*private static void verifyConvenienceArrayOwnership() {
        try {
            Class<?> consumer = com.mojang.blaze3d.vertex.VertexConsumer.class;
            var convenience = consumer.getMethod("putBulkData", com.mojang.blaze3d.vertex.PoseStack.Pose.class,
                net.minecraft.client.renderer.block.model.BakedQuad.class,
                float.class, float.class, float.class, float.class, int.class, int.class);
            java.util.List<int[]> retained = new java.util.ArrayList<>();
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(consumer.getClassLoader(), new Class<?>[]{consumer}, (target, method, arguments) -> {
                if (method.getName().equals("putBulkData") && method.getParameterTypes()[2] == float[].class) {
                    retained.add((int[]) arguments[7]);
                    if (retained.size() == 1) convenience.invoke(target, null, null, 1F, 1F, 1F, 1F, 22, 0);
                    return null;
                }
                if (method.isDefault()) return java.lang.reflect.InvocationHandler.invokeDefault(target, method, arguments);
                return target;
            });
            convenience.invoke(proxy, null, null, 1F, 1F, 1F, 1F, 11, 0);
            convenience.invoke(proxy, null, null, 1F, 1F, 1F, 1F, 33, 0);
            if (retained.size() != 3 || retained.get(0) == retained.get(1) || retained.get(0) == retained.get(2)
                || !Arrays.equals(retained.get(0), new int[]{11, 11, 11, 11})
                || !Arrays.equals(retained.get(1), new int[]{22, 22, 22, 22})) {
                throw new AssertionError("Nested or retained custom-consumer arrays were overwritten");
            }
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }
    *///? }
}
