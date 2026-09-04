package dev.kernel.fabric.bootstrap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class KnotClientInstallerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void installsAndPatchesRecognizedFabricProfileIdempotently() throws Exception {
        Path minecraftRoot = temporaryDirectory.resolve("minecraft");
        Path assets = minecraftRoot.resolve("assets");
        String versionId = "fabric-loader-0.19.3-1.21.11";
        Path profile = minecraftRoot.resolve("versions").resolve(versionId).resolve(versionId + ".json");
        Files.createDirectories(profile.getParent());
        Files.createDirectories(assets);

        String originalJson = """
            {
              "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
              "libraries": [
                { "name": "net.fabricmc:fabric-loader:0.19.3" }
              ]
            }
            """;
        Files.writeString(profile, originalJson, StandardCharsets.UTF_8);

        byte[] clientJar = "kernel-knot-client-test".getBytes(StandardCharsets.UTF_8);
        String[] launchArguments = {"--assetsDir", assets.toString(), "--version=" + versionId};

        KnotClientInstaller.InstallResult first = KnotClientInstaller.install(launchArguments, clientJar, "0.1.0");
        KnotClientInstaller.InstallResult second = KnotClientInstaller.install(launchArguments, clientJar, "0.1.0");

        assertEquals(KnotClientInstaller.Outcome.INSTALLED, first.outcome());
        assertEquals(KnotClientInstaller.Outcome.ALREADY_INSTALLED, second.outcome());
        assertEquals(originalJson, Files.readString(profile.resolveSibling(profile.getFileName() + ".kernel-backup")));

        JsonObject patched = JsonParser.parseString(Files.readString(profile)).getAsJsonObject();
        assertEquals(KnotClientInstaller.KERNEL_MAIN_CLASS, patched.get("mainClass").getAsString());
        assertEquals(
            KnotClientInstaller.MODERN_FABRIC_MAIN_CLASS,
            patched.getAsJsonObject("kernel").get("originalMainClass").getAsString()
        );

        JsonArray libraries = patched.getAsJsonArray("libraries");
        String coordinate = libraries.get(libraries.size() - 1).getAsJsonObject().get("name").getAsString();
        assertTrue(coordinate.startsWith("dev.kernel.client:kernel-knot-client:0.1.0-k"));

        String installedVersion = coordinate.substring(coordinate.lastIndexOf(':') + 1);
        Path installedJar = minecraftRoot.resolve("libraries/dev/kernel/client/kernel-knot-client")
            .resolve(installedVersion)
            .resolve("kernel-knot-client-" + installedVersion + ".jar");
        assertArrayEquals(clientJar, Files.readAllBytes(installedJar));
    }

    @Test
    void refusesToPatchUnknownLauncherMainClass() throws Exception {
        Path minecraftRoot = temporaryDirectory.resolve("minecraft");
        Path assets = minecraftRoot.resolve("assets");
        String versionId = "unrelated-profile";
        Path profile = minecraftRoot.resolve("versions").resolve(versionId).resolve(versionId + ".json");
        Files.createDirectories(profile.getParent());
        Files.createDirectories(assets);
        Files.writeString(profile, "{\"mainClass\":\"example.NotFabric\",\"libraries\":[]}");

        KnotClientInstaller.InstallResult result = KnotClientInstaller.install(
            new String[]{"--assetsDir", assets.toString(), "--version", versionId},
            new byte[]{1, 2, 3},
            "0.1.0"
        );

        assertEquals(KnotClientInstaller.Outcome.UNSUPPORTED_LAUNCHER, result.outcome());
        assertFalse(Files.exists(profile.resolveSibling(profile.getFileName() + ".kernel-backup")));
        assertFalse(Files.exists(minecraftRoot.resolve("libraries/kernel")));
        assertEquals("example.NotFabric", JsonParser.parseString(Files.readString(profile))
            .getAsJsonObject().get("mainClass").getAsString());
    }

    @Test
    void preservesMalformedThirdPartyLibraryEntries() throws Exception {
        Path minecraftRoot = temporaryDirectory.resolve("minecraft");
        Path assets = minecraftRoot.resolve("assets");
        String versionId = "fabric-loader-test";
        Path profile = minecraftRoot.resolve("versions").resolve(versionId).resolve(versionId + ".json");
        Files.createDirectories(profile.getParent());
        Files.createDirectories(assets);
        Files.writeString(profile, """
            {
              "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
              "libraries": [
                { "name": { "unexpected": true } },
                "unknown-entry"
              ]
            }
            """);

        KnotClientInstaller.InstallResult result = KnotClientInstaller.install(
            new String[]{"--assetsDir", assets.toString(), "--version", versionId},
            new byte[]{1, 2, 3},
            "0.1.0"
        );

        assertEquals(KnotClientInstaller.Outcome.INSTALLED, result.outcome());
        JsonArray libraries = JsonParser.parseString(Files.readString(profile))
            .getAsJsonObject().getAsJsonArray("libraries");
        assertTrue(libraries.get(0).isJsonObject());
        assertEquals("unknown-entry", libraries.get(1).getAsString());
        assertTrue(libraries.get(2).getAsJsonObject().get("name").getAsString()
            .startsWith("dev.kernel.client:kernel-knot-client:0.1.0-k"));
    }

    @Test
    void migratesThePreviousKernelNamespace() throws Exception {
        Path minecraftRoot = temporaryDirectory.resolve("minecraft");
        Path assets = minecraftRoot.resolve("assets");
        String versionId = "fabric-loader-migration-test";
        Path profile = minecraftRoot.resolve("versions").resolve(versionId).resolve(versionId + ".json");
        Files.createDirectories(profile.getParent());
        Files.createDirectories(assets);
        Files.writeString(profile, """
            {
              "mainClass": "kernel.client.KernelKnotClient",
              "libraries": [
                { "name": "kernel.client:kernel-knot-client:0.1.0-kold" }
              ],
              "kernel": {
                "originalMainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient"
              }
            }
            """);

        KnotClientInstaller.InstallResult result = KnotClientInstaller.install(
            new String[]{"--assetsDir", assets.toString(), "--version", versionId},
            new byte[]{1, 2, 3},
            "0.1.0"
        );

        assertEquals(KnotClientInstaller.Outcome.INSTALLED, result.outcome());
        JsonObject patched = JsonParser.parseString(Files.readString(profile)).getAsJsonObject();
        assertEquals(KnotClientInstaller.KERNEL_MAIN_CLASS, patched.get("mainClass").getAsString());
        JsonArray libraries = patched.getAsJsonArray("libraries");
        assertEquals(1, libraries.size());
        assertTrue(libraries.get(0).getAsJsonObject().get("name").getAsString()
            .startsWith("dev.kernel.client:kernel-knot-client:0.1.0-k"));
    }
}
