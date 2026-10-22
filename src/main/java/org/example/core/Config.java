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
    public static final String LAUNCHER_VERSION = "2.6.0";

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
    public String theme = "purple";
    public boolean particles = true;
    public boolean glows = true;
    public int launches = 0;
    public long playSeconds = 0;
    public long lastPlayed = 0;
    public String lastVersion = "";
    public List<ServerEntry> servers = new ArrayList<>();
    public List<String> seenPages = new ArrayList<>();
    public boolean discordEnabled = true;
    public String discordAppId = "";

    public static Config load() {
        try {
            if (Files.exists(FILE)) {
                Config c = GSON.fromJson(Files.readString(FILE), Config.class);
                if (c != null) {
                    if (c.accounts == null) c.accounts = new ArrayList<>();
                    if (c.version == null || c.version.isBlank()) c.version = DEFAULT_VERSION;
                    if (c.discordAppId == null) c.discordAppId = "";
                    if (c.theme == null) c.theme = "purple";
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
