package org.example.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Все папки лаунчера. Windows: %APPDATA%\.universalclient */
public final class Dirs {
    public static final Path ROOT = root();
    public static final Path VERSIONS = ROOT.resolve("versions");
    public static final Path LIBRARIES = ROOT.resolve("libraries");
    public static final Path ASSETS = ROOT.resolve("assets");
    public static final Path GAME = ROOT.resolve("game");
    public static final Path LOGS = ROOT.resolve("logs");

    private Dirs() {}

    private static Path root() {
        String os = System.getProperty("os.name").toLowerCase();
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return Path.of(appData != null ? appData : home, ".universalclient");
        }
        if (os.contains("mac")) return Path.of(home, "Library", "Application Support", "universalclient");
        return Path.of(home, ".universalclient");
    }

    public static void init() throws IOException {
        for (Path p : new Path[]{ROOT, VERSIONS, LIBRARIES, ASSETS, GAME, LOGS}) {
            Files.createDirectories(p);
        }
    }
}
