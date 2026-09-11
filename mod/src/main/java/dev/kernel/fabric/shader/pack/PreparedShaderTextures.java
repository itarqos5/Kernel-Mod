package dev.kernel.fabric.shader.pack;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded pack-local images, prepared without any GPU access on Kernel's IO worker. */
public final class PreparedShaderTextures {
    static final long MAX_RGBA_BYTES = 128L * 1024 * 1024;
    private PreparedShaderTextures() {}
    @FunctionalInterface interface Decoder {
        ShaderTextureImage decode(byte[] png, boolean blur, boolean clamp) throws IOException;
    }
    public static Map<String, ShaderTextureImage> read(ShaderPackArchive archive) throws IOException {
        return read(archive, ShaderTextureImage::decode);
    }
    static Map<String, ShaderTextureImage> read(ShaderPackArchive archive, Decoder decoder) throws IOException {
        return read(archive, decoder, MAX_RGBA_BYTES);
    }
    static Map<String, ShaderTextureImage> read(ShaderPackArchive archive, Decoder decoder, long budget) throws IOException {
        if (budget < 0 || budget > MAX_RGBA_BYTES) throw new IllegalArgumentException("Invalid texture preparation budget");
        if (!archive.contains("shaders.properties")) return Map.of();
        var declarations = ShaderTextureBindings.read(archive.source("shaders.properties"));
        var images = new LinkedHashMap<String, ShaderTextureImage>();
        var bindings = new LinkedHashMap<String, ShaderTextureImage>();
        long total = 0;
        for (var declaration : declarations.entrySet()) {
            checkCancelled();
            String path = declaration.getValue();
            var image = images.get(path);
            if (image == null) {
                byte[] png = archive.bytes(path, ShaderTextureImage.MAX_ENCODED_BYTES);
                int bytes = ShaderTextureImage.decodedBytes(png);
                if (bytes > budget - total) throw new IOException("Custom shader textures exceed the preparation memory budget");
                String metadataPath = path + ".mcmeta";
                var metadata = archive.contains(metadataPath) ? ShaderTextureMetadata.read(archive.text(metadataPath, 65536))
                    : new ShaderTextureMetadata(false, false);
                checkCancelled();
                image = decoder.decode(png, metadata.blur(), metadata.clamp());
                checkCancelled();
                if (image.bytes() != bytes) throw new IOException("Decoded texture differs from its declared size");
                images.put(path, image); total += bytes;
            }
            bindings.put(declaration.getKey(), image);
        }
        return Collections.unmodifiableMap(bindings);
    }
    private static void checkCancelled() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Shader texture preparation cancelled");
    }
}
