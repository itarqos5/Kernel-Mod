package dev.kernel.fabric.shader.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Public shader project discovery and exact-game-version file selection. Does not install mods or dependencies. */
public final class ModrinthShaders {
    @FunctionalInterface public interface JsonTransport { byte[] get(URI uri) throws IOException; }
    public record Project(String id, String slug, String title, String description, long downloads) {}
    public record Search(List<Project> projects, int offset, int total) {}
    public record Download(String projectId, String versionId, String filename, URI url, long bytes, String sha512, Instant published, boolean release) {}
    private final JsonTransport transport;
    public ModrinthShaders(JsonTransport transport) { this.transport = transport; }

    public Search search(String query, String gameVersion, int offset) throws IOException {
        if (offset < 0 || offset > 10000 || query.length() > 200) throw new IOException("Invalid shader search");
        var facets = new JsonArray();
        var type = new JsonArray(); type.add("project_type:shader"); facets.add(type);
        var version = new JsonArray(); version.add("versions:" + gameVersion); facets.add(version);
        URI uri = URI.create("https://api.modrinth.com/v2/search?limit=12&index=relevance&offset=" + offset + "&query=" + encode(query) + "&facets=" + encode(facets.toString()));
        try {
            JsonObject json = parse(transport.get(uri)).getAsJsonObject();
            var projects = new ArrayList<Project>();
            for (JsonElement element : json.getAsJsonArray("hits")) {
                JsonObject hit = element.getAsJsonObject();
                if (!"shader".equals(text(hit, "project_type"))) continue;
                String id = text(hit, "project_id");
                if (!id.matches("[A-Za-z0-9]{1,64}")) continue;
                projects.add(new Project(id, text(hit, "slug"), text(hit, "title"), text(hit, "description"), hit.get("downloads").getAsLong()));
            }
            return new Search(List.copyOf(projects), offset, Math.max(0, json.get("total_hits").getAsInt()));
        } catch (RuntimeException exception) { throw new IOException("Invalid Modrinth search response", exception); }
    }

    public Download latest(String projectId, String gameVersion) throws IOException {
        if (!projectId.matches("[A-Za-z0-9]{1,64}")) throw new IOException("Invalid Modrinth project ID");
        var versions = new JsonArray(); versions.add(gameVersion);
        URI uri = URI.create("https://api.modrinth.com/v2/project/" + projectId + "/version?include_changelog=false&game_versions=" + encode(versions.toString()));
        try {
            var choices = new ArrayList<Download>();
            for (JsonElement element : parse(transport.get(uri)).getAsJsonArray()) {
                JsonObject version = element.getAsJsonObject();
                if (!projectId.equals(text(version, "project_id")) || !contains(version.getAsJsonArray("game_versions"), gameVersion)) continue;
                String status = text(version, "status");
                if (!status.equals("listed") && !status.equals("archived")) continue;
                JsonObject selected = null;
                for (JsonElement fileElement : version.getAsJsonArray("files")) {
                    var file = fileElement.getAsJsonObject();
                    if (!text(file, "filename").toLowerCase(Locale.ROOT).endsWith(".zip")) continue;
                    if (selected == null || file.get("primary").getAsBoolean()) selected = file;
                    if (file.get("primary").getAsBoolean()) break;
                }
                if (selected == null) continue;
                String filename = text(selected, "filename");
                if (filename.length() > 180 || filename.startsWith(".") || filename.indexOf('/') >= 0 || filename.indexOf('\\') >= 0
                    || filename.indexOf(':') >= 0 || filename.chars().anyMatch(Character::isISOControl)) throw new IOException("Invalid shader download filename");
                URI url = URI.create(text(selected, "url"));
                if (!"https".equals(url.getScheme()) || !"cdn.modrinth.com".equals(url.getHost()) || url.getUserInfo() != null
                    || (url.getPort() != -1 && url.getPort() != 443) || !url.getPath().startsWith("/data/")) throw new IOException("Invalid shader download URL");
                String hash = text(selected.getAsJsonObject("hashes"), "sha512").toLowerCase(Locale.ROOT);
                if (!hash.matches("[0-9a-f]{128}")) throw new IOException("Missing shader SHA-512 integrity hash");
                long size = selected.get("size").getAsLong();
                if (size <= 0 || size > ShaderPackArchive.MAX_ARCHIVE_BYTES) continue;
                choices.add(new Download(projectId, text(version, "id"), filename, url, size, hash, Instant.parse(text(version, "date_published")), "release".equals(text(version, "version_type"))));
            }
            return choices.stream().max(Comparator.comparing(Download::release).thenComparing(Download::published))
                .orElseThrow(() -> new IOException("No ZIP shader release is listed for Minecraft " + gameVersion));
        } catch (RuntimeException exception) { throw new IOException("Invalid Modrinth version response", exception); }
    }
    private static JsonElement parse(byte[] bytes) { return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)); }
    private static String text(JsonObject object, String key) { return object.get(key).getAsString(); }
    private static boolean contains(JsonArray array, String value) {
        for (var element : array) if (value.equals(element.getAsString())) return true;
        return false;
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
