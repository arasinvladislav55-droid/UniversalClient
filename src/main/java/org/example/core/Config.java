package org.example.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Настройки лаунчера (хранятся в launcher.json). */
public class Config {
    public static final String DEFAULT_VERSION = "1.21.11";
    public static final String LAUNCHER_VERSION = "3.1.0";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Dirs.ROOT.resolve("launcher.json");

    public List<Account> accounts = new ArrayList<>();
    public String selected = "";
    public boolean autoLogin = true;

    public String version = DEFAULT_VERSION;
    public int ramMb = 4096;
    public String javaPath = "";
    public boolean closeOnLaunch = false;
    public boolean visualPack = true;
    public String theme = "amber";
    public boolean particles = true;
    public boolean glows = true;
    /** Свой акцентный цвет (тема «custom»), в формате #rrggbb. */
    public String customColor = "#38bdf8";
    /** Фон окна: space / graphite / amoled / midnight. */
    public String background = "galaxy";
    /** Яркость цветных свечений, 0–100 %. */
    public int glowPower = 100;
    /** Количество частиц анимированного фона. */
    public int particleCount = 70;
    /** Компактное боковое меню. */
    public boolean compactMenu = false;
    /** Вращающаяся планета на фоне и скорость вращения в процентах (0 — стоит). */
    public boolean planet = true;
    public int planetSpeed = 100;
    /** Версия оформления: при первом запуске нового оформления выставляются тема «Янтарь» и фон «Галактика». */
    public int uiVersion = 0;
    public int launches = 0;
    public long playSeconds = 0;
    public long lastPlayed = 0;
    public String lastVersion = "";
    public List<ServerEntry> servers = new ArrayList<>();
    public List<String> seenPages = new ArrayList<>();
    public boolean discordEnabled = true;
    public String discordAppId = "";
    /** Последняя версия и список изменений с GitHub (для блока «Что нового»). */
    public String newsVersion = "";
    public String newsNotes = "";

    public static Config load() {
        try {
            if (Files.exists(FILE)) {
                Config c = GSON.fromJson(Files.readString(FILE), Config.class);
                if (c != null) {
                    if (c.accounts == null) c.accounts = new ArrayList<>();
                    if (c.version == null || c.version.isBlank()) c.version = DEFAULT_VERSION;
                    if (c.discordAppId == null) c.discordAppId = "";
                    if (c.theme == null) c.theme = "purple";
                    if (c.customColor == null || !c.customColor.matches("#[0-9a-fA-F]{6}")) c.customColor = "#38bdf8";
                    if (c.background == null) c.background = "space";
                    if (c.uiVersion < 3) {
                        c.uiVersion = 3;
                        c.theme = "amber";
                        c.background = "galaxy";
                    }
                    c.planetSpeed = Math.max(0, Math.min(400, c.planetSpeed));
                    c.glowPower = Math.max(0, Math.min(100, c.glowPower));
                    if (c.particleCount < 10 || c.particleCount > 120) c.particleCount = 70;
                    if (c.newsVersion == null) c.newsVersion = "";
                    if (c.newsNotes == null) c.newsNotes = "";
                    if (c.servers == null) c.servers = new ArrayList<>();
                    if (c.seenPages == null) c.seenPages = new ArrayList<>();
                    if (c.lastVersion == null) c.lastVersion = "";
                    return c;
                }
            }
        } catch (Exception ignored) {}
        return new Config();
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this));
        } catch (Exception ignored) {}
    }

    public Account current() {
        for (Account a : accounts) if (a.name.equalsIgnoreCase(selected)) return a;
        return null;
    }

    public Account addOrGet(String name) {
        for (Account a : accounts) if (a.name.equalsIgnoreCase(name)) return a;
        Account a = new Account(name);
        accounts.add(a);
        return a;
    }

    public void remove(String name) {
        accounts.removeIf(a -> a.name.equalsIgnoreCase(name));
        if (selected.equalsIgnoreCase(name)) selected = "";
    }
}
