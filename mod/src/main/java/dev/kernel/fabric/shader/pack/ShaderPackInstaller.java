package dev.kernel.fabric.shader.pack;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.function.LongConsumer;

/** Validates a staged ZIP before making it visible. Existing packs and import sources are never overwritten. */
public final class ShaderPackInstaller {
    private final Path directory;
    @FunctionalInterface public interface Downloader {
        long download(java.net.URI uri, Path temporary, long expectedBytes, MessageDigest digest, LongConsumer progress) throws IOException;
    }
    private final Downloader downloader;
    public ShaderPackInstaller(Path directory, ShaderHttp http) {
        this(directory, http::download);
    }
    public ShaderPackInstaller(Path directory, Downloader downloader) {
        this.directory = directory.toAbsolutePath().normalize(); this.downloader = downloader;
    }
    public Path download(ModrinthShaders.Download download, LongConsumer progress) throws IOException {
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".kernel-download-", ".part");
        try {
            var digest = digest();
            downloader.download(download.url(), temporary, download.bytes(), digest, progress);
            String hash = HexFormat.of().formatHex(digest.digest());
            if (!MessageDigest.isEqual(HexFormat.of().parseHex(hash), HexFormat.of().parseHex(download.sha512()))) throw new IOException("Shader download failed its SHA-512 integrity check");
            return commit(temporary, download.filename(), hash);
        } finally { Files.deleteIfExists(temporary); }
    }
    public Path importZip(Path source, LongConsumer progress) throws IOException {
        if (!Files.isRegularFile(source) || !source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) throw new IOException("Drop a shader pack ZIP file");
        if (Files.size(source) > ShaderPackArchive.MAX_ARCHIVE_BYTES) throw new IOException("Shader ZIP exceeds 256 MiB");
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".kernel-import-", ".part");
        try {
            var digest = digest(); byte[] buffer = new byte[32 * 1024]; long count = 0;
            try (var input = Files.newInputStream(source); var output = Files.newOutputStream(temporary)) {
                for (int read; (read = input.read(buffer)) != -1;) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Shader import cancelled");
                    if (count > ShaderPackArchive.MAX_ARCHIVE_BYTES - read) throw new IOException("Shader ZIP exceeds 256 MiB");
                    output.write(buffer, 0, read); digest.update(buffer, 0, read); count += read; progress.accept(count);
                }
            }
            return commit(temporary, source.getFileName().toString(), HexFormat.of().formatHex(digest.digest()));
        } finally { Files.deleteIfExists(temporary); }
    }
    private Path commit(Path temporary, String originalName, String hash) throws IOException {
        try (var pack = new ShaderPackArchive(temporary)) { pack.files(); }
        String name = safeName(originalName);
        for (int suffix = 0; suffix < 1000; suffix++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Shader installation cancelled");
            String candidate = suffix == 0 ? name : name.substring(0, name.length() - 4) + "-" + hash.substring(0, 12) + (suffix == 1 ? "" : "-" + suffix) + ".zip";
            Path target = directory.resolve(candidate).normalize();
            if (!target.getParent().equals(directory)) throw new IOException("Invalid shader destination");
            if (Files.exists(target)) {
                if (Files.isRegularFile(target) && hash.equals(hash(target))) return target;
                continue;
            }
            try {
                // Same-directory move without REPLACE_EXISTING keeps an independently installed pack intact.
                return Files.move(temporary, target);
            } catch (java.nio.file.FileAlreadyExistsException raced) { /* Another installer won this name; choose a new one. */ }
        }
        throw new IOException("Too many conflicting shader pack filenames");
    }
    private static String safeName(String original) {
        String name = original.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) name = name.substring(0, name.length() - 4);
        name = name.replaceAll("^[. ]+|[. ]+$", "");
        if (name.isEmpty()) name = "shaderpack";
        if (name.length() > 120) name = name.substring(0, 120);
        if (name.toUpperCase(Locale.ROOT).matches("(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?")) name = "pack-" + name;
        return name + ".zip";
    }
    private static String hash(Path path) throws IOException {
        if (Files.size(path) > ShaderPackArchive.MAX_ARCHIVE_BYTES) return "";
        var digest = digest(); byte[] buffer = new byte[32 * 1024]; long count = 0;
        try (var input = Files.newInputStream(path)) {
            for (int read; (read = input.read(buffer)) != -1;) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Shader import cancelled");
                if (count > ShaderPackArchive.MAX_ARCHIVE_BYTES - read) return "";
                digest.update(buffer, 0, read); count += read;
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-512"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
