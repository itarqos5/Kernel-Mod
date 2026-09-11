package dev.kernel.fabric.shader.pack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Bounds the PNG's filtered pixel stream before passing compressed data to the native decoder. */
final class PngTextureBounds {
    private static final int IHDR = 0x49484452, IDAT = 0x49444154, IEND = 0x49454e44, PLTE = 0x504c5445;
    private PngTextureBounds() {}
    static void validate(byte[] png) throws IOException {
        ShaderTextureImage.decodedBytes(png);
        var data = ByteBuffer.wrap(png);
        int width = data.getInt(16), height = data.getInt(20), depth = png[24] & 255, color = png[25] & 255;
        int channels = switch (color) { case 0, 3 -> 1; case 2 -> 3; case 4 -> 2; case 6 -> 4; default -> 0; };
        boolean depthValid = color == 0 ? depth == 1 || depth == 2 || depth == 4 || depth == 8 || depth == 16
            : color == 3 ? depth == 1 || depth == 2 || depth == 4 || depth == 8 : depth == 8 || depth == 16;
        if (channels == 0 || !depthValid || png[26] != 0 || png[27] != 0 || png[28] < 0 || png[28] > 1)
            throw new IOException("Unsupported PNG header encoding");
        long expected = filteredBytes(width, height, channels * depth, png[28] != 0), decoded = 0;
        var inflater = new Inflater();
        var crc = new CRC32();
        byte[] scratch = new byte[8192];
        boolean pixels = false, endedPixels = false, end = false;
        try {
            for (int offset = 8; offset < png.length;) {
                if (png.length - offset < 12) throw new IOException("Truncated PNG chunk");
                int length = data.getInt(offset), type = data.getInt(offset + 4);
                if (length < 0 || length > png.length - offset - 12) throw new IOException("Invalid PNG chunk size");
                crc.reset(); crc.update(png, offset + 4, length + 4);
                if ((int) crc.getValue() != data.getInt(offset + length + 8)) throw new IOException("PNG chunk checksum differs");
                if (type == IHDR && offset != 8) throw new IOException("Duplicate PNG header");
                if (type == IDAT) {
                    if (endedPixels || inflater.finished() && length != 0) throw new IOException("Unexpected PNG pixel stream");
                    pixels = true;
                    inflater.setInput(png, offset + 8, length);
                    while (!inflater.needsInput() && !inflater.finished()) {
                        int count = inflater.inflate(scratch);
                        decoded += count;
                        if (decoded > expected) throw new IOException("PNG pixel stream exceeds its declared dimensions");
                        if (inflater.needsDictionary()) throw new IOException("PNG pixel stream requires a dictionary");
                        if (count == 0 && !inflater.needsInput() && !inflater.finished()) throw new IOException("Stalled PNG pixel stream");
                    }
                    if (inflater.finished() && inflater.getRemaining() != 0) throw new IOException("Unexpected data after PNG pixel stream");
                } else {
                    if (pixels) endedPixels = true;
                    if (type != IHDR && type != PLTE && type != IEND && (png[offset + 4] & 32) == 0)
                        throw new IOException("Unsupported critical PNG chunk");
                }
                offset += length + 12;
                if (type == IEND) {
                    if (length != 0 || offset != png.length) throw new IOException("Unexpected data after PNG end");
                    end = true;
                }
            }
            if (!end || !pixels || !inflater.finished() || decoded != expected) throw new IOException("Incomplete PNG pixel stream");
        } catch (DataFormatException invalid) { throw new IOException("Invalid PNG compressed data", invalid); }
        finally { inflater.end(); }
    }
    private static long filteredBytes(int width, int height, int bitsPerPixel, boolean interlaced) {
        if (!interlaced) return (((long) width * bitsPerPixel + 7) / 8 + 1) * height;
        int[] x = {0,4,0,2,0,1,0}, y = {0,0,4,0,2,0,1}, dx = {8,8,4,4,2,2,1}, dy = {8,8,8,4,4,2,2};
        long bytes = 0;
        for (int pass = 0; pass < 7; pass++) {
            int w = (width - x[pass] + dx[pass] - 1) / dx[pass], h = (height - y[pass] + dy[pass] - 1) / dy[pass];
            if (w > 0 && h > 0) bytes += (((long) w * bitsPerPixel + 7) / 8 + 1) * h;
        }
        return bytes;
    }
}
