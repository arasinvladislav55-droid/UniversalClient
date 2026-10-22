package org.example.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Проверка "rules" из JSON-файла версии Mojang (ОС, архитектура). */
public final class Rules {
    private Rules() {}

    public static String os() {
        String n = System.getProperty("os.name").toLowerCase();
        if (n.contains("win")) return "windows";
        if (n.contains("mac")) return "osx";
        return "linux";
    }

    public static boolean allowed(JsonArray rules) {
        if (rules == null || rules.isEmpty()) return true;
        boolean allow = false;
        for (JsonElement e : rules) {
            JsonObject r = e.getAsJsonObject();
            if (matches(r)) allow = r.get("action").getAsString().equals("allow");
        }
        return allow;
    }

    private static boolean matches(JsonObject r) {
        if (r.has("features")) return false; // demo / quick-play / resolution — не используем
        if (r.has("os")) {
            JsonObject o = r.getAsJsonObject("os");
            if (o.has("name") && !o.get("name").getAsString().equals(os())) return false;
            if (o.has("arch")) {
                String a = o.get("arch").getAsString();
                String sys = System.getProperty("os.arch").toLowerCase();
                boolean is64 = sys.contains("64");
                boolean arm = sys.contains("aarch64") || sys.contains("arm64");
                if (a.equals("x86") && is64) return false;
                if (a.equals("arm64") && !arm) return false;
            }
        }
        return true;
    }
}
