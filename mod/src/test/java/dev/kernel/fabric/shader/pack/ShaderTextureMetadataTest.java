package dev.kernel.fabric.shader.pack;
import java.io.IOException;
class ShaderTextureMetadataTest {
    @org.junit.jupiter.api.Test void validatesFilteringMetadataWithoutUnboundedNesting() throws Exception {
        if (!ShaderTextureMetadata.read("{}").equals(new ShaderTextureMetadata(false,false))
            || !ShaderTextureMetadata.read("{\"texture\":{\"blur\":true,\"clamp\":true}}").equals(new ShaderTextureMetadata(true,true)))
            throw new AssertionError("Texture options differ");
        for(String bad:new String[]{"{\"texture\":{\"blur\":\"true\"}}", "{\"texture\":{\"blur\":true,\"blur\":false}}",
            "{\"texture\":{},\"texture\":{}}", "{\"animation\":{}}", "{\"texture\":{\"future\":true}}", "[]", "{\"texture\":[]}",
            "{} {}", "{\"texture\":{\"blur\":1}}", "{\"texture\":{\"blur\":null}}", "[".repeat(10000)}) {
            try { ShaderTextureMetadata.read(bad); throw new AssertionError("Unsupported metadata accepted"); }
            catch(IOException expected) { }
        }
        System.out.println("Texture metadata passed: defaults, filters, types, duplicate/unknown fields and bounded nesting");
    }
}
