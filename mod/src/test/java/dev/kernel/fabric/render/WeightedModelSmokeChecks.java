package dev.kernel.fabric.render;

//? if <=1.21.4 {
/*import com.sun.management.ThreadMXBean;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.resources.model.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.*;
import net.minecraft.world.level.block.state.BlockState;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
*///? }

/** Differential native dispatch through the real Knot-transformed renderer. */
public final class WeightedModelSmokeChecks {
    public static void run(boolean enabled) {
        //? if <=1.21.4 {
        /*try { verify(enabled); }
        catch (Throwable failure) { throw new AssertionError("Weighted model native checks failed", failure); }
        *///? }
    }
    //? if <=1.21.4 {
    /*private static final MethodHandle REFERENCE = reference();
    private static final ThreadMXBean ALLOCATION = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static volatile Object sink;
    private static Model overrideModel;
    private static int overrideCalls;

    private static MethodHandle reference() {
        try (var input = WeightedBakedModel.class.getResourceAsStream("WeightedBakedModel.class")) {
            var node = new ClassNode(); new ClassReader(input).accept(node, 0);
            if (node.fields.size() != 1 || node.methods.size() != 3)
                throw new AssertionError("Unexpected native weighted model structure");
            var writer = new ClassWriter(0);
            node.accept(new ClassRemapper(writer, new SimpleRemapper(node.name, "dev/kernel/fabric/render/KernelNativeWeightedModel")));
            Class<?> type = MethodHandles.lookup().defineClass(writer.toByteArray());
            return MethodHandles.lookup().findConstructor(type, MethodType.methodType(void.class, SimpleWeightedRandomList.class))
                .asType(MethodType.methodType(BakedModel.class, SimpleWeightedRandomList.class));
        } catch (Exception failure) { throw new AssertionError("Cannot load original weighted model", failure); }
    }
    private static BakedModel reference(SimpleWeightedRandomList<BakedModel> list) throws Throwable {
        return (BakedModel) REFERENCE.invokeExact(list);
    }
    private static final class Model extends DelegateBakedModel {
        final List<BakedQuad> quads; int calls; boolean consume;
        BlockState state; Direction face; RandomSource random;
        RuntimeException failure; Runnable nested;
        Model(List<BakedQuad> quads) { super(null); this.quads = quads; }
        @Override public List<BakedQuad> getQuads(BlockState state, Direction face, RandomSource random) {
            calls++; this.state = state; this.face = face; this.random = random;
            if (consume) random.nextLong();
            if (nested != null) nested.run();
            if (failure != null) throw failure;
            return quads;
        }
        @Override public boolean useAmbientOcclusion() { return false; }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean usesBlockLight() { return false; }
    }
    private static final class Ticket extends net.minecraft.world.level.levelgen.LegacyRandomSource {
        int value, calls, bound;
        RuntimeException failure;
        Ticket(int value) { super(0); this.value = value; }
        @Override public int nextInt(int bound) {
            this.bound = bound; calls++;
            if (failure != null) throw failure;
            return value;
        }
    }
    private static final class DerivedModel extends WeightedBakedModel {
        DerivedModel(SimpleWeightedRandomList<BakedModel> list) { super(list); }
    }
    private static void verify(boolean enabled) throws Throwable {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        var random = new Random(217412);
        for (int round = 0; round < 1024; round++) {
            var builder = SimpleWeightedRandomList.<BakedModel>builder();
            int total = 0, size = 1 + random.nextInt(32);
            for (int index = 0; index < size; index++) {
                int weight = random.nextInt(9); total += weight;
                Model model = new Model(index % 7 == 0 ? null : new ArrayList<>()); model.consume = true;
                builder.add(index % 11 == 0 ? null : model, weight);
            }
            var list = builder.build();
            check((list instanceof WeightedListAccess) == enabled, "Weighted list access ownership");
            BakedModel live = new WeightedBakedModel(list), nativeModel = reference(list);
            compare(live, nativeModel, total, round);
        }
        var model = new Model(new ArrayList<>());
        var list = SimpleWeightedRandomList.<BakedModel>single(model);
        BakedModel live = new WeightedBakedModel(list), nativeModel = reference(list);
        check(live.useAmbientOcclusion() == nativeModel.useAmbientOcclusion() && live.isGui3d() == nativeModel.isGui3d()
            && live.usesBlockLight() == nativeModel.usesBlockLight(),
            "Inherited model properties changed");
        for (Direction face : Direction.values()) {
            var source = new Ticket(0); int calls = model.calls;
            BlockState state = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
            check(live.getQuads(state, face, source) == model.quads && model.calls == calls + 1
                && model.state == state && model.face == face && model.random == source, "Delegate call/argument/list identity");
        }
        var failure = new IllegalStateException("model failure"); model.failure = failure;
        sameFailure(failure, () -> live.getQuads(null, null, new Ticket(0)));
        sameFailure(failure, () -> nativeModel.getQuads(null, null, new Ticket(0)));
        model.failure = null;
        var failedRandom = new Ticket(0); failedRandom.failure = failure;
        sameFailure(failure, () -> live.getQuads(null, null, failedRandom));
        sameFailure(failure, () -> nativeModel.getQuads(null, null, failedRandom));
        expect(NullPointerException.class, () -> live.getQuads(null, null, null));
        expect(NullPointerException.class, () -> nativeModel.getQuads(null, null, null));
        var zero = SimpleWeightedRandomList.<BakedModel>builder().add(model, 0).build();
        check(new WeightedBakedModel(zero).getQuads(null, null, null) == Collections.<BakedQuad>emptyList(), "Zero weight used random");
        var large = SimpleWeightedRandomList.<BakedModel>builder().add(model, Integer.MAX_VALUE).build();
        compare(new WeightedBakedModel(large), reference(large), Integer.MAX_VALUE, 273);
        compare(new DerivedModel(list), reference(list), 1, 17);
        customList();
        var innerModel = new Model(new ArrayList<>());
        var inner = new WeightedBakedModel(SimpleWeightedRandomList.<BakedModel>single(innerModel));
        model.nested = () -> check(inner.getQuads(null, Direction.DOWN, new Ticket(0)) == innerModel.quads, "Nested delegate result");
        check(live.getQuads(null, Direction.UP, new Ticket(0)) == model.quads, "Outer delegate result");
        model.nested = null;
        var workers = Executors.newFixedThreadPool(4);
        try {
            var results = new ArrayList<Future<?>>();
            for (int thread = 0; thread < 4; thread++) {
                int seed = thread;
                results.add(workers.submit(() -> {
                    try {
                        var local = SimpleWeightedRandomList.<BakedModel>builder()
                            .add(new Model(new ArrayList<>()), 1).add(new Model(new ArrayList<>()), 7).build();
                        compare(new WeightedBakedModel(local), reference(local), 8, seed);
                    } catch (Throwable caught) { throw new CompletionException(caught); }
                }));
            }
            for (var result : results) result.get();
        } finally { workers.shutdownNow(); }
        allocation(enabled, live, nativeModel, new DerivedModel(list));
        System.out.println("Kernel weighted models: enabled=" + enabled
            + "; native selection, random consumption, null/exception/list ownership, subclass fallback and concurrency passed");
        if (Boolean.getBoolean("kernel.weightedModelBenchmark")) benchmark(enabled);
    }
    private static void compare(BakedModel live, BakedModel nativeModel, int total, int seed) {
        for (int ticket : new int[]{Integer.MIN_VALUE, -2, -1, 0, 1, total/2, total-1, total, Integer.MAX_VALUE}) {
            var one = new Ticket(ticket); var two = new Ticket(ticket);
            check(nativeModel.getQuads(null, Direction.UP, one) == live.getQuads(null, Direction.UP, two), "Selected result identity");
            check(one.calls == two.calls && one.bound == two.bound && one.nextLong() == two.nextLong(), "Ticket calls/bounds/state");
        }
        var one = RandomSource.create(seed); var two = RandomSource.create(seed);
        for (int index = 0; index < 100; index++) {
            check(nativeModel.getQuads(null, null, one) == live.getQuads(null, null, two), "Seeded model choice");
            check(one.nextLong() == two.nextLong(), "Seeded random consumption");
        }
    }
    public static Optional<BakedModel> overrideSelection(RandomSource random) {
        overrideCalls++; random.nextInt(197); return Optional.ofNullable(overrideModel);
    }
    private static void customList() throws Throwable {
        String name = "net/minecraft/util/random/KernelTestWeightedList";
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/util/random/SimpleWeightedRandomList", null);
        var init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/util/List;)V", null, null);
        init.visitCode(); init.visitVarInsn(Opcodes.ALOAD, 0); init.visitVarInsn(Opcodes.ALOAD, 1);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/util/random/SimpleWeightedRandomList", "<init>", "(Ljava/util/List;)V", false);
        init.visitInsn(Opcodes.RETURN); init.visitMaxs(2, 2); init.visitEnd();
        String descriptor = "(Lnet/minecraft/util/RandomSource;)Ljava/util/Optional;";
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "getRandomValue", descriptor, null, null);
        method.visitCode(); method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/kernel/fabric/render/WeightedModelSmokeChecks", "overrideSelection", descriptor, false);
        method.visitInsn(Opcodes.ARETURN); method.visitMaxs(1, 2); method.visitEnd(); writer.visitEnd();
        Class<?> type = MethodHandles.privateLookupIn(SimpleWeightedRandomList.class, MethodHandles.lookup()).defineClass(writer.toByteArray());
        @SuppressWarnings("unchecked") var list = (SimpleWeightedRandomList<BakedModel>) type.getConstructor(List.class)
            .newInstance(List.of(WeightedEntry.wrap(new Model(new ArrayList<>()), 1)));
        overrideModel = new Model(new ArrayList<>());
        try {
            var live = new WeightedBakedModel(list); var nativeModel = reference(list);
            int before = overrideCalls;
            compare(live, nativeModel, 1, 7654);
            check(overrideCalls - before == 218, "Custom list override was bypassed");
            overrideModel = null;
            check(live.getQuads(null, null, new Ticket(0)) == Collections.<BakedQuad>emptyList(), "Null custom choice");
        } finally { overrideModel = null; }
    }
    private static void allocation(boolean enabled, BakedModel live, BakedModel reference, BakedModel derived) {
        BakedModel[] variants = {reference, live, derived}; long[] bytes = new long[3];
        for (int index = 0; index < variants.length; index++) {
            var random = RandomSource.create(21);
            for (int warmup = 0; warmup < 40000; warmup++) sink = variants[index].getQuads(null, null, random);
            long thread = Thread.currentThread().threadId(), before = ALLOCATION.getThreadAllocatedBytes(thread);
            for (int call = 0; call < 8192; call++) sink = variants[index].getQuads(null, null, random);
            bytes[index] = ALLOCATION.getThreadAllocatedBytes(thread) - before;
        }
        check(bytes[0] > 0 && bytes[2] > 0, "Native/subclass allocation controls missing");
        if (enabled) check(bytes[1] < bytes[0], "Real dispatch did not reduce allocation");
        else check(bytes[1] > 0, "Disabled dispatch unexpectedly active");
        System.out.println("Kernel weighted model allocation: native/live/subclass=" + bytes[0]/8192.0 + "/" + bytes[1]/8192.0 + "/" + bytes[2]/8192.0 + " B/query");
    }
    private static void benchmark(boolean enabled) throws Throwable {
        for (int size : new int[]{1, 2, 4, 16, 64}) {
            var builder = SimpleWeightedRandomList.<BakedModel>builder();
            for (int index = 0; index < size; index++) builder.add(new Model(new ArrayList<>()), index % 5 + 1);
            var list = builder.build();
            BakedModel[] models = {reference(list), new WeightedBakedModel(list)};
            RandomSource[] sources = {RandomSource.create(27181), RandomSource.create(27181)};
            long[][] times = new long[2][15], bytes = new long[2][15];
            for (int round = -12; round < 15; round++) for (int offset = 0; offset < 2; offset++) {
                int variant = Math.floorMod(round + offset, 2);
                long thread = Thread.currentThread().threadId(), allocated = ALLOCATION.getThreadAllocatedBytes(thread), start = System.nanoTime();
                for (int call = 0; call < 65536; call++) sink = models[variant].getQuads(null, null, sources[variant]);
                long elapsed = System.nanoTime() - start, used = ALLOCATION.getThreadAllocatedBytes(thread) - allocated;
                if (round >= 0) { times[variant][round] = elapsed; bytes[variant][round] = used; }
            }
            for (int variant = 0; variant < 2; variant++) {
                Arrays.sort(times[variant]); Arrays.sort(bytes[variant]);
                System.out.printf(Locale.ROOT, "Kernel weighted model benchmark: size=%d %s enabled=%s ns/query=%.4f B/query=%.4f%n",
                    size, variant == 0 ? "reference" : "live", enabled, times[variant][7]/65536.0, bytes[variant][7]/65536.0);
            }
        }
    }
    private static void sameFailure(RuntimeException expected, Runnable action) {
        try { action.run(); } catch (RuntimeException actual) { check(actual == expected, "Exception identity"); return; }
        throw new AssertionError("Missing failure");
    }
    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try { action.run(); } catch (Throwable failure) { if (type.isInstance(failure)) return; throw failure; }
        throw new AssertionError("Missing " + type.getSimpleName());
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    *///? }
}
