package dev.kernel.fabric.shader;

import dev.kernel.fabric.shader.pack.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryUtil;

public final class ShaderTextureGlChecks {
    private static final int[] COLORS = {0x00123456,0x80112233,0xffabcdef,0x44a0b0c0,0xff010203,0x00224466};
    public static void run() throws Exception {
        dev.kernel.fabric.shader.pack.ShaderTexturePreparationChecks.run();
        byte[] png = png();
        var reference = ImageIO.read(new java.io.ByteArrayInputStream(png));
        if (!Arrays.equals(COLORS, reference.getRGB(0, 0, 3, 2, null, 0, 3))) throw new AssertionError("PNG fixture encoder changed pixels");
        for (int repetition = 0; repetition < 64; repetition++)
            checkImagePixels(ShaderTextureImage.decode(png, false, false).pixels(), "repeated decoded PNG");
        int pack = GL33C.glGetInteger(GL33C.GL_PIXEL_PACK_BUFFER_BINDING);
        int[] stores = {GL33C.GL_PACK_ROW_LENGTH, GL33C.GL_PACK_SKIP_ROWS, GL33C.GL_PACK_SKIP_PIXELS, GL33C.GL_PACK_ALIGNMENT};
        int[] previous = new int[stores.length];
        for (int index = 0; index < stores.length; index++) {
            previous[index] = GL33C.glGetInteger(stores[index]); GL33C.glPixelStorei(stores[index], index == 3 ? 1 : 0);
        }
        GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, 0);
        int source = GL33C.glGenTextures(), pbo = GL33C.glGenBuffers();
        try (var state = new ShaderGlState(16, 8)) {
            state.prepare();
            lifecycle(png);
            for (boolean blur : new boolean[]{false,true}) for (boolean clamp : new boolean[]{false,true})
                for (String binding : new String[]{"customTexture.lookup", "texture.composite.gaux4", "texture.composite.gcolor", "texture.noise"}) {
                    String sampler = binding.equals("customTexture.lookup") ? "lookup" : binding.equals("texture.noise") ? "noisetex"
                        : binding.equals("texture.composite.gcolor") ? "texture" : "colortex7";
                    var files = new LinkedHashMap<String, byte[]>();
                    files.put("final.fsh", ("#version 120\nvarying vec2 texcoord; uniform sampler2D " + sampler
                        + ";void main(){gl_FragColor=texture2D(" + sampler + ",texcoord*vec2(2.0,2.0)-vec2(.5));}").getBytes(StandardCharsets.UTF_8));
                    files.put("shaders.properties", (binding + "=textures/a.png\n").getBytes(StandardCharsets.UTF_8));
                    files.put("textures/a.png", png);
                    files.put("textures/a.png.mcmeta", ("{\"texture\":{\"blur\":" + blur + ",\"clamp\":" + clamp + "}}").getBytes(StandardCharsets.UTF_8));
                    var prepared = pack(files);
                    checkImagePixels(prepared.textures().values().iterator().next().pixels(), "prepared PNG");
                    setUnpack(pbo);
                    try (var pipeline = new ShaderPipeline(prepared)) {
                        checkUnpack(pbo);
                        for (int width : new int[]{12,9}) {
                            resetUnpack(); upload(source,width,8); setUnpack(pbo);
                            pipeline.render(source,width,8); checkUnpack(pbo);
                            var data = MemoryUtil.memAlloc(width*8*4);
                            try {
                                GL33C.glBindTexture(GL33C.GL_TEXTURE_2D,source);
                                GL33C.glGetTexImage(GL33C.GL_TEXTURE_2D,0,GL33C.GL_RGBA,GL33C.GL_UNSIGNED_BYTE,data);
                                for (int y=0;y<8;y++) for(int x=0;x<width;x++) for(int channel=0;channel<4;channel++) {
                                    double u=(x+.5)/width*2-.5, v=(y+.5)/8*2-.5;
                                    double expected = sample(u,v,channel,blur,clamp);
                                    int actual=data.get((y*width+x)*4+channel)&255;
                                    if(Math.abs(actual-expected)>1.2) {
                                        byte[] row = new byte[width * 4]; data.get(0, row);
                                        throw new AssertionError(binding+" filter="+blur+" clamp="+clamp+" pixel "+x+","+y+" channel "+channel+": "+actual+" != "+expected+"; first row="+Arrays.toString(row));
                                    }
                                }
                            } finally { MemoryUtil.memFree(data); }
                        }
                    }
                }
            resetUnpack(); upload(source,8,5);
            reusedUnits(source,png);
            try (var targets = new ShaderColorTargets(1, ShaderBufferSettings.defaults(), 0, 128L*1024*1024)) {
                try { targets.begin(source,7200,7200); throw new AssertionError("Custom-image reservation ignored"); }
                catch(java.io.IOException expected) { if(!expected.getMessage().contains("budget")) throw expected; }
            }
            if(GL33C.glGetError()!=GL33C.GL_NO_ERROR) throw new AssertionError("Custom texture GL error");
        } finally {
            GL33C.glDeleteTextures(source); GL33C.glDeleteBuffers(pbo);
            GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, pack);
            for (int index = 0; index < stores.length; index++) GL33C.glPixelStorei(stores[index], previous[index]);
        }
        System.out.println("Kernel custom texture GPU checks passed: ZIP PNGs, named/noise/alias inputs, nearest/linear, repeat/clamp, resizing, upload state and shared budget");
    }
    private static void reusedUnits(int source, byte[] png) throws Exception {
        var files = new LinkedHashMap<String, byte[]>();
        var properties = new StringBuilder();
        for (int index = 0; index < 32; index++) {
            properties.append("customTexture.image").append(index).append("=image").append(index).append(".png\n");
            files.put("image" + index + ".png", png);
        }
        for (int pass = 0; pass < 16; pass++) {
            String a = "image" + pass*2, b = "image" + (pass*2+1), color = "colortex" + pass;
            String program = "#version 120\nuniform sampler2D " + a + "; uniform sampler2D " + b + "; uniform sampler2D " + color
                + "; varying vec2 texcoord; void main(){gl_FragColor=(texture2D(" + a + ",texcoord)+texture2D(" + b
                + ",texcoord))*.5+texture2D(" + color + ",texcoord)*.001;}";
            files.put((pass == 0 ? "composite" : "composite" + pass) + ".fsh", program.getBytes(StandardCharsets.UTF_8));
        }
        files.put("shaders.properties", properties.toString().getBytes(StandardCharsets.UTF_8));
        var prepared = pack(files);
        try (var pipeline = new ShaderPipeline(prepared)) {
            upload(source,12,8); pipeline.render(source,12,8);
            var values=MemoryUtil.memAlloc(12*8*4);
            try {
                GL33C.glBindTexture(GL33C.GL_TEXTURE_2D,source);
                GL33C.glGetTexImage(GL33C.GL_TEXTURE_2D,0,GL33C.GL_RGBA,GL33C.GL_UNSIGNED_BYTE,values);
                for(int y=0;y<8;y++) for(int x=0;x<12;x++) for(int c=0;c<4;c++) {
                    double expected=sample((x+.5)/12,(y+.5)/8,c,false,false);
                    if(Math.abs(expected-(values.get((y*12+x)*4+c)&255))>1) throw new AssertionError("Per-pass input reuse changed pixels");
                }
            } finally { MemoryUtil.memFree(values); }
        }
        System.out.println("Custom texture per-pass units passed: 48 distinct inputs across 16 passes, at most three units per pass");
    }
    private static void lifecycle(byte[] png) throws Exception {
        var first=ShaderTextureImage.decode(png,false,false);
        var second=ShaderTextureImage.decode(png,true,true);
        checkImagePixels(first.pixels(), "decoded PNG");
        int texture,sampler;
        try(var owned=new ShaderCustomTextures(List.of(first,second),1L<<16)) {
            texture=owned.texture(0); sampler=owned.sampler(0);
            if(!GL33C.glIsTexture(texture) || !GL33C.glIsSampler(sampler) || owned.texture(1)!=0 || owned.sampler(1)!=0 || owned.bytes()!=24)
                throw new AssertionError("Unused image allocated or live image missing");
            var pixels = MemoryUtil.memAlloc(24);
            try {
                GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, texture);
                GL33C.glGetTexImage(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, pixels);
                checkImagePixels(pixels, "uploaded PNG");
            } finally { MemoryUtil.memFree(pixels); }
        }
        if(GL33C.glIsTexture(texture) || GL33C.glIsSampler(sampler)) throw new AssertionError("Custom texture resources leaked");
        var files = new LinkedHashMap<String,byte[]>();
        files.put("shaders.properties", "texture.composite.colortex7=image.png".getBytes(StandardCharsets.UTF_8));
        files.put("image.png",png);
        files.put("final.fsh", ("#version 120\nconst bool colortex7MipmapEnabled=true;\nuniform sampler2D gaux4; varying vec2 texcoord;"
            + "void main(){gl_FragColor=texture2D(gaux4,texcoord);}").getBytes(StandardCharsets.UTF_8));
        try(var ignored=new ShaderPipeline(pack(files))) { throw new AssertionError("Image override silently consumed a color mipmap request"); }
        catch(java.io.IOException expected) { if(!expected.getMessage().contains("overridden image")) throw expected; }
    }
    private static double sample(double u,double v,int channel,boolean blur,boolean clamp) {
        if(!blur) return color((int)Math.floor(u*3),(int)Math.floor(v*2),channel,clamp);
        double x=u*3-.5,y=v*2-.5; int ix=(int)Math.floor(x),iy=(int)Math.floor(y); double fx=x-ix,fy=y-iy;
        return (color(ix,iy,channel,clamp)*(1-fx)+color(ix+1,iy,channel,clamp)*fx)*(1-fy)
            +(color(ix,iy+1,channel,clamp)*(1-fx)+color(ix+1,iy+1,channel,clamp)*fx)*fy;
    }
    private static void checkImagePixels(java.nio.ByteBuffer pixels, String stage) {
        for (int index = 0; index < COLORS.length; index++) for (int channel = 0; channel < 4; channel++) {
            int expected = COLORS[index] >>> new int[]{16, 8, 0, 24}[channel] & 255;
            if ((pixels.get(index * 4 + channel) & 255) != expected) {
                byte[] actual = new byte[24]; pixels.get(0, actual);
                throw new AssertionError(stage + " differs at pixel " + index + " channel " + channel + ": " + Arrays.toString(actual));
            }
        }
    }
    private static int color(int x,int y,int channel,boolean clamp) {
        x=clamp?Math.max(0,Math.min(2,x)):Math.floorMod(x,3); y=clamp?Math.max(0,Math.min(1,y)):Math.floorMod(y,2);
        return (COLORS[y*3+x] >>> new int[]{16,8,0,24}[channel])&255;
    }
    private static void setUnpack(int pbo) {
        GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER,pbo);
        GL33C.glPixelStorei(GL33C.GL_UNPACK_ROW_LENGTH,7); GL33C.glPixelStorei(GL33C.GL_UNPACK_SKIP_ROWS,2);
        GL33C.glPixelStorei(GL33C.GL_UNPACK_SKIP_PIXELS,1); GL33C.glPixelStorei(GL33C.GL_UNPACK_ALIGNMENT,8);
    }
    private static void checkUnpack(int pbo) {
        if(GL33C.glGetInteger(GL33C.GL_PIXEL_UNPACK_BUFFER_BINDING)!=pbo || GL33C.glGetInteger(GL33C.GL_UNPACK_ROW_LENGTH)!=7
            || GL33C.glGetInteger(GL33C.GL_UNPACK_SKIP_ROWS)!=2 || GL33C.glGetInteger(GL33C.GL_UNPACK_SKIP_PIXELS)!=1
            || GL33C.glGetInteger(GL33C.GL_UNPACK_ALIGNMENT)!=8) throw new AssertionError("Custom image upload state leaked");
    }
    private static void resetUnpack() {
        GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER,0);
        GL33C.glPixelStorei(GL33C.GL_UNPACK_ROW_LENGTH,0); GL33C.glPixelStorei(GL33C.GL_UNPACK_SKIP_ROWS,0);
        GL33C.glPixelStorei(GL33C.GL_UNPACK_SKIP_PIXELS,0); GL33C.glPixelStorei(GL33C.GL_UNPACK_ALIGNMENT,1);
    }
    private static void upload(int texture,int width,int height) {
        GL33C.glBindTexture(GL33C.GL_TEXTURE_2D,texture);
        GL33C.glTexImage2D(GL33C.GL_TEXTURE_2D,0,GL33C.GL_RGBA8,width,height,0,GL33C.GL_RGBA,GL33C.GL_UNSIGNED_BYTE,0L);
    }
    private static byte[] png() throws Exception { return png(3,2,COLORS); }
    private static byte[] png(int width,int height,int[] colors) throws Exception {
        var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB); image.setRGB(0,0,width,height,colors,0,width);
        var encoded=new ByteArrayOutputStream();
        try(var output=new MemoryCacheImageOutputStream(encoded)) { if(!ImageIO.write(image,"png",output)) throw new AssertionError("PNG encoder unavailable"); }
        return encoded.toByteArray();
    }
    public static void writeImportFixture(java.nio.file.Path path) throws Exception {
        var files=Map.of(
            "shaders/final.fsh", """
                #version 120
                uniform sampler2D probeImage, depthtex0;
                uniform int worldTime, worldDay, moonPhase;
                uniform float rainStrength, thunderStrength;
                uniform mat4 gbufferProjection, gbufferProjectionInverse, gbufferPreviousProjection;
                void main() {
                    if (gl_FragCoord.x < 2.0) gl_FragColor = vec4(rainStrength, thunderStrength, float(moonPhase)/7.0, 1.0);
                    else if (gl_FragCoord.x < 4.0) gl_FragColor = vec4(float(worldTime)/24000.0, float(worldDay%256)/255.0, 0.0, 1.0);
                    else if (gl_FragCoord.x < 6.0) gl_FragColor = vec4(vec3(texture2D(depthtex0,vec2(.5)).r),1.0);
                    else if (gl_FragCoord.x < 22.0) {
                        int index = int(gl_FragCoord.x) - 6, row = int(gl_FragCoord.y) % 3;
                        float value = row == 0 ? gbufferProjection[index/4][index%4]
                            : row == 1 ? gbufferProjectionInverse[index/4][index%4] : gbufferPreviousProjection[index/4][index%4];
                        uint bits = floatBitsToUint(value);
                        gl_FragColor = vec4(float(bits & 255u), float((bits >> 8) & 255u), float((bits >> 16) & 255u), float(bits >> 24)) / 255.0;
                    }
                    else gl_FragColor = texture2D(probeImage,vec2(.5));
                }
                """.getBytes(StandardCharsets.UTF_8),
            "shaders/shaders.properties", "customTexture.probeImage=probe.png\n".getBytes(StandardCharsets.UTF_8),
            "shaders/probe.png", png(1,1,new int[]{0xff4080bf}));
        try(var output=new ZipOutputStream(Files.newOutputStream(path))) {
            for(var entry:files.entrySet()) { output.putNextEntry(new ZipEntry(entry.getKey())); output.write(entry.getValue()); output.closeEntry(); }
        }
    }
    private static PreparedShaderPack pack(Map<String,byte[]> files) throws Exception {
        var path=Files.createTempFile("kernel-shader-texture-test-", ".zip");
        try {
            try(var output=new ZipOutputStream(Files.newOutputStream(path))) {
                for(var entry:files.entrySet()) { output.putNextEntry(new ZipEntry("shaders/"+entry.getKey())); output.write(entry.getValue()); output.closeEntry(); }
            }
            return PreparedShaderPack.read(path);
        } finally { Files.deleteIfExists(path); }
    }
}
