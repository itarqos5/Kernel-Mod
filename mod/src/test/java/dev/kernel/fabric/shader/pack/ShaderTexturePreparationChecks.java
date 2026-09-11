package dev.kernel.fabric.shader.pack;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;

public final class ShaderTexturePreparationChecks {
    public static void run() throws Exception {
        var image=new BufferedImage(3,2,BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0,0,0x80123456);
        var bytes=new ByteArrayOutputStream();
        try(var output=new MemoryCacheImageOutputStream(bytes)) { ImageIO.write(image,"png",output); }
        var files=new LinkedHashMap<String,byte[]>();
        files.put("textures/a.png",bytes.toByteArray());
        files.put("textures/a.png.mcmeta",text("{\"texture\":{\"blur\":true,\"clamp\":true}}"));
        files.put("shaders.properties",text("customTexture.lookup=textures/./a.png\ntexture.noise=textures/temp/../a.png"));
        var count=new AtomicInteger();
        var decoded=read(files,(png,blur,clamp)->{ count.incrementAndGet(); return ShaderTextureImage.decode(png,blur,clamp); },128L*1024*1024);
        if(count.get()!=1 || decoded.size()!=2 || decoded.get("lookup")!=decoded.get("noisetex") || !decoded.get("lookup").blur() || !decoded.get("lookup").clamp())
            throw new AssertionError("Canonical texture deduplication or metadata differs");
        try { decoded.clear(); throw new AssertionError("Mutable prepared texture bindings"); }
        catch(UnsupportedOperationException expected) { }
        var pixels = decoded.get("lookup").pixels();
        if ((pixels.get(0) & 255) != 0x12 || (pixels.get(1) & 255) != 0x34
            || (pixels.get(2) & 255) != 0x56 || (pixels.get(3) & 255) != 0x80)
            throw new AssertionError("Decoded RGBA channels or alpha differs");
        try { pixels.put(0, (byte) 0); throw new AssertionError("Mutable prepared texture pixels"); }
        catch (java.nio.ReadOnlyBufferException expected) { }
        pixels.position(4);
        if (decoded.get("lookup").pixels().position() != 0) throw new AssertionError("Pixel views share cursor state");
        for(String bad:new String[]{"customTexture.lookup=../outside.png", "customTexture.lookup=missing.png"}) {
            files.put("shaders.properties",text(bad)); count.set(0);
            try { read(files,(png,blur,clamp)->{ count.incrementAndGet(); throw new AssertionError("Decoder reached invalid file"); },48); throw new AssertionError("Invalid file accepted"); }
            catch(IOException expected) { }
            if(count.get()!=0) throw new AssertionError("Invalid path reached decoder");
        }
        files.put("b.png",bytes.toByteArray()); files.put("c.png",bytes.toByteArray());
        files.put("shaders.properties",text("customTexture.a=textures/a.png\ncustomTexture.b=b.png\ncustomTexture.c=c.png"));
        count.set(0);
        try { read(files,(png,blur,clamp)->{ count.incrementAndGet(); return ShaderTextureImage.decode(png,blur,clamp); },48); throw new AssertionError("Preparation budget exceeded"); }
        catch(IOException expected) { if(!expected.getMessage().contains("budget")) throw expected; }
        if(count.get()!=2) throw new AssertionError("Budget check ran after decoding a third image");
        count.set(0);
        Thread.currentThread().interrupt();
        try {
            read(files,(png,blur,clamp)->{ count.incrementAndGet(); return ShaderTextureImage.decode(png,blur,clamp); },128L*1024*1024);
            throw new AssertionError("Interrupted preparation continued");
        } catch(InterruptedIOException expected) { }
        finally { Thread.interrupted(); }
        if(count.get()!=0) throw new AssertionError("Cancellation reached decoding");
        try {
            read(files,(png,blur,clamp)->{ var result=ShaderTextureImage.decode(png,blur,clamp); Thread.currentThread().interrupt(); return result; },128L*1024*1024);
            throw new AssertionError("Cancelled decoded data was published");
        } catch(InterruptedIOException expected) { }
        finally { Thread.interrupted(); }
        System.out.println("Texture preparation passed: canonical sharing, metadata, immutable bindings, missing/escaping paths, before-decode budget and cancellation");
    }
    private static byte[] text(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static Map<String,ShaderTextureImage> read(Map<String,byte[]> files,PreparedShaderTextures.Decoder decoder,long budget) throws Exception {
        var path=Files.createTempFile("kernel-texture-prepare-", ".zip");
        try {
            try(var output=new ZipOutputStream(Files.newOutputStream(path))) {
                output.putNextEntry(new ZipEntry("shaders/final.fsh")); output.write(text("void main(){}")); output.closeEntry();
                for(var entry:files.entrySet()) { output.putNextEntry(new ZipEntry("shaders/"+entry.getKey())); output.write(entry.getValue()); output.closeEntry(); }
            }
            try(var archive=new ShaderPackArchive(path)) { return PreparedShaderTextures.read(archive,decoder,budget); }
        } finally { Files.deleteIfExists(path); }
    }
}
