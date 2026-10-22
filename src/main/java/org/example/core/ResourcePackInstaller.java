package org.example.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Устанавливает фирменный ресурспак «Rounded UI» (скруглённые кнопки, слайдеры, вкладки, подсказки,
 * хотбар, фоны меню, логотип, панорама) и включает его в options.txt.
 * Цвета пака подбираются под цветовую тему лаунчера (/pack/UniversalClient-Pack-<тема>.zip).
 */
public final class ResourcePackInstaller {
    private static final String PACK_FILE = "UniversalClient.zip";
    private static final String PACK_ENTRY = "file/" + PACK_FILE;

    private ResourcePackInstaller() {}

    /** Совместимость: версия берётся из настроек. */
    public static void apply(Config cfg) throws IOException {
        apply(cfg, cfg.version);
    }

    public static void apply(Config cfg, String mcVersion) throws IOException {
        Path packs = Dirs.GAME.resolve("resourcepacks");
        Files.createDirectories(packs);
        boolean enable = cfg.visualPack && supports(mcVersion);
        if (enable) {
            String theme = cfg.theme == null || cfg.theme.isBlank() ? "purple" : cfg.theme;
            InputStream in = ResourcePackInstaller.class.getResourceAsStream("/pack/UniversalClient-Pack-" + theme + ".zip");
            if (in == null) in = ResourcePackInstaller.class.getResourceAsStream("/pack/UniversalClient-Pack.zip");
            if (in == null) throw new IOException("Ресурспак не найден внутри лаунчера");
            try (InputStream src = in) {
                Files.copy(src, packs.resolve(PACK_FILE), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        updateOptions(enable);
    }

    /**
     * Пак использует спрайтовый GUI и новый формат логотипа (Minecraft 1.21+).
     * На более старых версиях (1.19 — 1.20.x) он автоматически отключается, чтобы ничего не ломалось.
     */
    static boolean supports(String version) {
        if (version == null) return true;
        String[] p = version.split("[.\\-]");
        try {
            int major = Integer.parseInt(p[0]);
            int minor = p.length > 1 ? Integer.parseInt(p[1]) : 0;
            if (major > 1) return true;
            return minor >= 21;
        } catch (NumberFormatException e) {
            return true; // снапшоты и нестандартные версии — не мешаем
        }
    }

    private static void updateOptions(boolean enable) throws IOException {
        Path opt = Dirs.GAME.resolve("options.txt");
        List<String> lines = Files.exists(opt)
                ? new ArrayList<>(Files.readAllLines(opt, StandardCharsets.UTF_8))
                : new ArrayList<>();

        boolean hasPacks = false, hasLang = false, hasOnboard = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("resourcePacks:")) {
                hasPacks = true;
                List<String> list = new ArrayList<>();
                try {
                    JsonArray arr = JsonParser.parseString(line.substring("resourcePacks:".length())).getAsJsonArray();
                    for (JsonElement e : arr) list.add(e.getAsString());
                } catch (Exception ex) {
                    list.add("vanilla");
                }
                list.remove(PACK_ENTRY);
                if (enable) list.add(PACK_ENTRY); // последний в списке = наивысший приоритет
                JsonArray out = new JsonArray();
                for (String s : list) out.add(s);
                lines.set(i, "resourcePacks:" + out);
            } else if (line.startsWith("lang:")) {
                hasLang = true;
            } else if (line.startsWith("onboardAccessibility:")) {
                hasOnboard = true;
            }
        }
        if (!hasPacks && enable) lines.add("resourcePacks:[\"vanilla\",\"" + PACK_ENTRY + "\"]");
        if (!hasLang) lines.add("lang:ru_ru");
        if (!hasOnboard) lines.add("onboardAccessibility:false");
        Files.write(opt, lines, StandardCharsets.UTF_8);
    }
}
