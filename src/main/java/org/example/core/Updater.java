package org.example.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

/**
 * Автообновление лаунчера.
 *
 * Лаунчер скачивает маленький файл version.json и сравнивает версию с Config.LAUNCHER_VERSION.
 * Если на сервере версия новее — на главной вместо «ИГРАТЬ» появляется «ОБНОВИТЬ».
 * По нажатию скачивается новый установщик и запускается; лаунчер закрывается.
 *
 * Формат version.json:
 * {
 *   "version": "2.6.0",
 *   "url": "https://github.com/arasinvladislav55-droid/UniversalClient/releases/download/v2.6.0/UniversalClient-2.6.0.exe",
 *   "sha256": "…необязательно…",
 *   "notes": "Что нового (необязательно)"
 * }
 */
public final class Updater {
    /** Страница релизов: https://github.com/arasinvladislav55-droid/UniversalClient/releases */
    public static final String RELEASES_URL = "https://github.com/arasinvladislav55-droid/UniversalClient/releases";

    /** Ссылка на version.json в корне репозитория (ветка master). */
    public static final String UPDATE_URL = "https://raw.githubusercontent.com/arasinvladislav55-droid/UniversalClient/master/version.json";

    public record Info(String version, String url, String sha256, String notes) {}

    public interface Progress { void update(double fraction, String text); }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private Updater() {}

    /** Возвращает данные об обновлении или null, если обновлений нет / сервер недоступен. */
    public static Info check() {
        if (UPDATE_URL.contains("USER/REPO")) {
            LogFile.write("[Updater] UPDATE_URL не настроен — проверка обновлений пропущена");
            return null;
        }
        try {
            String body = Downloader.getString(UPDATE_URL + "?t=" + System.currentTimeMillis());
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            String version = str(o, "version");
            String url = str(o, "url");
            if (version.isBlank() || url.isBlank()) return null;
            if (!isNewer(version, Config.LAUNCHER_VERSION)) {
                LogFile.write("[Updater] Актуальная версия " + Config.LAUNCHER_VERSION);
                return null;
            }
            LogFile.write("[Updater] Найдено обновление " + Config.LAUNCHER_VERSION + " → " + version);
            return new Info(version, url, str(o, "sha256"), str(o, "notes"));
        } catch (Exception e) {
            LogFile.write("[Updater] Не удалось проверить обновления: " + e);
            return null;
        }
    }

    /** Скачивает установщик во временную папку лаунчера и возвращает путь к нему. */
    public static Path download(Info info, Progress progress) throws IOException, InterruptedException {
        Path dir = Dirs.ROOT.resolve("updates");
        Files.createDirectories(dir);
        String name = info.url().substring(info.url().lastIndexOf('/') + 1);
        int q = name.indexOf('?');
        if (q >= 0) name = name.substring(0, q);
        if (name.isBlank()) name = "UniversalClient-" + info.version() + ".exe";
        Path target = dir.resolve(name);
        Path tmp = dir.resolve(name + ".part");

        HttpRequest req = HttpRequest.newBuilder(URI.create(info.url())).timeout(Duration.ofMinutes(10)).build();
        HttpResponse<InputStream> r = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode() + " : " + info.url());
        long total = r.headers().firstValueAsLong("Content-Length").orElse(-1);

        MessageDigest md;
        try { md = MessageDigest.getInstance("SHA-256"); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }

        try (InputStream in = r.body(); OutputStream out = Files.newOutputStream(tmp)) {
            byte[] buf = new byte[65536];
            long done = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                md.update(buf, 0, n);
                done += n;
                if (total > 0) {
                    progress.update((double) done / total,
                            String.format("Скачивание обновления… %d / %d МБ", done >> 20, total >> 20));
                } else {
                    progress.update(-1, "Скачивание обновления…");
                }
            }
        }

        if (!info.sha256().isBlank()) {
            String got = HexFormat.of().formatHex(md.digest());
            if (!got.equalsIgnoreCase(info.sha256().trim())) {
                Files.deleteIfExists(tmp);
                throw new IOException("Контрольная сумма обновления не совпала");
            }
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    /** Запускает установщик. После этого лаунчер должен закрыться. */
    public static void runInstaller(Path installer) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        ProcessBuilder pb;
        if (os.contains("win")) {
            String f = installer.toString();
            if (f.toLowerCase().endsWith(".msi")) pb = new ProcessBuilder("msiexec", "/i", f);
            else pb = new ProcessBuilder("cmd", "/c", "start", "", f);   // start корректно обрабатывает запрос прав
        } else if (os.contains("mac")) {
            pb = new ProcessBuilder("open", installer.toString());
        } else {
            pb = new ProcessBuilder("xdg-open", installer.toString());
        }
        LogFile.write("[Updater] Запуск установщика " + installer);
        pb.start();
    }

    // ───────────── вспомогательное ─────────────

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    /** true, если a новее b (сравнение по числам: 2.10.0 > 2.9.0). */
    static boolean isNewer(String a, String b) {
        int[] x = parse(a), y = parse(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? x[i] : 0, q = i < y.length ? y[i] : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    private static int[] parse(String v) {
        String[] parts = v.trim().replaceFirst("^[vV]", "").split("[^0-9]+");
        int[] r = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { r[i] = parts[i].isEmpty() ? 0 : Integer.parseInt(parts[i]); } catch (NumberFormatException e) { r[i] = 0; }
        }
        return r;
    }
}
