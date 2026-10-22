package org.example.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Скачивает версию Minecraft: json, client.jar, библиотеки, natives и ассеты. */
public final class MinecraftInstaller {
    public interface Progress { void update(double fraction, String text); }

    private record Job(String url, Path target, String sha1) {}

    static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String RESOURCES = "https://resources.download.minecraft.net/";

    private MinecraftInstaller() {}

    public static JsonObject install(String version, Progress progress) throws Exception {
        Path vDir = Dirs.VERSIONS.resolve(version);
        Path vJson = vDir.resolve(version + ".json");

        progress.update(-1, "Получение информации о версии " + version + "...");
        if (!Files.exists(vJson)) {
            JsonObject manifest = JsonParser.parseString(Downloader.getString(MANIFEST)).getAsJsonObject();
            String url = null;
            for (JsonElement e : manifest.getAsJsonArray("versions")) {
                JsonObject o = e.getAsJsonObject();
                if (o.get("id").getAsString().equals(version)) { url = o.get("url").getAsString(); break; }
            }
            if (url == null) throw new IOException("Версия не найдена в манифесте Mojang: " + version);
            Downloader.download(url, vJson, null);
        }
        JsonObject json = JsonParser.parseString(Files.readString(vJson)).getAsJsonObject();
        if (!json.has("arguments")) throw new IOException("Версия " + version + " не поддерживается лаунчером (слишком старая)");

        List<Job> jobs = new ArrayList<>();
        List<Path> nativeJars = new ArrayList<>();

        // 1. client.jar
        JsonObject client = json.getAsJsonObject("downloads").getAsJsonObject("client");
        jobs.add(new Job(client.get("url").getAsString(), vDir.resolve(version + ".jar"), client.get("sha1").getAsString()));

        // 2. библиотеки (+ natives старого формата)
        boolean is64 = System.getProperty("os.arch").contains("64");
        for (JsonElement e : json.getAsJsonArray("libraries")) {
            JsonObject lib = e.getAsJsonObject();
            if (!Rules.allowed(lib.has("rules") ? lib.getAsJsonArray("rules") : null)) continue;
            JsonObject dl = lib.getAsJsonObject("downloads");
            if (dl == null) continue;
            if (dl.has("artifact")) {
                JsonObject art = dl.getAsJsonObject("artifact");
                jobs.add(new Job(art.get("url").getAsString(),
                        Dirs.LIBRARIES.resolve(art.get("path").getAsString()), art.get("sha1").getAsString()));
            }
            if (lib.has("natives") && dl.has("classifiers")) {
                JsonObject natives = lib.getAsJsonObject("natives");
                if (natives.has(Rules.os())) {
                    String classifier = natives.get(Rules.os()).getAsString().replace("${arch}", is64 ? "64" : "32");
                    JsonObject cl = dl.getAsJsonObject("classifiers");
                    if (cl.has(classifier)) {
                        JsonObject art = cl.getAsJsonObject(classifier);
                        Path p = Dirs.LIBRARIES.resolve(art.get("path").getAsString());
                        jobs.add(new Job(art.get("url").getAsString(), p, art.get("sha1").getAsString()));
                        nativeJars.add(p);
                    }
                }
            }
        }

        // 3. ассеты
        JsonObject ai = json.getAsJsonObject("assetIndex");
        Path index = Dirs.ASSETS.resolve("indexes").resolve(ai.get("id").getAsString() + ".json");
        progress.update(-1, "Загрузка индекса ассетов...");
        Downloader.download(ai.get("url").getAsString(), index, ai.get("sha1").getAsString());
        JsonObject objects = JsonParser.parseString(Files.readString(index)).getAsJsonObject().getAsJsonObject("objects");
        for (Map.Entry<String, JsonElement> en : objects.entrySet()) {
            String hash = en.getValue().getAsJsonObject().get("hash").getAsString();
            String prefix = hash.substring(0, 2);
            jobs.add(new Job(RESOURCES + prefix + "/" + hash,
                    Dirs.ASSETS.resolve("objects").resolve(prefix).resolve(hash), null));
        }

        // 4. параллельная загрузка
        int total = jobs.size();
        AtomicInteger done = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(12);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (Job j : jobs) {
                futures.add(pool.submit(() -> {
                    Downloader.download(j.url(), j.target(), j.sha1());
                    int d = done.incrementAndGet();
                    if (d % 25 == 0 || d == total) {
                        progress.update((double) d / total, "Загрузка файлов: " + d + " / " + total);
                    }
                    return null;
                }));
            }
            for (Future<Void> f : futures) f.get();
        } finally {
            pool.shutdownNow();
        }

        // 5. natives старого формата — распаковываем
        extractNatives(nativeJars, vDir.resolve("natives"));
        progress.update(1, "Файлы готовы");
        return json;
    }

    private static void extractNatives(List<Path> jars, Path dir) throws IOException {
        Path base = dir.toAbsolutePath().normalize();
        Files.createDirectories(base);
        for (Path jar : jars) {
            try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(jar))) {
                ZipEntry en;
                while ((en = zin.getNextEntry()) != null) {
                    String name = en.getName();
                    if (en.isDirectory() || name.startsWith("META-INF")) continue;
                    Path out = base.resolve(name).normalize();
                    if (!out.startsWith(base)) continue;
                    Files.createDirectories(out.getParent());
                    Files.copy(zin, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /** Пути ко всем библиотекам для classpath. */
    public static List<Path> libraryPaths(JsonObject json) {
        List<Path> list = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray("libraries")) {
            JsonObject lib = e.getAsJsonObject();
            if (!Rules.allowed(lib.has("rules") ? lib.getAsJsonArray("rules") : null)) continue;
            JsonObject dl = lib.getAsJsonObject("downloads");
            if (dl == null || !dl.has("artifact")) continue;
            list.add(Dirs.LIBRARIES.resolve(dl.getAsJsonObject("artifact").get("path").getAsString()));
        }
        return list;
    }
}
