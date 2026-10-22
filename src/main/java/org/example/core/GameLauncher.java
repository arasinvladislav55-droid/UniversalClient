package org.example.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/** Собирает команду запуска и стартует Minecraft. */
public final class GameLauncher {
    private GameLauncher() {}

    public static Process launch(Config cfg, JsonObject json, Consumer<String> log) throws IOException {
        return launch(cfg, json, log, null);
    }

    /** joinAddress (host или host:port) — сразу зайти на сервер после запуска; null — обычный запуск. */
    public static Process launch(Config cfg, JsonObject json, Consumer<String> log, String joinAddress) throws IOException {
        String version = json.get("id").getAsString();
        Path vDir = Dirs.VERSIONS.resolve(version);
        Path natives = vDir.resolve("natives");
        Files.createDirectories(natives);
        Files.createDirectories(Dirs.GAME);

        // classpath
        List<String> cp = new ArrayList<>();
        for (Path p : MinecraftInstaller.libraryPaths(json)) cp.add(p.toString());
        cp.add(vDir.resolve(version + ".jar").toString());

        Account acc = cfg.current();
        if (acc == null) throw new IOException("Профиль не выбран");
        String uuid = acc.uuid;

        Map<String, String> vars = new HashMap<>();
        vars.put("auth_player_name", acc.name);
        vars.put("version_name", version);
        vars.put("game_directory", Dirs.GAME.toString());
        vars.put("assets_root", Dirs.ASSETS.toString());
        vars.put("assets_index_name", json.getAsJsonObject("assetIndex").get("id").getAsString());
        vars.put("auth_uuid", uuid);
        vars.put("auth_access_token", "0");
        vars.put("clientid", "");
        vars.put("auth_xuid", "0");
        vars.put("user_type", "legacy");
        vars.put("version_type", json.has("type") ? json.get("type").getAsString() : "release");
        vars.put("natives_directory", natives.toString());
        vars.put("launcher_name", "UniversalClient");
        vars.put("launcher_version", Config.LAUNCHER_VERSION);
        vars.put("classpath", String.join(File.pathSeparator, cp));
        vars.put("library_directory", Dirs.LIBRARIES.toString());
        vars.put("classpath_separator", File.pathSeparator);
        vars.put("user_properties", "{}");

        JsonObject args = json.getAsJsonObject("arguments");

        List<String> cmd = new ArrayList<>();
        cmd.add(javaExecutable(cfg, json));
        cmd.add("-Xmx" + cfg.ramMb + "M");
        cmd.add("-Xms512M");
        cmd.add("-XX:+UseG1GC");
        cmd.addAll(expand(args.getAsJsonArray("jvm"), vars));
        cmd.add(json.get("mainClass").getAsString());
        cmd.addAll(expand(args.getAsJsonArray("game"), vars));
        if (joinAddress != null && !joinAddress.isBlank()) {
            cmd.addAll(joinArgs(version, joinAddress.trim()));
            log.accept("[Launcher] Автоматический вход на сервер " + joinAddress.trim());
        }

        log.accept("[Launcher] Java: " + cmd.get(0));
        log.accept("[Launcher] Запуск Minecraft " + version + " как " + acc.name);

        ProcessBuilder pb = new ProcessBuilder(cmd).directory(Dirs.GAME.toFile()).redirectErrorStream(true);
        if (cfg.closeOnLaunch) {
            // лаунчер закроется — пишем вывод игры в файл
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(Dirs.LOGS.resolve("latest.log").toFile()));
            return pb.start();
        }
        Process p = pb.start();
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) log.accept(line);
            } catch (IOException ignored) {}
        }, "game-output");
        t.setDaemon(true);
        t.start();
        return p;
    }

    /** 1.20+ понимает --quickPlayMultiplayer, 1.19 — классические --server/--port. */
    private static List<String> joinArgs(String version, String address) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^1\\.(\\d+)").matcher(version);
        boolean legacy = m.find() && Integer.parseInt(m.group(1)) < 20;
        if (!legacy) return List.of("--quickPlayMultiplayer", address);
        int idx = address.lastIndexOf(':');
        String host = idx > 0 ? address.substring(0, idx) : address;
        String port = idx > 0 ? address.substring(idx + 1) : "25565";
        return List.of("--server", host, "--port", port);
    }

    private static String javaExecutable(Config cfg, JsonObject json) throws IOException {
        if (cfg.javaPath != null && !cfg.javaPath.isBlank()) return cfg.javaPath.trim();
        int need = json.has("javaVersion") ? json.getAsJsonObject("javaVersion").get("majorVersion").getAsInt() : 8;
        int have = Runtime.version().feature();
        if (have < need) {
            throw new IOException("Для этой версии нужна Java " + need + " (сейчас " + have + "). Укажите путь к Java в настройках.");
        }
        String exe = Rules.os().equals("windows") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", exe).toString();
    }

    private static List<String> expand(JsonArray arr, Map<String, String> vars) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : arr) {
            if (e.isJsonPrimitive()) {
                out.add(replace(e.getAsString(), vars));
            } else {
                JsonObject o = e.getAsJsonObject();
                if (!Rules.allowed(o.has("rules") ? o.getAsJsonArray("rules") : null)) continue;
                JsonElement v = o.get("value");
                if (v.isJsonArray()) for (JsonElement x : v.getAsJsonArray()) out.add(replace(x.getAsString(), vars));
                else out.add(replace(v.getAsString(), vars));
            }
        }
        return out;
    }

    private static String replace(String s, Map<String, String> vars) {
        for (Map.Entry<String, String> en : vars.entrySet()) s = s.replace("${" + en.getKey() + "}", en.getValue());
        return s;
    }
}
