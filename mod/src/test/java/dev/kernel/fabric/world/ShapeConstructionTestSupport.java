package dev.kernel.fabric.world;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.phys.shapes.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;

/** Loads the installed target JAR's original join and result methods into an isolated test class. */
final class ShapeConstructionTestSupport {
    private static final Class<?>[] PARAMETERS = {DiscreteVoxelShape.class, DiscreteVoxelShape.class,
        IndexMerger.class, IndexMerger.class, IndexMerger.class, BooleanOp.class};
    private static final MethodType TYPE = MethodType.methodType(DiscreteVoxelShape.class, PARAMETERS);
    static final MethodHandle LIVE = ShapeJoinTestSupport.method(BitSetDiscreteVoxelShape.class, "join", PARAMETERS).asType(TYPE);
    static final MethodHandle NATIVE = reference();
    private ShapeConstructionTestSupport() {}

    private static MethodHandle reference() {
        try (var input = BitSetDiscreteVoxelShape.class.getResourceAsStream("BitSetDiscreteVoxelShape.class")) {
            var source = new ClassNode(); new ClassReader(input).accept(source, 0);
            var writer = new ClassWriter(0);
            var remapper = new ClassRemapper(writer, new SimpleRemapper(source.name, "dev/kernel/fabric/world/KernelNativeShapeConstruction"));
            remapper.visit(source.version, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, source.name, null, source.superName, null);
            for (var field : source.fields) field.accept(remapper);
            Set<String> instanceMethods = Set.of("getIndex", "fillUpdateBounds", "isFull", "fill", "isEmpty", "firstFull", "lastFull");
            var pending = new ArrayDeque<MethodNode>();
            for (var method : source.methods) if (instanceMethods.contains(method.name)
                || method.name.equals("<init>") && method.desc.equals("(III)V") || method.name.equals("join")) pending.add(method);
            var copied = new HashSet<String>();
            while (!pending.isEmpty()) {
                var method = pending.removeFirst();
                if (!copied.add(method.name + method.desc)) continue;
                method.accept(remapper);
                for (var instruction : method.instructions) if (instruction instanceof InvokeDynamicInsnNode dynamic)
                    for (var argument : dynamic.bsmArgs) if (argument instanceof Handle handle && handle.getOwner().equals(source.name))
                        pending.add(source.methods.stream().filter(candidate -> candidate.name.equals(handle.getName())
                            && candidate.desc.equals(handle.getDesc())).findFirst().orElseThrow());
            }
            if (copied.size() != 12 || copied.stream().anyMatch(name -> name.contains("kernel$")))
                throw new AssertionError("Unexpected native construction structure: " + copied);
            remapper.visitEnd();
            return ShapeJoinTestSupport.method(MethodHandles.lookup().defineClass(writer.toByteArray()), "join", PARAMETERS).asType(TYPE);
        } catch (Exception failure) { throw new AssertionError("Cannot load original shape construction", failure); }
    }

    static DiscreteVoxelShape join(boolean reference, DiscreteVoxelShape first, DiscreteVoxelShape second,
                                   IndexMerger x, IndexMerger y, IndexMerger z, BooleanOp operation) throws Throwable {
        return (DiscreteVoxelShape) (reference ? NATIVE : LIVE).invokeExact(first, second, x, y, z, operation);
    }
}
