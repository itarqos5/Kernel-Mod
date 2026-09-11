package dev.kernel.fabric.shader.pack;
import java.io.IOException;
import java.util.Map;
class ShaderTextureBindingsTest {
    @org.junit.jupiter.api.Test void boundsBindingsAndWhitespaceScanning() throws Exception {
        var properties = new StringBuilder();
        for (int index = 0; index < 32; index++) properties.append("customTexture.image").append(index).append("=image.png\n");
        org.junit.jupiter.api.Assertions.assertEquals(32, ShaderTextureBindings.read(properties.toString()).size());
        properties.append("customTexture.excess=image.png\n");
        org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () -> ShaderTextureBindings.read(properties.toString()));
        org.junit.jupiter.api.Assertions.assertTimeout(java.time.Duration.ofSeconds(5),
            () -> ShaderTextureBindings.read(" \n".repeat(524288)));
    }
    @org.junit.jupiter.api.Test void validatesBindingNamesAliasesAndSupportedProperties() throws Exception {
        var parsed = ShaderTextureBindings.read("""
            # Literal texture inputs
            texture.composite.gaux4 = textures/pattern.png
            texture.composite.colortex7 = textures/pattern.png
            customTexture.lookup = textures/lookup.png
            texture.noise = textures/noise.png
            """);
        if (!parsed.equals(Map.of("colortex7","textures/pattern.png", "lookup","textures/lookup.png", "noisetex","textures/noise.png")))
            throw new AssertionError("Texture names or aliases differ");
        for(String bad:new String[]{"texture.composite.gaux4=a.png\ntexture.composite.colortex7=b.png",
            "customTexture.lookup=a.png\ncustomTexture.lookup=b.png", "customTexture.gl_test=a.png", "customTexture.frameCounter=a.png",
            "customTexture.worldTime=a.png", "customTexture.worldDay=a.png", "customTexture.moonPhase=a.png",
            "customTexture.rainStrength=a.png", "customTexture.thunderStrength=a.png",
            "customTexture.depthtex0=a.png", "customTexture.gdepthtex=a.png",
            "customTexture.colortex0=a.png", "customTexture.noise=a.raw TEXTURE_3D R8 8 8 8 RED UNSIGNED_BYTE",
            "texture.deferred.colortex0=a.png", "texture.composite.colortex0=minecraft:textures/block/stone.png",
            "texture.composite.colortex0=C:/secret.png", "#if FLAG\ncustomTexture.name=a.png\n#endif", "texture.composite.colortex16=a.png"}) {
            try { ShaderTextureBindings.read(bad); throw new AssertionError("Unsupported texture property accepted: "+bad); }
            catch(IOException expected) { }
        }
        System.out.println("Texture bindings passed: aliases, reserved names, conflicts and unsupported properties");
    }
}
