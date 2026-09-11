package dev.kernel.fabric.world;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.phys.shapes.CubeVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;

/** Original installed getCoords bytecode in a separate test subclass; no game bytecode is bundled. */
final class ShapeCoordinatesTestSupport {
    private static final MethodHandle LIVE = constructor(CubeVoxelShape.class);
    private static final MethodHandle NATIVE = reference();
    private static MethodHandle constructor(Class<?> type) {
        try {
            var constructor = type.getDeclaredConstructor(DiscreteVoxelShape.class);
            constructor.setAccessible(true);
            return MethodHandles.lookup().unreflectConstructor(constructor)
                .asType(MethodType.methodType(VoxelShape.class, DiscreteVoxelShape.class));
        } catch (Exception failure) { throw new AssertionError("Cannot create cube shape reference", failure); }
    }
    private static MethodHandle reference() {
        try (var input = CubeVoxelShape.class.getResourceAsStream("CubeVoxelShape.class")) {
            var source = new ClassNode(); new ClassReader(input).accept(source, 0);
            if (!source.fields.isEmpty() || source.methods.size() != 3
                || source.methods.stream().anyMatch(method -> !java.util.Set.of("<init>", "getCoords", "findIndex").contains(method.name)))
                throw new AssertionError("Unexpected native cube shape structure");
            var writer = new ClassWriter(0);
            var remapper = new ClassRemapper(writer, new SimpleRemapper(source.name, "dev/kernel/fabric/world/KernelNativeCubeCoordinates"));
            remapper.visit(source.version, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                source.name, null, source.superName, null);
            for (var method : source.methods) method.accept(remapper);
            remapper.visitEnd();
            return constructor(MethodHandles.lookup().defineClass(writer.toByteArray()));
        } catch (Exception failure) { throw new AssertionError("Cannot load original cube coordinates", failure); }
    }
    static VoxelShape create(DiscreteVoxelShape grid, boolean reference) throws Throwable {
        return (VoxelShape) (reference ? NATIVE : LIVE).invokeExact(grid);
    }
}
