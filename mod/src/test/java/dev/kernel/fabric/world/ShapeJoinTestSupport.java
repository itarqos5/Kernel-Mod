package dev.kernel.fabric.world;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Random;

/** Executes the target JAR's original private traversal, even in a transforming Fabric classloader. */
public final class ShapeJoinTestSupport {
    static final Class<?>[] PARAMETERS = {IndexMerger.class, IndexMerger.class, IndexMerger.class,
        DiscreteVoxelShape.class, DiscreteVoxelShape.class, BooleanOp.class};
    static final MethodHandle NATIVE = nativeTraversal();
    static final MethodHandle LIVE = method(Shapes.class, "joinIsNotEmpty", PARAMETERS);
    private static final MethodHandle MERGER = method(Shapes.class, "createIndexMerger", int.class,
        DoubleList.class, DoubleList.class, boolean.class, boolean.class);

    private ShapeJoinTestSupport() {}

    static MethodHandle method(Class<?> owner, String name, Class<?>... parameters) {
        try {
            var method = owner.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return MethodHandles.lookup().unreflect(method);
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }

    private static MethodHandle nativeTraversal() {
        try (var input = Shapes.class.getResourceAsStream("Shapes.class")) {
            var source = new ClassNode();
            new ClassReader(input).accept(source, 0);
            var writer = new ClassWriter(0);
            var remapper = new ClassRemapper(writer, new SimpleRemapper(source.name,
                "dev/kernel/fabric/world/KernelNativeShapeTraversal"));
            remapper.visit(source.version, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, source.name, null, "java/lang/Object", null);
            var root = source.methods.stream().filter(method -> method.name.equals("joinIsNotEmpty")
                && method.desc.startsWith("(Lnet/minecraft/world/phys/shapes/IndexMerger;")).findFirst().orElseThrow();
            var pending = new java.util.ArrayDeque<org.objectweb.asm.tree.MethodNode>();
            var copied = new java.util.HashSet<String>();
            pending.add(root);
            while (!pending.isEmpty()) {
                var method = pending.removeFirst();
                if (!copied.add(method.name + method.desc)) continue;
                method.accept(remapper);
                // Older official mappings give synthetic lambda bodies generated method names.
                for (var instruction : method.instructions) if (instruction instanceof org.objectweb.asm.tree.InvokeDynamicInsnNode dynamic)
                    for (var argument : dynamic.bsmArgs) if (argument instanceof org.objectweb.asm.Handle handle && handle.getOwner().equals(source.name))
                        pending.add(source.methods.stream().filter(candidate -> candidate.name.equals(handle.getName())
                            && candidate.desc.equals(handle.getDesc())).findFirst().orElseThrow());
            }
            if (copied.size() != 4) throw new AssertionError("Unexpected native shape callback structure: " + copied);
            remapper.visitEnd();
            return method(MethodHandles.lookup().defineClass(writer.toByteArray()), "joinIsNotEmpty", PARAMETERS);
        } catch (Exception exception) { throw new AssertionError(exception); }
    }

    static boolean reference(IndexMerger x, IndexMerger y, IndexMerger z, DiscreteVoxelShape a,
                             DiscreteVoxelShape b, BooleanOp op) throws Throwable {
        return (boolean) NATIVE.invokeExact(x, y, z, a, b, op);
    }

    static void differential(int seed, int count, boolean live) throws Throwable {
        var random = new Random(seed);
        for (int iteration = 0; iteration < count; iteration++) {
            int sizeA = 1 + random.nextInt(8), sizeB = 1 + random.nextInt(8);
            var a = new BitSetDiscreteVoxelShape(sizeA, sizeA, sizeA);
            var b = new BitSetDiscreteVoxelShape(sizeB, sizeB, sizeB);
            fill(a, sizeA, random);
            fill(b, sizeB, random);
            int mask = iteration & 15;
            BooleanOp op = (first, second) -> (mask & (1 << ((first ? 2 : 0) | (second ? 1 : 0)))) != 0;
            IndexMerger[] axes = new IndexMerger[3];
            int cost = 1;
            for (int axis = 0; axis < 3; axis++) {
                DoubleList coordsA = iteration % 5 == 0 ? cubeCoordinates(a) : coordinates(sizeA, random);
                DoubleList coordsB = iteration % 5 == 0 ? cubeCoordinates(b) : coordinates(sizeB, random);
                if (iteration % 7 == 0) coordsB = coordsA;
                axes[axis] = (IndexMerger) MERGER.invokeExact(cost, coordsA, coordsB, op.apply(true, false), op.apply(false, true));
                cost *= axes[axis].size() - 1;
            }
            boolean expected = reference(axes[0], axes[1], axes[2], a, b, op);
            boolean actual = live ? (boolean) LIVE.invokeExact(axes[0], axes[1], axes[2], (DiscreteVoxelShape) a, (DiscreteVoxelShape) b, op)
                : ShapeJoinTraversal.test(axes[0], axes[1], axes[2], a, b, op);
            if (actual != expected) throw new AssertionError("Shape traversal differs at seed " + seed + ", iteration " + iteration);
        }
    }

    private static void fill(BitSetDiscreteVoxelShape shape, int size, Random random) {
        for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) for (int z = 0; z < size; z++)
            if (random.nextInt(8) == 0) shape.fill(x, y, z);
    }

    private static DoubleList coordinates(int size, Random random) {
        double[] values = new double[size + 1];
        double start = random.nextInt(8) - 4;
        for (int i = 0; i <= size; i++) values[i] = start + i * (random.nextBoolean() ? 1.0 : 1.0E-8);
        java.util.Arrays.sort(values);
        if (random.nextInt(12) == 0) values[0] = Double.NEGATIVE_INFINITY;
        if (random.nextInt(12) == 0) values[size] = Double.POSITIVE_INFINITY;
        return DoubleArrayList.wrap(values);
    }

    static IndexMerger identity(int size) {
        return new IdenticalMerger(DoubleArrayList.wrap(new double[size + 1]));
    }

    private static DoubleList cubeCoordinates(DiscreteVoxelShape shape) throws ReflectiveOperationException {
        var constructor = CubeVoxelShape.class.getDeclaredConstructor(DiscreteVoxelShape.class);
        constructor.setAccessible(true);
        return constructor.newInstance(shape).getCoords(Direction.Axis.X);
    }
}
