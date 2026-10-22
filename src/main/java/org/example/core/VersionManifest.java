package org.example.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Список версий Minecraft для выбора (релизы 1.19 — 1.21.x). */
public final class VersionManifest {
    public record Entry(String id, String releaseTime) {}

    private static final Pattern RELEASE = Pattern.compile("^1\\.(\\d+)(?:\\.(\\d+))?$");
    private static final int MIN_MINOR = 19;

    private VersionManifest() {}

    public static List<Entry> fetch() throws Exception {
        Path cache = Dirs.ROOT.resolve("version_manifest.json");
        String text;
        try {
            text = Downloader.getString(MinecraftInstaller.MANIFEST);
            Files.createDirectories(Dirs.ROOT);
            Files.writeString(cache, text);
        } catch (Exception e) {
            if (!Files.exists(cache)) throw e;
            text = Files.readString(cache); // оффлайн — берём сохранённый список
        }
        JsonObject m = JsonParser.parseString(text).getAsJsonObject();
        List<Entry> out = new ArrayList<>();
        for (JsonElement el : m.getAsJsonArray("versions")) {
            JsonObject o = el.getAsJsonObject();
            if (!"release".equals(o.get("type").getAsString())) continue;
            String id = o.get("id").getAsString();
            Matcher mt = RELEASE.matcher(id);
            if (!mt.matches() || Integer.parseInt(mt.group(1)) < MIN_MINOR) continue;
            out.add(new Entry(id, o.get("releaseTime").getAsString()));
        }
        return out;
    }

    /** Уже скачанные версии (запасной вариант, если нет интернета). */
    public static List<Entry> installed() {
        List<Entry> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(Dirs.VERSIONS)) {
            s.filter(p -> isInstalled(p.getFileName().toString()))
                    .forEach(p -> out.add(new Entry(p.getFileName().toString(), "")));
        } catch (IOException ignored) {}
        return out;
    }

    public static boolean isInstalled(String id) {
        return Files.exists(Dirs.VERSIONS.resolve(id).resolve(id + ".jar"))
                && Files.exists(Dirs.VERSIONS.resolve(id).resolve(id + ".json"));
    }
}
