package dev.kernel.fabric.shader.pack;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/** Literal pack-local PNG declarations for the currently supported composite/final stages. */
public final class ShaderTextureBindings {
    private ShaderTextureBindings() {}
    private static final Pattern PREPROCESSOR = Pattern.compile("(?m)^[ \\t]*#[ \\t]*(?:if|ifdef|ifndef|elif|else|endif|define|undef)\\b");
    public static Map<String, String> read(String source) throws IOException {
        if (PREPROCESSOR.matcher(source).find()) throw new IOException("Conditional shader properties are not supported yet");
        var declarations = new LinkedHashMap<String, String>();
        var properties = new Properties() {
            @Override public synchronized Object put(Object key, Object value) {
                String previous = declarations.putIfAbsent((String) key, (String) value);
                if (previous != null && !previous.equals(value)) throw new IllegalArgumentException("Conflicting shader property: " + key);
                return previous;
            }
        };
        try { properties.load(new StringReader(source)); }
        catch (IllegalArgumentException malformed) { throw new IOException("Invalid shader properties: " + malformed.getMessage(), malformed); }
        var bindings = new LinkedHashMap<String, String>();
        for (var declaration : declarations.entrySet()) {
            String key = declaration.getKey(), name;
            if (key.startsWith("texture.composite.")) {
                int buffer = ShaderUniforms.colorBuffer(key.substring("texture.composite.".length()));
                if (buffer < 0) throw new IOException("Unsupported composite texture binding: " + key);
                name = "colortex" + buffer;
            } else if (key.equals("texture.noise")) name = "noisetex";
            else if (key.startsWith("customTexture.")) {
                name = key.substring("customTexture.".length());
                if (!name.matches("[A-Za-z_][A-Za-z_0-9]*") || name.startsWith("gl_") || name.startsWith("kernel_")
                    || ShaderUniforms.scalarType(name) >= 0 || ShaderUniforms.colorBuffer(name) >= 0 || ShaderUniforms.isDepthInput(name)
                    || ShaderUniforms.projectionInput(name) != 0)
                    throw new IOException("Custom texture name conflicts with a reserved binding: " + name);
            } else throw new IOException("Unsupported shader property: " + key);
            String path = declaration.getValue().strip();
            if (!path.toLowerCase(java.util.Locale.ROOT).endsWith(".png") || path.startsWith("/") || path.indexOf(':') >= 0 || path.indexOf('\\') >= 0 || path.isBlank())
                throw new IOException("Custom shader textures require pack-local PNG paths: " + path);
            path = ShaderPackArchive.normalize(path);
            String previous = bindings.putIfAbsent(name, path);
            if (previous != null && !previous.equals(path)) throw new IOException("Conflicting texture binding: " + name);
            if (bindings.size() > 32) throw new IOException("Shader packs may declare at most 32 custom texture bindings");
        }
        return Collections.unmodifiableMap(bindings);
    }
}
