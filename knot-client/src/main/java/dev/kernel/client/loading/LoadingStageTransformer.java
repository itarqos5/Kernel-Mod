package dev.kernel.client.loading;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.lang.instrument.ClassFileTransformer;
import java.security.MessageDigest;
import java.security.ProtectionDomain;
import java.util.HexFormat;

/** Label-only hooks for audited loader/Mixin bytecode. Never changes preparation order or results. */
public final class LoadingStageTransformer implements ClassFileTransformer {
    static final String MIXIN = "org/spongepowered/asm/mixin/transformer/MixinInfo";
    static final String ENTRYPOINT = "net/fabricmc/loader/impl/entrypoint/EntrypointContainerImpl";
    private static final String BRIDGE = "dev/kernel/client/loading/StartupProgress";

    @Override public byte[] transform(ClassLoader loader, String name, Class<?> redefined, ProtectionDomain domain, byte[] bytes) {
        if (redefined != null || (!MIXIN.equals(name) && !ENTRYPOINT.equals(name))) return null;
        try {
            String expected = MIXIN.equals(name) ? "e85279ca92533bb0fa76f3c4056c52bb7fffb18aac8131d21098a650f2a62991"
                : "574d9ec4fc5ca21119657d8771cbe675d197670ba17aa477056d9d34c0083be9";
            if (!expected.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)))) return null;
            if (Class.forName(StartupProgress.class.getName(), false, loader) != StartupProgress.class) return null;
            ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
            MethodNode target = node.methods.stream().filter(method -> MIXIN.equals(name)
                ? method.name.equals("validate") && method.desc.equals("()V")
                : method.name.equals("getEntrypoint") && method.desc.equals("()Ljava/lang/Object;")).findFirst().orElse(null);
            if (target == null) return null;
            InsnList label = new InsnList(); label.add(new VarInsnNode(Opcodes.ALOAD, 0));
            if (MIXIN.equals(name)) {
                label.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MIXIN, "getClassName", "()Ljava/lang/String;", false));
            } else {
                label.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTRYPOINT, "getProvider", "()Lnet/fabricmc/loader/api/ModContainer;", false));
                label.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "net/fabricmc/loader/api/ModContainer", "getMetadata", "()Lnet/fabricmc/loader/api/metadata/ModMetadata;", true));
                label.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "net/fabricmc/loader/api/metadata/ModMetadata", "getId", "()Ljava/lang/String;", true));
            }
            label.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, MIXIN.equals(name) ? "mixin" : "mod", "(Ljava/lang/String;)V", false));
            target.instructions.insert(label);
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer);
            return writer.toByteArray();
        } catch (Exception | LinkageError exception) {
            System.err.println("[Kernel] Optional loading detail hook skipped: " + name + ": " + exception); return null;
        }
    }
}
