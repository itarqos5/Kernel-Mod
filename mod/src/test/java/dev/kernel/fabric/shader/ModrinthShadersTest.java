package dev.kernel.fabric.shader;

import dev.kernel.fabric.shader.pack.ModrinthShaders;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;

class ModrinthShadersTest {
    @Test void searchesAreVersionFilteredAndReturnOnlyShaderProjects() throws Exception {
        var requested = new AtomicReference<URI>();
        var api = new ModrinthShaders(uri -> {
            requested.set(uri);
            return """
                {"total_hits":2,"hits":[
                {"project_type":"shader","project_id":"Abcd1234","slug":"example","title":"Original shader","description":"Test","downloads":10},
                {"project_type":"mod","project_id":"Mod1234"}]}
                """.getBytes(StandardCharsets.UTF_8);
        });
        assertEquals(1, api.search("white & gray", "26.2", 12).projects().size());
        String query = java.net.URLDecoder.decode(requested.get().getRawQuery(), StandardCharsets.UTF_8);
        assertTrue(query.contains("versions:26.2")); assertTrue(query.contains("project_type:shader")); assertTrue(query.contains("offset=12"));
    }
    @Test void picksExactVersionStableZipAndRefusesForeignDownloadHosts() throws Exception {
        String template = """
            {"project_id":"Abcd1234","id":"%s","game_versions":["%s"],"status":"listed","version_type":"%s","date_published":"%s",
             "files":[{"primary":true,"filename":"original.zip","url":"https://cdn.modrinth.com/data/Abcd1234/original.zip","size":42,"hashes":{"sha512":"%s"}}]}
            """;
        String hash = "a".repeat(128);
        String response = "[" + template.formatted("stable", "26.2", "release", "2026-08-01T00:00:00Z", hash) + ","
            + template.formatted("beta", "26.2", "beta", "2026-09-01T00:00:00Z", hash) + ","
            + template.formatted("wrong", "1.21.4", "release", "2026-09-02T00:00:00Z", hash) + "]";
        var api = new ModrinthShaders(uri -> response.getBytes(StandardCharsets.UTF_8));
        assertEquals("stable", api.latest("Abcd1234", "26.2").versionId());
        var foreign = new ModrinthShaders(uri -> response.replace("cdn.modrinth.com", "example.com").getBytes(StandardCharsets.UTF_8));
        assertThrows(java.io.IOException.class, () -> foreign.latest("Abcd1234", "26.2"));
        assertThrows(java.io.IOException.class, () -> api.latest("../bad", "26.2"));
    }
}
