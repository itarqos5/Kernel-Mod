package dev.kernel.fabric.shader.pack;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Set;
import java.util.function.LongConsumer;

/** Anonymous, bounded HTTPS access to Modrinth. Intended for a background worker, never the render thread. */
public final class ShaderHttp {
    private static final Set<String> HOSTS = Set.of("api.modrinth.com", "cdn.modrinth.com");
    private final String userAgent;
    public ShaderHttp(String version) { userAgent = "itarqos5/Kernel-Mod/" + version + " (https://github.com/itarqos5/Kernel-Mod)"; }
    public byte[] json(URI uri) throws IOException {
        try (var response = open(uri)) {
            var output = new ByteArrayOutputStream();
            transfer(response.input, output, 2 * 1024 * 1024, null, ignored -> {});
            return output.toByteArray();
        }
    }
    public long download(URI uri, Path temporary, long expectedBytes, MessageDigest digest, LongConsumer progress) throws IOException {
        if (!"cdn.modrinth.com".equals(uri.getHost())) throw new IOException("Shader download is not hosted on the Modrinth CDN");
        if (expectedBytes <= 0 || expectedBytes > ShaderPackArchive.MAX_ARCHIVE_BYTES) throw new IOException("Shader download has an invalid size");
        try (var response = open(uri); var output = Files.newOutputStream(temporary)) {
            if (response.contentLength > 0 && response.contentLength != expectedBytes) throw new IOException("Shader download size differs from Modrinth metadata");
            long received = transfer(response.input, output, expectedBytes, digest, progress);
            if (received != expectedBytes) throw new IOException("Incomplete shader download");
            return received;
        }
    }
    private Response open(URI uri) throws IOException {
        for (int redirect = 0; redirect <= 3; redirect++) {
            checkInterrupted(); validate(uri);
            var connection = (HttpsURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(15_000); connection.setReadTimeout(20_000);
            connection.setInstanceFollowRedirects(false); connection.setRequestProperty("User-Agent", userAgent);
            connection.setRequestProperty("Accept", "application/json, application/zip;q=0.9, application/octet-stream;q=0.8");
            try {
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) {
                    String destination = connection.getHeaderField("Location");
                    if (destination == null) throw new IOException("Missing Modrinth redirect location");
                    URI next = uri.resolve(destination);
                    validate(next);
                    if (!next.getHost().equals(uri.getHost())) throw new IOException("Cross-host Modrinth redirect refused");
                    uri = next; connection.disconnect(); continue;
                }
                if (status == 429) throw new IOException("Modrinth is rate limiting requests. Try again later.");
                if (status != 200) throw new IOException("Modrinth returned HTTP " + status);
                return new Response(connection, connection.getInputStream(), connection.getContentLengthLong());
            } catch (IOException | RuntimeException failure) { connection.disconnect(); throw failure; }
        }
        throw new IOException("Too many Modrinth redirects");
    }
    private static long transfer(InputStream input, java.io.OutputStream output, long maximum, MessageDigest digest, LongConsumer progress) throws IOException {
        long count = 0; byte[] buffer = new byte[32 * 1024];
        long deadline = System.nanoTime() + 300_000_000_000L;
        for (int read; (read = input.read(buffer)) != -1;) {
            checkInterrupted();
            if (System.nanoTime() - deadline >= 0) throw new IOException("Modrinth transfer timed out after five minutes");
            if (count > maximum - read) throw new IOException("Modrinth response exceeds the expected size");
            output.write(buffer, 0, read); if (digest != null) digest.update(buffer, 0, read);
            count += read; progress.accept(count);
        }
        return count;
    }
    private static void validate(URI uri) throws IOException {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || !HOSTS.contains(uri.getHost())
            || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) throw new IOException("Invalid Modrinth HTTPS address");
    }
    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Shader request cancelled");
    }
    private record Response(HttpsURLConnection connection, InputStream input, long contentLength) implements AutoCloseable {
        @Override public void close() throws IOException { try { input.close(); } finally { connection.disconnect(); } }
    }
}
