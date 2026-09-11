package dev.kernel.fabric.shader.pack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShaderTextureImageTest {
    @Test void boundsDimensionsAndDecodedBytesBeforeNativeAllocation() throws Exception {
        assertEquals(24, ShaderTextureImage.decodedBytes(header(3, 2)));
        assertEquals(64 * 1024 * 1024, ShaderTextureImage.decodedBytes(header(4096, 4096)));
        for (int[] size : new int[][]{{0, 2}, {2, 0}, {-1, 2}, {16385, 1}, {1, 16385},
            {4096, 4097}, {16384, 16384}, {Integer.MAX_VALUE, Integer.MAX_VALUE}})
            assertThrows(IOException.class, () -> ShaderTextureImage.decodedBytes(header(size[0], size[1])));
    }

    @Test void rejectsMalformedHeadersAndExcessiveEncodedBytes() {
        assertThrows(IOException.class, () -> ShaderTextureImage.decodedBytes(new byte[32]));
        assertThrows(IOException.class, () -> ShaderTextureImage.decodedBytes(new byte[16 * 1024 * 1024 + 1]));
        for (int offset : new int[]{0, 8, 12, 16, 29}) {
            byte[] invalid = header(3, 2); invalid[offset] ^= 1;
            assertThrows(IOException.class, () -> ShaderTextureImage.decodedBytes(invalid));
        }
    }

    private static byte[] header(int width, int height) {
        var header = ByteBuffer.allocate(33);
        header.putLong(0x89504e470d0a1a0aL).putInt(13).putInt(0x49484452).putInt(width).putInt(height)
            .put((byte) 8).put((byte) 6).put((byte) 0).put((byte) 0).put((byte) 0);
        var crc = new CRC32(); crc.update(header.array(), 12, 17); header.putInt((int) crc.getValue());
        return header.array();
    }
}
