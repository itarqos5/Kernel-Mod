package dev.kernel.fabric.shader.pack;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.IIOImage;
import javax.imageio.stream.MemoryCacheImageOutputStream;

class PngTextureBoundsTest {
    @org.junit.jupiter.api.Test void validatesPngEncodingsAndRejectsMalformedOrOversizedPixelStreams() throws Exception {
        int count=0;
        for(int type:new int[]{BufferedImage.TYPE_BYTE_GRAY,BufferedImage.TYPE_USHORT_GRAY,BufferedImage.TYPE_BYTE_BINARY,
            BufferedImage.TYPE_BYTE_INDEXED,BufferedImage.TYPE_INT_RGB,BufferedImage.TYPE_INT_ARGB})
            for(boolean interlaced:new boolean[]{false,true}) for(int[] size:new int[][]{{1,1},{3,2},{17,9}}) {
                var image=new BufferedImage(size[0],size[1],type);
                for(int y=0;y<size[1];y++) for(int x=0;x<size[0];x++) image.setRGB(x,y,0xff000000 | (x*39123+y*77134));
                var bytes=new ByteArrayOutputStream(); var writer=ImageIO.getImageWritersByFormatName("png").next();
                try(var output=new MemoryCacheImageOutputStream(bytes)) {
                    writer.setOutput(output); var params=writer.getDefaultWriteParam();
                    params.setProgressiveMode(interlaced?ImageWriteParam.MODE_DEFAULT:ImageWriteParam.MODE_DISABLED);
                    writer.write(null,new IIOImage(image,null,null),params);
                } finally { writer.dispose(); }
                byte[] png=bytes.toByteArray();
                if((png[28]!=0)!=interlaced) throw new AssertionError("PNG encoder interlace mismatch");
                PngTextureBounds.validate(png); count++;
            }
        byte[] valid=png(new byte[]{0,1,2,3,4}); PngTextureBounds.validate(valid);
        reject(png(new byte[1024*1024])); reject(png(new byte[]{0,1,2,3}));
        byte[] damaged=valid.clone(); damaged[damaged.length-1]^=1; reject(damaged);
        reject(Arrays.copyOf(valid,valid.length-12)); reject(Arrays.copyOf(valid,valid.length+1));
        byte[] huge=valid.clone(); ByteBuffer.wrap(huge).putInt(33,Integer.MAX_VALUE); reject(huge);
        var duplicate=new ByteArrayOutputStream(); duplicate.write(valid,0,33); duplicate.write(valid,8,25); duplicate.write(valid,33,valid.length-33);
        reject(duplicate.toByteArray());
        System.out.println("PNG preflight passed "+count+" valid format/interlace/size combinations, stream bounds, truncation, CRC and duplicate-header rejection");
    }
    private static byte[] png(byte[] raw) throws Exception {
        var result=new ByteArrayOutputStream(); var output=new DataOutputStream(result);
        output.writeLong(0x89504e470d0a1a0aL);
        var header=ByteBuffer.allocate(13).putInt(1).putInt(1).put((byte)8).put((byte)6).put((byte)0).put((byte)0).put((byte)0).array();
        chunk(output,0x49484452,header);
        var compressed=new ByteArrayOutputStream();
        try(var deflater=new DeflaterOutputStream(compressed)) { deflater.write(raw); }
        chunk(output,0x49444154,compressed.toByteArray()); chunk(output,0x49454e44,new byte[0]);
        return result.toByteArray();
    }
    private static void chunk(DataOutputStream output,int type,byte[] data) throws Exception {
        output.writeInt(data.length); output.writeInt(type); output.write(data);
        var crc=new CRC32(); crc.update(ByteBuffer.allocate(4).putInt(type).array()); crc.update(data); output.writeInt((int)crc.getValue());
    }
    private static void reject(byte[] png) throws Exception {
        try { PngTextureBounds.validate(png); throw new AssertionError("Invalid PNG pixel stream accepted"); }
        catch(IOException expected) { }
    }
}
