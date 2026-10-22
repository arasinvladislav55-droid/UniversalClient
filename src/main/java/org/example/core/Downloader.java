package org.example.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

public final class Downloader {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private Downloader() {}

    public static String getString(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).build();
        HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode() + " : " + url);
        return r.body();
    }

    /** Скачивает файл, если его нет или не совпадает SHA-1. 3 попытки. */
    public static void download(String url, Path target, String sha1) throws IOException, InterruptedException {
        if (Files.exists(target) && (sha1 == null || sha1(target).equalsIgnoreCase(sha1))) return;
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        IOException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2)).build();
                HttpResponse<InputStream> r = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode() + " : " + url);
                try (InputStream in = r.body()) {
                    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                }
                if (sha1 != null && !sha1(tmp).equalsIgnoreCase(sha1)) throw new IOException("SHA-1 mismatch: " + url);
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException e) {
                last = e;
                Thread.sleep(400L * attempt);
            }
        }
        throw last;
    }

    public static String sha1(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
