package dev.kernel.fabric.shader.pack;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Fixtures here are original Kernel GLSL shaped like a Minecraft core program. No Minecraft shader
 * source is copied into this repository.
 */
class ShaderWorldTranslationTest {
    /** A core vertex program in the older shape: matrices declared locally, a model offset, a normal. */
    private static final String LOCAL_VERTEX = """
        #version 150
        #moj_import <minecraft:fog.glsl>
        in vec3 Position;
        in vec4 Color;
        in vec2 UV0;
        in ivec2 UV2;
        in vec3 Normal;
        uniform sampler2D Sampler0;
        uniform sampler2D Sampler2;
        uniform mat4 ModelViewMat;
        uniform mat4 ProjMat;
        uniform vec3 ModelOffset;
        out vec2 texCoord0;
        void main() {
            vec3 pos = Position + ModelOffset;
            gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
            texCoord0 = UV0;
        }
        """;
    /** The newer shape: matrices behind an import, chunk-relative position, no normal. */
    private static final String IMPORTED_VERTEX = """
        #version 330
        #moj_import <minecraft:globals.glsl>
        #moj_import <minecraft:chunksection.glsl>
        #moj_import <minecraft:projection.glsl>
        in vec3 Position;
        in vec4 Color;
        in vec2 UV0;
        in ivec2 UV2;
        uniform sampler2D Sampler2;
        out vec2 texCoord0;
        void main() {
            vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
            gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
            texCoord0 = UV0;
        }
        """;
    private static final String FRAGMENT = """
        #version 330
        uniform sampler2D Sampler0;
        in vec2 texCoord0;
        out vec4 fragColor;
        void main() { fragColor = texture(Sampler0, texCoord0); }
        """;

    private static ShaderWorldEnvironment vertexEnvironment(String source) {
        return ShaderWorldEnvironment.parse(source, true);
    }

    @Test void theEnvironmentIsReadFromTheProgramBeingReplaced() {
        var local = vertexEnvironment(LOCAL_VERTEX);
        assertNotNull(local);
        assertEquals("150", local.version());
        assertEquals(java.util.List.of("minecraft:fog.glsl"), local.imports());
        assertEquals("Position + ModelOffset", local.position());
        assertTrue(local.hasAttribute("Normal"));
        assertEquals("mat4", local.uniforms().get("ProjMat"));

        var imported = vertexEnvironment(IMPORTED_VERTEX);
        assertNotNull(imported);
        assertEquals("330", imported.version());
        assertEquals("Position + (ChunkPosition - CameraBlockPos) + CameraOffset", imported.position());
        assertFalse(imported.hasAttribute("Normal"), "this shape supplies no normal");
        // The matrices arrive through an import, so they are not declared here but are still reachable.
        assertFalse(imported.uniforms().containsKey("ProjMat"));
    }

    @Test void aProgramWithoutTheMatricesCannotBeDescribed() {
        assertNull(ShaderWorldEnvironment.parse("#version 150\nin vec3 Position;\nvoid main() {}\n", true));
        assertNull(ShaderWorldEnvironment.parse("", true));
        assertNull(ShaderWorldEnvironment.parse(null, true));
        // A fragment program has to name the output the pack will write.
        assertNull(ShaderWorldEnvironment.parse("#version 330\nuniform mat4 ProjMat;\nuniform mat4 ModelViewMat;\nvoid main() {}\n", false));
    }

    @Test void theVertexPositionKeepsWhateverRebasingTheVersionUses() throws Exception {
        String pack = "#version 120\nvarying vec2 uv;\nvoid main() { gl_Position = ftransform(); uv = gl_MultiTexCoord0.st; }\n";
        String local = ShaderWorldTranslation.translate(pack, vertexEnvironment(LOCAL_VERTEX), true);
        assertTrue(local.contains("#define kernel_Vertex vec4(Position + ModelOffset, 1.0)"), local);
        assertTrue(local.contains("(ProjMat * ModelViewMat * kernel_Vertex)"), local);
        assertTrue(local.contains("out vec2 uv;"), "varying becomes out in a vertex program");

        String imported = ShaderWorldTranslation.translate(pack, vertexEnvironment(IMPORTED_VERTEX), true);
        assertTrue(imported.contains("#define kernel_Vertex vec4(Position + (ChunkPosition - CameraBlockPos) + CameraOffset, 1.0)"), imported);
        assertTrue(imported.startsWith("#version 330\n"), imported);
        assertTrue(imported.contains("#moj_import <minecraft:chunksection.glsl>"), "the environment's imports are re-emitted");
    }

    @Test void theFragmentOutputIsRenamedToTheOneTheVersionDeclares() throws Exception {
        var environment = ShaderWorldEnvironment.parse(FRAGMENT, false);
        assertNotNull(environment);
        String translated = ShaderWorldTranslation.translate(
            "#version 120\nvarying vec2 uv;\nvoid main() { gl_FragData[0] = vec4(uv, 0.0, 1.0); }\n", environment, false);
        assertTrue(translated.contains("out vec4 fragColor;"), translated);
        assertTrue(translated.contains("fragColor = vec4(uv, 0.0, 1.0);"), translated);
        assertTrue(translated.contains("in vec2 uv;"), "varying becomes in in a fragment program");
    }

    @Test void thePackColourSamplerBindsToTheMinecraftBindingAndLosesItsOwnDeclaration() throws Exception {
        var environment = ShaderWorldEnvironment.parse(FRAGMENT, false);
        String translated = ShaderWorldTranslation.translate(
            "#version 120\nuniform sampler2D texture;\nvarying vec2 uv;\nvoid main() { gl_FragColor = texture2D(texture, uv); }\n",
            environment, false);
        assertFalse(translated.contains("uniform sampler2D texture;"), "the environment already declares the binding");
        assertTrue(translated.contains("fragColor = texture(Sampler0, uv);"), translated);
    }

    @Test void programsNeedingStagesOrAttributesKernelDoesNotSupplyAreRejected() {
        var vertex = vertexEnvironment(LOCAL_VERTEX);
        var fragment = ShaderWorldEnvironment.parse(FRAGMENT, false);
        assertThrows(java.io.IOException.class, () -> ShaderWorldTranslation.translate(
            "#version 120\nattribute vec4 mc_Entity;\nvoid main() { gl_Position = ftransform(); }\n", vertex, true));
        assertThrows(java.io.IOException.class, () -> ShaderWorldTranslation.translate(
            "#version 120\nuniform sampler2D shadowtex0;\nvoid main() { gl_FragColor = vec4(1.0); }\n", fragment, false));
        assertThrows(java.io.IOException.class, () -> ShaderWorldTranslation.translate(
            "#version 120\nvoid main() { gl_FragData[0] = vec4(1.0); gl_FragData[1] = vec4(0.0); }\n", fragment, false));
        assertThrows(java.io.IOException.class, () -> ShaderWorldTranslation.translate(
            "#version 120\n#extension GL_ARB_shader_texture_lod : enable\nvoid main() { gl_FragColor = vec4(1.0); }\n", fragment, false));
        assertThrows(java.io.IOException.class, () -> ShaderWorldTranslation.translate(
            "#version 120\nvoid main() { gl_Position = ftransform(); }\n", null, true));
    }

    @Test void normalsAreRefusedRatherThanFakedWhenTheVersionStoppedSupplyingThem() throws Exception {
        String pack = "#version 120\nvarying vec3 n;\nvoid main() { gl_Position = ftransform(); n = gl_Normal; }\n";
        assertTrue(ShaderWorldTranslation.translate(pack, vertexEnvironment(LOCAL_VERTEX), true).contains("n = Normal;"));
        var failure = assertThrows(java.io.IOException.class,
            () -> ShaderWorldTranslation.translate(pack, vertexEnvironment(IMPORTED_VERTEX), true));
        assertTrue(failure.getMessage().contains("normals"), failure.getMessage());
    }

    @Test void aProgramMentioningAnUnsupportedNameOnlyInACommentIsStillAccepted() throws Exception {
        String pack = "#version 120\n// mc_Entity is described here but never read\nvoid main() { gl_Position = ftransform(); }\n";
        assertNotNull(ShaderWorldTranslation.translate(pack, vertexEnvironment(LOCAL_VERTEX), true));
    }
}
