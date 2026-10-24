package org.example.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.example.core.Config;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/** Оформление лаунчера: цветовые темы, свой цвет, фон окна, эффекты и вид меню. */
public class AppearancePage extends VBox {
    private final Config cfg;
    private final Consumer<Theme> onTheme;
    private final Runnable onLook;
    private final Map<String, VBox> themeTiles = new HashMap<>();
    private final Map<String, VBox> backdropTiles = new HashMap<>();
    private final Map<String, Label> themeChecks = new HashMap<>();
    private final Map<String, Label> backdropChecks = new HashMap<>();
    private Region customPreview;
    private ColorPicker picker;

    /**
     * @param onTheme выбрана другая цветовая тема
     * @param onLook  изменился любой другой параметр вида (фон, свечения, частицы, меню)
     */
    public AppearancePage(Config cfg, Consumer<Theme> onTheme, Runnable onLook) {
        super(14);
        this.cfg = cfg;
        this.onTheme = onTheme;
        this.onLook = onLook;

        Label h = new Label("Оформление");
        h.getStyleClass().add("page-title");
        Label hint = new Label("Все изменения применяются мгновенно и сохраняются автоматически");
        hint.getStyleClass().add("muted");

        // ── цветовая тема + свой цвет ──
        FlowPane themes = new FlowPane(12, 12);
        for (Theme t : Theme.ALL) themes.getChildren().add(themeTile(t));
        themes.getChildren().add(customTile());
        VBox themeCard = card("Цветовая тема", "Готовые палитры или собственный акцентный цвет", themes);

        // ── фон окна ──
        FlowPane backs = new FlowPane(12, 12);
        for (Backdrop b : Backdrop.ALL) backs.getChildren().add(backdropTile(b));
        VBox backCard = card("Фон окна", "От глубокого космоса до чистого чёрного — экономит заряд на OLED-экранах", backs);

        // ── эффекты ──
        CheckBox particles = check("Анимированный фон (частицы)", cfg.particles, v -> cfg.particles = v);
        Node density = sliderRow("Количество частиц", 10, ParticleCanvas.MAX, cfg.particleCount,
                v -> String.valueOf(v), v -> cfg.particleCount = v);
        density.disableProperty().bind(particles.selectedProperty().not());
        CheckBox glows = check("Цветные свечения на фоне", cfg.glows, v -> cfg.glows = v);
        Node power = sliderRow("Яркость свечений", 0, 100, cfg.glowPower, v -> v + " %", v -> cfg.glowPower = v);
        power.disableProperty().bind(glows.selectedProperty().not());
        CheckBox planetBox = check("Вращающаяся планета на фоне", cfg.planet, v -> cfg.planet = v);
        Node planetSpeed = sliderRow("Скорость вращения планеты", 0, 400, cfg.planetSpeed, v -> (v / 100.0) + "×", v -> cfg.planetSpeed = v);
        planetSpeed.disableProperty().bind(planetBox.selectedProperty().not());
        VBox fxCard = card("Эффекты", "Меньше эффектов — меньше нагрузка на процессор и видеокарту",
                planetBox, planetSpeed, particles, density, glows, power);

        // ── интерфейс ──
        CheckBox compact = check("Компактное боковое меню", cfg.compactMenu, v -> cfg.compactMenu = v);
        VBox uiCard = card("Интерфейс", "Плотность элементов навигации", compact);

        VBox cards = new VBox(16, themeCard, backCard, fxCard, uiCard);
        cards.setMaxWidth(660);
        cards.setPadding(new Insets(4, 4, 12, 4));
        ScrollPane sp = new ScrollPane(cards);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.getStyleClass().add("version-scroll");
        VBox.setVgrow(sp, Priority.ALWAYS);

        getChildren().addAll(new VBox(2, h, hint), sp);
        setPadding(new Insets(28, 36, 16, 36));
        refreshSelection();
    }

    // ───────────────────────── Блоки ─────────────────────────

    private static VBox card(String title, String caption, Node... content) {
        Label t = new Label(title);
        t.getStyleClass().add("card-title");
        Label c = new Label(caption);
        c.getStyleClass().add("muted-small");
        c.setWrapText(true);
        VBox box = new VBox(12, new VBox(2, t, c));
        box.getChildren().addAll(content);
        box.getStyleClass().add("card");
        return box;
    }

    private CheckBox check(String text, boolean value, Consumer<Boolean> set) {
        CheckBox cb = new CheckBox(text);
        cb.getStyleClass().add("uc-check");
        cb.setSelected(value);
        cb.selectedProperty().addListener((o, a, b) -> { set.accept(b); cfg.save(); onLook.run(); });
        return cb;
    }

    private Node sliderRow(String title, int min, int max, int value, IntFunction<String> fmt, Consumer<Integer> set) {
        Label name = new Label(title);
        name.getStyleClass().add("muted");
        Label val = new Label(fmt.apply(value));
        val.getStyleClass().add("accent");
        Slider s = new Slider(min, max, value);
        s.setMaxWidth(Double.MAX_VALUE);
        s.valueProperty().addListener((o, a, b) -> {
            int v = (int) Math.round(b.doubleValue());
            val.setText(fmt.apply(v));
            set.accept(v);
            onLook.run();
            if (!s.isValueChanging()) cfg.save();     // во время перетаскивания файл не пишем
        });
        s.valueChangingProperty().addListener((o, a, changing) -> { if (!changing) cfg.save(); });
        HBox head = new HBox(name, Fx.spacer(), val);
        head.setAlignment(Pos.CENTER_LEFT);
        return new VBox(6, head, s);
    }

    // ───────────────────────── Плитки тем и фонов ─────────────────────────

    private VBox tile(String gradient, String name, Label check) {
        Region preview = new Region();
        preview.setMinSize(120, 40);
        preview.setPrefSize(120, 40);
        preview.setStyle("-fx-background-radius: 10; -fx-background-color: " + gradient + ";");
        Label n = new Label(name);
        n.getStyleClass().add("row-name");
        check.getStyleClass().add("ver-mark");
        VBox v = new VBox(8, preview, n, check);
        v.getStyleClass().add("swatch");
        v.setPrefWidth(144);
        Fx.hoverScale(v, 1.04);
        return v;
    }

    private static String themeGradient(Theme t) {
        return "linear-gradient(to right, " + Theme.hex(t.a()) + ", " + Theme.hex(t.b()) + ", " + Theme.hex(t.c()) + ")";
    }

    private Node themeTile(Theme t) {
        Label check = new Label();
        VBox v = tile(themeGradient(t), t.name(), check);
        v.setOnMouseClicked(e -> {
            cfg.theme = t.id();
            cfg.save();
            onTheme.accept(t);
            refreshSelection();
        });
        themeTiles.put(t.id(), v);
        themeChecks.put(t.id(), check);
        return v;
    }

    /** Плитка «Свой цвет»: клик применяет цвет, а палитра внутри позволяет выбрать любой другой. */
    private Node customTile() {
        Label check = new Label();
        Theme current = Theme.custom(cfg.customColor);
        VBox v = tile(themeGradient(current), "Свой цвет", check);
        customPreview = (Region) v.getChildren().get(0);

        picker = new ColorPicker(Color.web(cfg.customColor));
        picker.getStyleClass().add("uc-picker");
        picker.setMaxWidth(Double.MAX_VALUE);
        picker.setOnAction(e -> {
            cfg.customColor = Theme.hex(picker.getValue());
            cfg.theme = "custom";
            cfg.save();
            Theme t = Theme.custom(cfg.customColor);
            customPreview.setStyle("-fx-background-radius: 10; -fx-background-color: " + themeGradient(t) + ";");
            onTheme.accept(t);
            refreshSelection();
        });
        v.getChildren().add(picker);
        v.setOnMouseClicked(e -> {
            if (e.getTarget() instanceof Node n && isInside(n, picker)) return;   // клики по палитре обрабатывает сама палитра
            cfg.theme = "custom";
            cfg.save();
            onTheme.accept(Theme.custom(cfg.customColor));
            refreshSelection();
        });
        themeTiles.put("custom", v);
        themeChecks.put("custom", check);
        return v;
    }

    private static boolean isInside(Node n, Node parent) {
        for (Node p = n; p != null; p = p.getParent()) if (p == parent) return true;
        return false;
    }

    private Node backdropTile(Backdrop b) {
        Label check = new Label();
        VBox v = tile(b.preview(), b.name(), check);
        v.setOnMouseClicked(e -> {
            cfg.background = b.id();
            cfg.save();
            onLook.run();
            refreshSelection();
        });
        backdropTiles.put(b.id(), v);
        backdropChecks.put(b.id(), check);
        return v;
    }

    private void refreshSelection() {
        mark(themeTiles, themeChecks, cfg.theme == null ? "purple" : cfg.theme);
        mark(backdropTiles, backdropChecks, cfg.background);
    }

    private static void mark(Map<String, VBox> tiles, Map<String, Label> checks, String selected) {
        for (Map.Entry<String, VBox> e : tiles.entrySet()) {
            boolean sel = e.getKey().equals(selected);
            e.getValue().getStyleClass().remove("swatch-selected");
            if (sel) e.getValue().getStyleClass().add("swatch-selected");
            checks.get(e.getKey()).setText(sel ? "✓ ВЫБРАНО" : " ");
        }
    }
}
