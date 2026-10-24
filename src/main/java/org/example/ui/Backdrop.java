package org.example.ui;

import java.util.List;

/** Фон окна лаунчера (три оттенка градиента; применяются через CSS-переменные -uc-bg*). */
public record Backdrop(String id, String name, String c1, String c2, String c3) {

    public static final List<Backdrop> ALL = List.of(
            new Backdrop("galaxy", "Галактика", "#03040b", "#071433", "#0e2250"),
            new Backdrop("space", "Космос", "#0a0a11", "#0e0e1a", "#120f20"),
            new Backdrop("midnight", "Полночь", "#060b16", "#0a1224", "#0d1830"),
            new Backdrop("graphite", "Графит", "#0f1012", "#16181b", "#1b1d21"),
            new Backdrop("amoled", "AMOLED", "#000000", "#000000", "#06060a"));

    public static Backdrop byId(String id) {
        for (Backdrop b : ALL) if (b.id().equals(id)) return b;
        return ALL.get(0);
    }

    public String css() {
        return "-uc-bg1:" + c1 + ";-uc-bg2:" + c2 + ";-uc-bg3:" + c3 + ";";
    }

    /** Градиент для миниатюры. */
    public String preview() {
        return "linear-gradient(to bottom right, " + c1 + ", " + c2 + " 55%, " + c3 + ")";
    }
}
