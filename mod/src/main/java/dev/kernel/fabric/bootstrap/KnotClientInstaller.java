package dev.kernel.fabric.bootstrap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Installs the Kernel Knot Client into an official-launcher-style Fabric profile.
 *
 * <p>Unknown launcher layouts and non-Fabric main classes are left untouched.</p>
 */
public final class KnotClientInstaller {
    static final String KERNEL_MAIN_CLASS = "dev.kernel.client.KernelKnotClient";
    static final String LEGACY_KERNEL_MAIN_CLASS = "kernel.client.KernelKnotClient";
    static final String MODERN_FABRIC_MAIN_CLASS = "net.fabricmc.loader.impl.launch.knot.KnotClient";
    static final String LEGACY_FABRIC_MAIN_CLASS = "net.fabricmc.loader.launch.knot.KnotClient";

    private static final String BOOTSTRAP_JAR_RESOURCE = "/kernel/bootstrap/kernel-knot-client.jar";
    private static final String BOOTSTRAP_PROPERTIES_RESOURCE = "/kernel-bootstrap.properties";
    private static final String LIBRARY_PREFIX = "dev.kernel.client:kernel-knot-client:";
    private static final String LEGACY_LIBRARY_PREFIX = "kernel.client:kernel-knot-client:";
    static final String AGENT_PREFIX = "-javaagent:${library_directory}/dev/kernel/client/kernel-knot-client/";
    private static final Pattern OWNED_AGENT = Pattern.compile(Pattern.quote(AGENT_PREFIX)
        + "([A-Za-z0-9._+\\-]+)/kernel-knot-client-\\1\\.jar");
    private static final Pattern SAFE_VERSION_ID = Pattern.compile("[A-Za-z0-9._+\\-]+", Pattern.UNICODE_CASE);
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private KnotClientInstaller() {
    }

    public static InstallResult installForCurrentLaunch() throws IOException {
        byte[] knotClient = readRequiredResource(BOOTSTRAP_JAR_RESOURCE);
        Properties properties = loadBootstrapProperties();
        String version = properties.getProperty("version");

        if (version == null || version.isBlank()) {
            throw new IOException("Kernel Knot Client version is missing from " + BOOTSTRAP_PROPERTIES_RESOURCE);
        }

        return install(FabricLoader.getInstance().getLaunchArguments(false), knotClient, version);
    }

    static InstallResult install(String[] launchArguments, byte[] knotClient, String semanticVersion) throws IOException {
        LaunchProfile profile;

        try {
            profile = locateLaunchProfile(launchArguments);
        } catch (UnsupportedLauncherException exception) {
            return new InstallResult(Outcome.UNSUPPORTED_LAUNCHER, exception.getMessage());
        }

        JsonObject profileJson = readJson(profile.profileJson());
        String currentMainClass = getRequiredString(profileJson, "mainClass");

        if (!isFabricMainClass(currentMainClass) && !isKernelMainClass(currentMainClass)) {
            return new InstallResult(
                Outcome.UNSUPPORTED_LAUNCHER,
                "the selected profile does not use a recognized Fabric KnotClient entry point"
            );
        }
        if (!hasSupportedJvmArguments(profileJson)) {
            return new InstallResult(Outcome.UNSUPPORTED_LAUNCHER, "the profile has an unsupported JVM arguments structure");
        }

        String hash = sha256(knotClient);
        String installedVersion = semanticVersion + "-k" + hash.substring(0, 12);
        String coordinate = LIBRARY_PREFIX + installedVersion;
        Path installedJar = profile.minecraftRoot()
            .resolve("libraries")
            .resolve("dev/kernel/client/kernel-knot-client")
            .resolve(installedVersion)
            .resolve("kernel-knot-client-" + installedVersion + ".jar");

        boolean jarChanged = installJar(installedJar, knotClient, hash);
        String agentArgument = AGENT_PREFIX + installedVersion + "/kernel-knot-client-" + installedVersion + ".jar";
        boolean profileChanged = patchProfile(profileJson, currentMainClass, coordinate, semanticVersion, hash, agentArgument);

        if (profileChanged) {
            backupOnce(profile.profileJson());
            writeJsonAtomically(profile.profileJson(), profileJson);
        }

        Outcome outcome = jarChanged || profileChanged ? Outcome.INSTALLED : Outcome.ALREADY_INSTALLED;
        return new InstallResult(outcome, profile.profileJson().toString());
    }

    private static LaunchProfile locateLaunchProfile(String[] arguments) throws UnsupportedLauncherException {
        String assetsDirectory = findArgument(arguments, "--assetsDir");
        String versionId = findArgument(arguments, "--version");

        if (assetsDirectory == null || versionId == null) {
            throw new UnsupportedLauncherException("the launch arguments do not identify an assets directory and version");
        }
        if (!SAFE_VERSION_ID.matcher(versionId).matches()) {
            throw new UnsupportedLauncherException("the launcher supplied an unsafe version identifier");
        }

        Path assets;

        try {
            assets = Path.of(assetsDirectory).toAbsolutePath().normalize();
        } catch (InvalidPathException exception) {
            throw new UnsupportedLauncherException("the launcher supplied an invalid assets directory");
        }
        Path minecraftRoot = assets.getParent();

        if (minecraftRoot == null) {
            throw new UnsupportedLauncherException("the assets directory has no Minecraft root directory");
        }

        Path versionsRoot = minecraftRoot.resolve("versions").normalize();
        Path profile = versionsRoot.resolve(versionId).resolve(versionId + ".json").normalize();

        if (!profile.startsWith(versionsRoot) || !Files.isRegularFile(profile)) {
            throw new UnsupportedLauncherException("no official-launcher-style version profile was found");
        }

        return new LaunchProfile(minecraftRoot, profile);
    }

    private static String findArgument(String[] arguments, String key) {
        String prefix = key + "=";

        for (int index = 0; index < arguments.length; index++) {
            String argument = arguments[index];
            if (argument.startsWith(prefix)) {
                return argument.substring(prefix.length());
            }
            if (argument.equals(key) && index + 1 < arguments.length) {
                return arguments[index + 1];
            }
        }

        return null;
    }

    private static boolean patchProfile(
        JsonObject profile,
        String currentMainClass,
        String coordinate,
        String semanticVersion,
        String hash,
        String agentArgument
    ) {
        boolean changed = false;
        String originalMainClass = currentMainClass;

        JsonObject kernelMetadata = profile.has("kernel") && profile.get("kernel").isJsonObject()
            ? profile.getAsJsonObject("kernel")
            : new JsonObject();

        if (isKernelMainClass(currentMainClass)) {
            JsonElement storedOriginal = kernelMetadata.get("originalMainClass");
            if (storedOriginal != null && storedOriginal.isJsonPrimitive()) {
                originalMainClass = storedOriginal.getAsString();
            } else {
                originalMainClass = MODERN_FABRIC_MAIN_CLASS;
            }
            if (!KERNEL_MAIN_CLASS.equals(currentMainClass)) {
                profile.addProperty("mainClass", KERNEL_MAIN_CLASS);
                changed = true;
            }
        } else {
            profile.addProperty("mainClass", KERNEL_MAIN_CLASS);
            changed = true;
        }

        JsonArray libraries = profile.has("libraries") && profile.get("libraries").isJsonArray()
            ? profile.getAsJsonArray("libraries")
            : new JsonArray();
        JsonArray updatedLibraries = new JsonArray();
        int matchingLibraries = 0;
        boolean expectedLibraryPresent = false;

        for (JsonElement library : libraries) {
            JsonElement nameElement = library.isJsonObject() ? library.getAsJsonObject().get("name") : null;
            String name = nameElement != null && nameElement.isJsonPrimitive()
                && nameElement.getAsJsonPrimitive().isString()
                ? nameElement.getAsString()
                : null;

            if (name != null && (name.startsWith(LIBRARY_PREFIX) || name.startsWith(LEGACY_LIBRARY_PREFIX))) {
                matchingLibraries++;
                expectedLibraryPresent |= coordinate.equals(name);
            } else {
                updatedLibraries.add(library);
            }
        }

        if (matchingLibraries != 1 || !expectedLibraryPresent) {
            JsonObject library = new JsonObject();
            library.addProperty("name", coordinate);
            updatedLibraries.add(library);
            profile.add("libraries", updatedLibraries);
            changed = true;
        }

        changed |= setString(kernelMetadata, "originalMainClass", originalMainClass);
        changed |= setString(kernelMetadata, "knotClientVersion", semanticVersion);
        changed |= setString(kernelMetadata, "knotClientSha256", hash);
        changed |= patchAgentArgument(profile, agentArgument);
        changed |= setString(kernelMetadata, "javaAgentArgument", agentArgument);

        if (!profile.has("kernel") || profile.get("kernel") != kernelMetadata) {
            profile.add("kernel", kernelMetadata);
            changed = true;
        }

        return changed;
    }

    private static boolean hasSupportedJvmArguments(JsonObject profile) {
        if (!profile.has("arguments")) return true;
        if (!profile.get("arguments").isJsonObject()) return false;
        JsonObject arguments = profile.getAsJsonObject("arguments");
        return !arguments.has("jvm") || arguments.get("jvm").isJsonArray();
    }

    private static boolean patchAgentArgument(JsonObject profile, String agentArgument) {
        JsonObject arguments = profile.has("arguments") ? profile.getAsJsonObject("arguments") : new JsonObject();
        JsonArray original = arguments.has("jvm") ? arguments.getAsJsonArray("jvm") : new JsonArray();
        JsonArray updated = new JsonArray();
        for (JsonElement argument : original) {
            if (argument.isJsonPrimitive() && argument.getAsJsonPrimitive().isString()
                && OWNED_AGENT.matcher(argument.getAsString()).matches()) continue;
            // Keep third-party agents, conditional launcher arguments, and game arguments verbatim.
            updated.add(argument);
        }
        updated.add(agentArgument);
        if (original.equals(updated)) return false;
        arguments.add("jvm", updated);
        profile.add("arguments", arguments);
        return true;
    }

    private static boolean setString(JsonObject object, String key, String value) {
        JsonElement existing = object.get(key);
        if (existing != null && existing.isJsonPrimitive() && value.equals(existing.getAsString())) {
            return false;
        }

        object.addProperty(key, value);
        return true;
    }

    private static boolean installJar(Path destination, byte[] contents, String expectedHash) throws IOException {
        if (Files.isRegularFile(destination) && expectedHash.equals(sha256(Files.readAllBytes(destination)))) {
            return false;
        }

        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), "kernel-knot-client-", ".tmp");

        try {
            Files.write(temporary, contents);
            moveAtomically(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }

        return true;
    }

    private static void backupOnce(Path profile) throws IOException {
        Path backup = profile.resolveSibling(profile.getFileName() + ".kernel-backup");
        if (!Files.exists(backup)) {
            Files.copy(profile, backup, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private static void writeJsonAtomically(Path destination, JsonObject json) throws IOException {
        Path temporary = Files.createTempFile(destination.getParent(), destination.getFileName().toString(), ".tmp");

        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
            moveAtomically(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static JsonObject readJson(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) {
                throw new IOException("Launcher profile is not a JSON object: " + path);
            }
            return element.getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IOException("Could not parse launcher profile: " + path, exception);
        }
    }

    private static String getRequiredString(JsonObject object, String key) throws IOException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IOException("Launcher profile is missing its " + key);
        }
        return value.getAsString();
    }

    private static boolean isFabricMainClass(String className) {
        return MODERN_FABRIC_MAIN_CLASS.equals(className) || LEGACY_FABRIC_MAIN_CLASS.equals(className);
    }

    private static boolean isKernelMainClass(String className) {
        return KERNEL_MAIN_CLASS.equals(className) || LEGACY_KERNEL_MAIN_CLASS.equals(className);
    }

    private static Properties loadBootstrapProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = KnotClientInstaller.class.getResourceAsStream(BOOTSTRAP_PROPERTIES_RESOURCE)) {
            if (input == null) {
                throw new IOException("Missing bundled resource " + BOOTSTRAP_PROPERTIES_RESOURCE);
            }
            properties.load(input);
        }
        return properties;
    }

    private static byte[] readRequiredResource(String name) throws IOException {
        try (InputStream input = KnotClientInstaller.class.getResourceAsStream(name)) {
            if (input == null) {
                throw new IOException("Missing bundled resource " + name);
            }
            return input.readAllBytes();
        }
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("This Java runtime does not provide SHA-256", exception);
        }
    }

    public enum Outcome {
        INSTALLED,
        ALREADY_INSTALLED,
        UNSUPPORTED_LAUNCHER
    }

    public record InstallResult(Outcome outcome, String detail) {
    }

    private record LaunchProfile(Path minecraftRoot, Path profileJson) {
    }

    private static final class UnsupportedLauncherException extends Exception {
        private UnsupportedLauncherException(String message) {
            super(message);
        }
    }
}
