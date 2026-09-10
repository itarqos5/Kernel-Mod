package dev.kernel.client.startup;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

/** Narrow in-memory adapters for Fabric 0.19.3's two bytecode-read paths. Unknown shapes are left untouched. */
public final class StartupCacheTransformer implements ClassFileTransformer {
    static final String KNOT = "net/fabricmc/loader/impl/launch/knot/KnotClassDelegate";
    static final String MIXIN_SERVICE = "net/fabricmc/loader/impl/launch/knot/MixinServiceKnot";
    private static final String BRIDGE = "dev/kernel/client/startup/StartupCaches";

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> redefined,
                            ProtectionDomain domain, byte[] bytes) {
        if (redefined != null || (!KNOT.equals(className) && !MIXIN_SERVICE.equals(className))) return null;
        try {
            // Only the released Fabric 0.19.3 bytecode is audited. A different loader or an earlier agent's
            // edits disable this optimization instead of overwriting behavior we have not inspected.
            String expected = KNOT.equals(className)
                ? "da0f061a7c052a2183a9526f638dde014b1f119170045df746a54c09fbdcd02f"
                : "ff5696cb50641654c7c3b0e3b396bbd61d8ad7b82a63cbf8038a7207bef3c295";
            if (!expected.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)))) {
                System.err.println("[Kernel] Startup cache bypassed for unaudited Fabric bytecode: " + className);
                return null;
            }
            // Helper classes must resolve to this agent, not to a second copy in a child class loader.
            if (Class.forName(StartupCaches.class.getName(), false, loader) != StartupCaches.class) return null;
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (KNOT.equals(className) && method.name.equals("getRawClassByteArray")
                    && method.desc.equals("(Ljava/lang/String;Z)[B")) {
                    changed = patchRawRead(method);
                } else if (MIXIN_SERVICE.equals(className) && method.name.equals("getClassNode")
                    && method.desc.equals("(Ljava/lang/String;ZI)Lorg/objectweb/asm/tree/ClassNode;")) {
                    changed = patchTargetRead(method);
                }
            }
            if (!changed) {
                System.err.println("[Kernel] Unrecognized startup method shape; left unchanged: " + className);
                return null;
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            System.err.println("[Kernel] Startup cache hook active: " + className);
            return writer.toByteArray();
        } catch (RuntimeException | LinkageError | ClassNotFoundException | NoSuchAlgorithmException exception) {
            System.err.println("[Kernel] Skipped optional startup hook for " + className + ": " + exception);
            return null;
        }
    }

    private static boolean patchTargetRead(MethodNode method) {
        int providerCalls = 0;
        int readerCalls = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                if (call.owner.equals(MIXIN_SERVICE) && call.name.equals("getClassBytes")
                    && call.desc.equals("(Ljava/lang/String;Z)[B")) providerCalls++;
                else if (call.owner.equals("org/objectweb/asm/ClassReader") && call.name.equals("accept")
                    && call.desc.equals("(Lorg/objectweb/asm/ClassVisitor;I)V")) readerCalls++;
                else if (!call.name.equals("<init>")) return false;
            }
            if (instruction instanceof JumpInsnNode || instruction instanceof InvokeDynamicInsnNode) return false;
        }
        if (providerCalls != 1 || readerCalls != 1 || !method.tryCatchBlocks.isEmpty()) return false;
        method.instructions.clear();
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MIXIN_SERVICE,
            "getClassBytes", "(Ljava/lang/String;Z)[B", false));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE,
            "readTarget", "([BI)Lorg/objectweb/asm/tree/ClassNode;", false));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        clearDebugLocals(method);
        return true;
    }

    private static boolean patchRawRead(MethodNode method) {
        MethodInsnNode open = null;
        boolean usesVanillaBuffer = false;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                if (call.owner.equals("java/net/URL") && call.name.equals("openStream")
                    && call.desc.equals("()Ljava/io/InputStream;")) {
                    if (open != null) return false;
                    open = call;
                }
                if (call.owner.equals("java/io/ByteArrayOutputStream") && call.name.equals("toByteArray")) {
                    usesVanillaBuffer = true;
                }
            }
        }
        if (open == null || !usesVanillaBuffer) return false;
        Set<LabelNode> retainedLabels = new HashSet<>();
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != open; instruction = instruction.getNext()) {
            if (instruction instanceof LabelNode label) retainedLabels.add(label);
        }
        // The retained prefix must contain the full resource lookup and permission checks. Reject control
        // flow into the stream/cleanup tail, and any non-resource exception handler around that prefix.
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != open; instruction = instruction.getNext()) {
            if (instruction instanceof JumpInsnNode jump && !retainedLabels.contains(jump.label)) return false;
            if (instruction instanceof TableSwitchInsnNode || instruction instanceof LookupSwitchInsnNode) return false;
        }
        for (TryCatchBlockNode handler : method.tryCatchBlocks) {
            if (retainedLabels.contains(handler.start)) return false;
        }
        while (method.instructions.getLast() != open) method.instructions.remove(method.instructions.getLast());
        method.instructions.set(open, new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE,
            "readClass", "(Ljava/net/URL;)[B", false));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.tryCatchBlocks.clear();
        clearDebugLocals(method);
        return true;
    }

    private static void clearDebugLocals(MethodNode method) {
        if (method.localVariables != null) method.localVariables.clear();
        method.visibleLocalVariableAnnotations = null;
        method.invisibleLocalVariableAnnotations = null;
    }
}
