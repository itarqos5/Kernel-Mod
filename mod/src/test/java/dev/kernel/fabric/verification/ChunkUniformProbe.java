package dev.kernel.fabric.verification;

//? if >=1.21.11 {
import dev.kernel.fabric.config.KernelRendererSettings;
import dev.kernel.fabric.config.RendererFeature;
import dev.kernel.fabric.render.ChunkMatrixSnapshot;
import net.minecraft.client.renderer.DynamicUniforms;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
//? }

/** Observes the actual records passed to the native uniform uploader in an isolated test world. */
public final class ChunkUniformProbe {
    private ChunkUniformProbe() {}
    //? if >=1.21.11 {
    private static Matrix4fc retained;
    private static Matrix4f expected;
    private static int batches, largest;
    public static void inspect(DynamicUniforms.ChunkSectionInfo[] values) {
        if (!Boolean.getBoolean("kernel.guiProbe.shaders") || values.length < 2) return;
        boolean reuse = KernelRendererSettings.enabled(RendererFeature.CHUNK_UNIFORMS);
        if (retained != null && !ChunkMatrixSnapshot.matches(expected, retained)) throw new AssertionError("Previously uploaded camera snapshot was mutated");
        Matrix4fc first = values[0].modelView();
        if (retained == first) throw new AssertionError("Snapshot escaped its native preparation invocation");
        for (int index = 1; index < values.length; index++) {
            if ((first == values[index].modelView()) != reuse) throw new AssertionError("Unexpected chunk uniform matrix ownership with reuse=" + reuse);
        }
        retained = first; expected = new Matrix4f(first);
        largest = Math.max(largest, values.length); batches++;
    }
    //? }
    public static void verifyComplete() {
        //? if >=1.21.11 {
        if (batches < 2 || largest < 2) throw new AssertionError("Chunk uniform probe never observed populated native batches");
        System.out.println("Kernel chunk uniform snapshots: enabled=" + KernelRendererSettings.enabled(RendererFeature.CHUNK_UNIFORMS)
            + ", largestBatch=" + largest + ", batches=" + batches + "; sharing and retained ownership passed.");
        //? }
    }
}
