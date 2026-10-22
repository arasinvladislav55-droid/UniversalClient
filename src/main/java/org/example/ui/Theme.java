package org.example.ui;

import javafx.scene.paint.Color;

import java.util.List;

/** Цветовая тема лаунчера (применяется через CSS-переменные -uc-*). */
public record Theme(String id, String name, Color a, Color b, Color c) {

    public static final List<Theme> ALL = List.of(
            of("purple", "Фиолетовый", "#8b5cf6", "#6366f1", "#22d3ee"),
            of("ocean", "Океан", "#0ea5e9", "#3b82f6", "#a78bfa"),
            of("rose", "Роза", "#ec4899", "#f43f5e", "#fb923c"),
            of("emerald", "Изумруд", "#10b981", "#14b8a6", "#a3e635"),
            of("sunset", "Закат", "#f97316", "#ef4444", "#facc15"));

    private static Theme of(String id, String name, String a, String b, String c) {
        return new Theme(id, name, Color.web(a), Color.web(b), Color.web(c));
    }

    public static Theme byId(String id) {
        for (Theme t : ALL) if (t.id().equals(id)) return t;
        return ALL.get(0);
    }

    public Color light() {
        return a.interpolate(Color.WHITE, 0.55);
    }

    public static String hex(Color c) {
        return String.format("#%02x%02x%02x",
                (int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
    }

    /** Строка inline-стиля с переменными темы. */
    public String css() {
        String ha = hex(a), hc = hex(c);
        return "-uc-a:" + ha + ";-uc-b:" + hex(b) + ";-uc-c:" + hc + ";"
                + "-uc-a-soft:" + ha + "26;-uc-a-mid:" + ha + "59;-uc-a-strong:" + ha + "99;"
                + "-uc-c-soft:" + hc + "26;-uc-light:" + hex(light()) + ";";
    }
}
