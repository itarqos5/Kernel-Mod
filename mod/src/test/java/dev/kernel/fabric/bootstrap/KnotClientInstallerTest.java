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
        assertEquals(KnotClientInstaller.AGENT_PREFIX + installedVersion + "/kernel-knot-client-" + installedVersion + ".jar",
            patched.getAsJsonObject("arguments").getAsJsonArray("jvm").get(0).getAsString());
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

    @Test
    void updatesOnlyKernelAgentArgumentAndPreservesBackupAcrossUpdates() throws Exception {
        String oldAgent = KnotClientInstaller.AGENT_PREFIX + "0.1.0-kold/kernel-knot-client-0.1.0-kold.jar";
        JsonObject original = JsonParser.parseString("""
            {
              "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
              "arguments": {
                "jvm": ["-Xmx4G", "-javaagent:other-agent.jar", {"rules":[{"action":"allow"}],"value":"-Dtest=true"}],
                "game": ["--demo"]
              }
            }
            """).getAsJsonObject();
        original.getAsJsonObject("arguments").getAsJsonArray("jvm").add(oldAgent);
        Path root = this.temporaryDirectory.resolve("minecraft with spaces");
        Path profile = root.resolve("versions/fabric-test/fabric-test.json");
        Files.createDirectories(profile.getParent());
        String originalText = original.toString();
        Files.writeString(profile, originalText);
        String[] arguments = {"--assetsDir", root.resolve("assets").toString(), "--version", "fabric-test"};

        KnotClientInstaller.install(arguments, new byte[]{1, 2}, "0.1.0");
        String first = Files.readString(profile);
        KnotClientInstaller.install(arguments, new byte[]{3, 4}, "0.1.0");
        JsonObject updated = JsonParser.parseString(Files.readString(profile)).getAsJsonObject();
        JsonArray jvm = updated.getAsJsonObject("arguments").getAsJsonArray("jvm");
        assertEquals(4, jvm.size());
        assertEquals(original.getAsJsonObject("arguments").getAsJsonArray("jvm").get(0), jvm.get(0));
        assertEquals("-javaagent:other-agent.jar", jvm.get(1).getAsString());
        assertEquals(original.getAsJsonObject("arguments").getAsJsonArray("jvm").get(2), jvm.get(2));
        assertTrue(jvm.get(3).getAsString().startsWith(KnotClientInstaller.AGENT_PREFIX));
        assertFalse(jvm.get(3).getAsString().equals(oldAgent));
        assertEquals(original.getAsJsonObject("arguments").get("game"), updated.getAsJsonObject("arguments").get("game"));
        assertFalse(first.equals(Files.readString(profile)));
        assertEquals(originalText, Files.readString(profile.resolveSibling("fabric-test.json.kernel-backup")));
        assertEquals(KnotClientInstaller.Outcome.ALREADY_INSTALLED,
            KnotClientInstaller.install(arguments, new byte[]{3, 4}, "0.1.0").outcome());
    }

    @Test
    void refusesMalformedJvmArgumentStructuresWithoutWritingAnything() throws Exception {
        int index = 0;
        for (String argumentsJson : new String[]{"null", "[]", "{\"jvm\":null}", "{\"jvm\":\"-Xmx4G\"}"}) {
            Path root = this.temporaryDirectory.resolve("invalid-" + index++);
            Path profile = root.resolve("versions/fabric-test/fabric-test.json");
            Files.createDirectories(profile.getParent());
            String original = "{\"mainClass\":\"net.fabricmc.loader.impl.launch.knot.KnotClient\",\"arguments\":" + argumentsJson + "}";
            Files.writeString(profile, original);
            var result = KnotClientInstaller.install(new String[]{"--assetsDir", root.resolve("assets").toString(),
                "--version", "fabric-test"}, new byte[]{1}, "0.1.0");
            assertEquals(KnotClientInstaller.Outcome.UNSUPPORTED_LAUNCHER, result.outcome());
            assertEquals(original, Files.readString(profile));
            assertFalse(Files.exists(root.resolve("libraries")));
            assertFalse(Files.exists(profile.resolveSibling("fabric-test.json.kernel-backup")));
        }
    }
}
