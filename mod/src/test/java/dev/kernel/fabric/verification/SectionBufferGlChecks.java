package dev.kernel.fabric.verification;

//? if <=1.21.4 {
/*import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.*;
import dev.kernel.fabric.config.KernelRendererSettings;
import dev.kernel.fabric.config.RendererFeature;
import dev.kernel.fabric.render.DeferredSectionVertexBuffer;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryUtil;
*///? }

/** Real driver and native section ownership checks; excluded from release artifacts. */
public final class SectionBufferGlChecks {
    private SectionBufferGlChecks() {}
    public static void run() throws Exception {
        //? if <=1.21.4 {
        /*boolean enabled = KernelRendererSettings.enabled(RendererFeature.SECTION_BUFFERS);
        Field storage = VertexBuffer.class.getDeclaredField("vertexBuffer"); storage.setAccessible(true);
        Field array = VertexBuffer.class.getDeclaredField("arrayObjectId"); array.setAccessible(true);
        var constructor = SectionRenderDispatcher.RenderSection.class.getConstructor(SectionRenderDispatcher.class, int.class, long.class);
        var section = constructor.newInstance(null, 0, 0L);
        try {
            for (RenderType layer : RenderType.chunkBufferLayers()) {
                VertexBuffer buffer = section.getBuffer(layer);
                if ((buffer instanceof DeferredSectionVertexBuffer) != enabled) throw new AssertionError("Section factory control differs");
                initial(buffer, storage, array, enabled);
                VertexBuffer fromWorker = CompletableFuture.supplyAsync(() -> section.getBuffer(layer)).join();
                if (fromWorker != buffer) throw new AssertionError("Worker buffer identity changed");
                if (enabled) CompletableFuture.runAsync(() -> {
                    try { buffer.bind(); throw new AssertionError("Worker created a GPU object"); }
                    catch (IllegalStateException expected) {}
                }).join();
                initial(buffer, storage, array, enabled);
            }
            drawAndResort(section.getBuffer(RenderType.solid()), storage, array);
            section.setSectionNode(1234);
            if (section.getBuffer(RenderType.solid()).getFormat() != DefaultVertexFormat.POSITION_COLOR)
                throw new AssertionError("Reposition discarded native reusable storage");
        } finally { section.releaseBuffers(); }
        for (RenderType layer : RenderType.chunkBufferLayers()) {
            VertexBuffer buffer = section.getBuffer(layer);
            if (!buffer.isInvalid() || array.getInt(buffer) != -1) throw new AssertionError("Released section remained live");
            if (enabled && layer != RenderType.solid() && storage.get(buffer) != null) throw new AssertionError("Release allocated an unused layer");
            closedUpload(buffer, storage);
        }
        section.releaseBuffers();
        int boundBefore = GL33C.glGetInteger(GL33C.GL_VERTEX_ARRAY_BINDING);
        try (VertexBuffer boundOnly = new DeferredSectionVertexBuffer(BufferUsage.STATIC_WRITE)) {
            boundOnly.bind();
            if (array.getInt(boundOnly) == 0 || (enabled && storage.get(boundOnly) != null))
                throw new AssertionError("First binding did not defer vertex storage until upload");
            VertexBuffer.unbind();
        } finally { GlStateManager._glBindVertexArray(boundBefore); }
        // An index upload writes the currently bound VAO, including when no vertices exist yet.
        // Keep this native edge case away from the title screen's retained vertex arrays.
        int previousArray = GL33C.glGetInteger(GL33C.GL_VERTEX_ARRAY_BINDING), indexArray = GlStateManager._glGenVertexArrays();
        GlStateManager._glBindVertexArray(indexArray);
        try (VertexBuffer indexOnly = new DeferredSectionVertexBuffer(BufferUsage.STATIC_WRITE);
             var indices = new ByteBufferBuilder(64)) {
            long address = indices.reserve(12);
            for (int i = 0; i < 6; i++) MemoryUtil.memPutShort(address + i * 2L, (short) 0);
            indexOnly.uploadIndexBuffer(indices.build());
            if (enabled && storage.get(indexOnly) != null) throw new AssertionError("Index-only upload allocated vertex storage");
        } finally { GlStateManager._glBindVertexArray(previousArray); GL33C.glDeleteVertexArrays(indexArray); }
        try (VertexBuffer ordinary = new VertexBuffer(BufferUsage.STATIC_WRITE);
             VertexBuffer custom = new VertexBuffer(BufferUsage.STATIC_WRITE) {}) {
            initial(ordinary, storage, array, false); initial(custom, storage, array, false);
        }
        if (Boolean.getBoolean("kernel.sectionBufferBenchmark")) benchmark(enabled);
        if (GL33C.glGetError() != GL33C.GL_NO_ERROR) throw new AssertionError("Section buffer lifecycle produced a GL error");
        System.out.println("Kernel section buffers: enabled=" + enabled + "; native factory, worker identity, uploads, pixels, resort, reposition and early/late close passed");
        *///? }
    }
    public static void verifyWorld(net.minecraft.client.Minecraft minecraft) throws Exception {
        //? if <=1.21.4 {
        /*Field areaField = net.minecraft.client.renderer.LevelRenderer.class.getDeclaredField("viewArea"); areaField.setAccessible(true);
        Object area = areaField.get(minecraft.levelRenderer);
        Field sectionsField = area.getClass().getDeclaredField("sections"); sectionsField.setAccessible(true);
        var sections = (SectionRenderDispatcher.RenderSection[]) sectionsField.get(area);
        Field storage = VertexBuffer.class.getDeclaredField("vertexBuffer"); storage.setAccessible(true);
        Field array = VertexBuffer.class.getDeclaredField("arrayObjectId"); array.setAccessible(true);
        boolean enabled = KernelRendererSettings.enabled(RendererFeature.SECTION_BUFFERS);
        int allocated = 0, unused = 0;
        for (var section : sections) for (RenderType layer : RenderType.chunkBufferLayers()) {
            VertexBuffer buffer = section.getBuffer(layer);
            if (buffer.isInvalid() || (buffer instanceof DeferredSectionVertexBuffer) != enabled)
                throw new AssertionError("Live world layer factory/lifetime differs");
            if (storage.get(buffer) == null) {
                unused++;
                if (!enabled || array.getInt(buffer) != 0 || buffer.getFormat() != null) throw new AssertionError("Deferred world layer state differs");
            } else allocated++;
            if (!section.getCompiled().isEmpty(layer) && storage.get(buffer) == null) throw new AssertionError("Compiled geometry lacks uploaded storage");
        }
        if (allocated == 0 || (enabled && unused == 0)) throw new AssertionError("Flat world did not exercise both active and unused buffers");
        System.out.println("Kernel section buffer world: enabled=" + enabled + ", " + sections.length + " sections, " + allocated + " allocated layers, " + unused + " layers without GPU objects");
        *///? }
    }
    //? if <=1.21.4 {
    /*private static void initial(VertexBuffer buffer, Field storage, Field array, boolean deferred) throws Exception {
        if (buffer.isInvalid() || buffer.getFormat() != null) throw new AssertionError("New buffer native state differs");
        if ((storage.get(buffer) == null) != deferred || (array.getInt(buffer) == 0) != deferred)
            throw new AssertionError("Unexpected eager/deferred GPU allocation");
    }
    private static MeshData quad(ByteBufferBuilder bytes, int red, int green, int blue) {
        var builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        builder.addVertex(-1, -1, 0).setColor(red, green, blue, 255);
        builder.addVertex(1, -1, 0).setColor(red, green, blue, 255);
        builder.addVertex(1, 1, 0).setColor(red, green, blue, 255);
        builder.addVertex(-1, 1, 0).setColor(red, green, blue, 255);
        return builder.buildOrThrow();
    }
    private static void closedUpload(VertexBuffer buffer, Field storage) throws Exception {
        Object before = storage.get(buffer);
        try (var bytes = new ByteBufferBuilder(256)) {
            MeshData mesh = quad(bytes, 0, 0, 0); buffer.upload(mesh);
            try { mesh.vertexBuffer(); throw new AssertionError("Closed upload retained mesh ownership"); }
            catch (IllegalStateException expected) {}
        }
        buffer.upload(null);
        if (storage.get(buffer) != before || !buffer.isInvalid()) throw new AssertionError("Closed upload recreated storage");
    }
    private static void drawAndResort(VertexBuffer buffer, Field storage, Field array) throws Exception {
        int oldVao = GL33C.glGetInteger(GL33C.GL_VERTEX_ARRAY_BINDING), oldBuffer = GL33C.glGetInteger(GL33C.GL_ARRAY_BUFFER_BINDING);
        int oldDraw = GL33C.glGetInteger(GL33C.GL_DRAW_FRAMEBUFFER_BINDING), oldRead = GL33C.glGetInteger(GL33C.GL_READ_FRAMEBUFFER_BINDING);
        int oldTexture = GL33C.glGetInteger(GL33C.GL_TEXTURE_BINDING_2D), oldProgram = GL33C.glGetInteger(GL33C.GL_CURRENT_PROGRAM);
        int oldUnpack = GL33C.glGetInteger(GL33C.GL_PIXEL_UNPACK_BUFFER_BINDING), oldPack = GL33C.glGetInteger(GL33C.GL_PIXEL_PACK_BUFFER_BINDING);
        int[] viewport = new int[4]; GL33C.glGetIntegerv(GL33C.GL_VIEWPORT, viewport);
        int[] caps = {GL33C.GL_DEPTH_TEST, GL33C.GL_BLEND, GL33C.GL_CULL_FACE, GL33C.GL_SCISSOR_TEST};
        boolean[] states = new boolean[caps.length];
        for (int i = 0; i < caps.length; i++) { states[i] = GL33C.glIsEnabled(caps[i]); GL33C.glDisable(caps[i]); }
        int[] stores = {GL33C.GL_PACK_ALIGNMENT, GL33C.GL_PACK_ROW_LENGTH, GL33C.GL_PACK_SKIP_ROWS, GL33C.GL_PACK_SKIP_PIXELS};
        int[] values = new int[stores.length];
        for (int i = 0; i < stores.length; i++) { values[i] = GL33C.glGetInteger(stores[i]); GL33C.glPixelStorei(stores[i], i == 0 ? 1 : 0); }
        int texture = GL33C.glGenTextures(), fbo = GL33C.glGenFramebuffers(), program = program();
        int allocatedArray = 0, allocatedBuffer = 0;
        try {
            GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER, 0); GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, 0);
            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, texture);
            GL33C.glTexImage2D(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA8, 8, 8, 0, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, 0L);
            GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, fbo);
            GL33C.glFramebufferTexture2D(GL33C.GL_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0, GL33C.GL_TEXTURE_2D, texture, 0);
            if (GL33C.glCheckFramebufferStatus(GL33C.GL_FRAMEBUFFER) != GL33C.GL_FRAMEBUFFER_COMPLETE) throw new AssertionError("Probe framebuffer incomplete");
            GL33C.glViewport(0, 0, 8, 8); GL33C.glUseProgram(program);
            for (int pass = 0; pass < 3; pass++) try (var bytes = new ByteBufferBuilder(256); var indices = new ByteBufferBuilder(64)) {
                MeshData mesh = quad(bytes, 50 + pass * 40, 80, 130);
                byte[] expected = new byte[mesh.vertexBuffer().remaining()]; mesh.vertexBuffer().get(0, expected);
                buffer.bind(); buffer.upload(mesh);
                int handle = ((GpuBuffer) storage.get(buffer)).handle, vao = array.getInt(buffer);
                if (pass == 0) { allocatedArray = vao; allocatedBuffer = handle; }
                if (handle != allocatedBuffer || vao != allocatedArray || vao == 0) throw new AssertionError("Repeated upload replaced stable GPU objects");
                var output = MemoryUtil.memAlloc(expected.length);
                try {
                    GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, handle); GL33C.glGetBufferSubData(GL33C.GL_ARRAY_BUFFER, 0L, output);
                    for (int i = 0; i < expected.length; i++) if (expected[i] != output.get(i)) throw new AssertionError("Native vertex upload bytes differ");
                } finally { MemoryUtil.memFree(output); }
                buffer.draw(); pixel(50 + pass * 40, 80, 130);
                long indexAddress = indices.reserve(12);
                short[] order = {2, 3, 0, 0, 1, 2};
                for (int i = 0; i < order.length; i++) MemoryUtil.memPutShort(indexAddress + i * 2L, order[i]);
                buffer.uploadIndexBuffer(indices.build()); buffer.draw(); pixel(50 + pass * 40, 80, 130);
                VertexBuffer.unbind();
            }
        } finally {
            GL33C.glUseProgram(oldProgram); GL33C.glDeleteProgram(program);
            GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, oldDraw); GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, oldRead);
            GL33C.glDeleteFramebuffers(fbo); GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, oldTexture); GL33C.glDeleteTextures(texture);
            GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER, oldUnpack); GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, oldPack);
            for (int i = 0; i < stores.length; i++) GL33C.glPixelStorei(stores[i], values[i]);
            GlStateManager._glBindVertexArray(oldVao); GlStateManager._glBindBuffer(GL33C.GL_ARRAY_BUFFER, oldBuffer);
            GL33C.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            for (int i = 0; i < caps.length; i++) { if (states[i]) GL33C.glEnable(caps[i]); else GL33C.glDisable(caps[i]); }
        }
    }
    private static void pixel(int r, int g, int b) {
        var pixel = MemoryUtil.memAlloc(4);
        try {
            GL33C.glReadPixels(4, 4, 1, 1, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, pixel);
            if ((pixel.get(0) & 255) != r || (pixel.get(1) & 255) != g || (pixel.get(2) & 255) != b || (pixel.get(3) & 255) != 255)
                throw new AssertionError("Native buffer rendered incorrect pixels");
        } finally { MemoryUtil.memFree(pixel); }
    }
    private static int program() {
        int vertex = shader(GL33C.GL_VERTEX_SHADER, "#version 330 core\nlayout(location=0) in vec3 position; layout(location=1) in vec4 color; out vec4 shade; void main(){gl_Position=vec4(position,1);shade=color;}");
        int fragment = shader(GL33C.GL_FRAGMENT_SHADER, "#version 330 core\nin vec4 shade; out vec4 result; void main(){result=shade;}");
        int program = GL33C.glCreateProgram(); GL33C.glAttachShader(program, vertex); GL33C.glAttachShader(program, fragment); GL33C.glLinkProgram(program);
        GL33C.glDeleteShader(vertex); GL33C.glDeleteShader(fragment);
        if (GL33C.glGetProgrami(program, GL33C.GL_LINK_STATUS) == 0) throw new AssertionError(GL33C.glGetProgramInfoLog(program));
        return program;
    }
    private static int shader(int type, String source) {
        int shader = GL33C.glCreateShader(type); GL33C.glShaderSource(shader, source); GL33C.glCompileShader(shader);
        if (GL33C.glGetShaderi(shader, GL33C.GL_COMPILE_STATUS) == 0) throw new AssertionError(GL33C.glGetShaderInfoLog(shader));
        return shader;
    }
    private static void benchmark(boolean enabled) {
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long[] nativeTimes = new long[9], kernelTimes = new long[9], nativeBytes = new long[9], kernelBytes = new long[9];
        for (int pass = 0; pass < 12; pass++) for (int order = 0; order < 2; order++) {
            boolean deferred = (pass + order) % 2 == 0;
            long allocated = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()), start = System.nanoTime();
            for (int i = 0; i < 4096; i++) {
                VertexBuffer buffer = deferred ? new DeferredSectionVertexBuffer(BufferUsage.STATIC_WRITE) : new VertexBuffer(BufferUsage.STATIC_WRITE);
                buffer.close();
            }
            long duration = System.nanoTime() - start, bytes = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - allocated;
            if (pass >= 3) { (deferred ? kernelTimes : nativeTimes)[pass - 3] = duration; (deferred ? kernelBytes : nativeBytes)[pass - 3] = bytes; }
        }
        Arrays.sort(nativeTimes); Arrays.sort(kernelTimes); Arrays.sort(nativeBytes); Arrays.sort(kernelBytes);
        System.out.printf("Kernel unused section buffer benchmark: enabled=%s, native/deferred %.2f/%.2f ns and %.1f/%.1f bytes per create+close%n",
            enabled, nativeTimes[4] / 4096.0, kernelTimes[4] / 4096.0, nativeBytes[4] / 4096.0, kernelBytes[4] / 4096.0);
    }
    *///? }
}
