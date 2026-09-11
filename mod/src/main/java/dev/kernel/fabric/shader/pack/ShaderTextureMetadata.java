package dev.kernel.fabric.shader.pack;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;

/** Bounded, shallow texture metadata. Unknown settings are not silently discarded. */
public record ShaderTextureMetadata(boolean blur, boolean clamp) {
    public static ShaderTextureMetadata read(String source) throws IOException {
        if (source.length() > 65536) throw new IOException("Texture metadata exceeds 64 KiB");
        boolean blur = false, clamp = false, textureSeen = false;
        try (var reader = new JsonReader(new StringReader(source))) {
            reader.setLenient(false);
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (!key.equals("texture") || textureSeen) throw new IOException("Unsupported or duplicate texture metadata field: " + key);
                textureSeen = true;
                reader.beginObject();
                var fields = new HashSet<String>();
                while (reader.hasNext()) {
                    String field = reader.nextName();
                    if (!fields.add(field) || !field.equals("blur") && !field.equals("clamp"))
                        throw new IOException("Unsupported or duplicate texture option: " + field);
                    if (reader.peek() != JsonToken.BOOLEAN) throw new IOException("Texture options require JSON booleans");
                    boolean enabled = reader.nextBoolean();
                    if (field.equals("blur")) blur = enabled; else clamp = enabled;
                }
                reader.endObject();
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Unexpected data after texture metadata");
        } catch (IllegalStateException | NumberFormatException invalid) {
            throw new IOException("Invalid texture metadata", invalid);
        }
        return new ShaderTextureMetadata(blur, clamp);
    }
}
