package org.example.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import org.example.core.Config;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Оформление лаунчера: цветовые темы и визуальные эффекты. */
public class AppearancePage extends VBox {
    private final Config cfg;
    private final Consumer<Theme> onTheme;
    private final Map<String, VBox> boxes = new HashMap<>();
    private final Map<String, Label> checks = new HashMap<>();

    public AppearancePage(Config cfg, Consumer<Theme> onTheme, Runnable onToggles) {
        super(14);
        this.cfg = cfg;
        this.onTheme = onTheme;

        Label h = new Label("Оформление");
        h.getStyleClass().add("page-title");
        Label hint = new Label("Цвета меняются мгновенно и сохраняются автоматически");
        hint.getStyleClass().add("muted");

        Label themeTitle = new Label("Цветовая тема");
        themeTitle.getStyleClass().add("card-title");
        FlowPane grid = new FlowPane(14, 14);
        for (Theme t : Theme.ALL) grid.getChildren().add(swatch(t));
        VBox themeCard = new VBox(14, themeTitle, grid);
        themeCard.getStyleClass().add("card");

        Label fxTitle = new Label("Эффекты");
        fxTitle.getStyleClass().add("card-title");
        CheckBox particles = new CheckBox("Анимированный фон (частицы)");
        particles.getStyleClass().add("uc-check");
        particles.setSelected(cfg.particles);
        particles.selectedProperty().addListener((o, a, b) -> { cfg.particles = b; cfg.save(); onToggles.run(); });
        CheckBox glows = new CheckBox("Цветные свечения на фоне");
        glows.getStyleClass().add("uc-check");
        glows.setSelected(cfg.glows);
        glows.selectedProperty().addListener((o, a, b) -> { cfg.glows = b; cfg.save(); onToggles.run(); });
        VBox fxCard = new VBox(12, fxTitle, particles, glows);
        fxCard.getStyleClass().add("card");

        VBox cards = new VBox(16, themeCard, fxCard);
        cards.setMaxWidth(640);
        cards.setPadding(new Insets(4));
        ScrollPane sp = new ScrollPane(cards);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.getStyleClass().add("version-scroll");
        VBox.setVgrow(sp, Priority.ALWAYS);

        getChildren().addAll(new VBox(2, h, hint), sp);
        setPadding(new Insets(28, 36, 16, 36));
        refreshSelection();
    }

    private Node swatch(Theme t) {
        Region preview = new Region();
        preview.setMinSize(120, 40);
        preview.setPrefSize(120, 40);
        preview.setStyle("-fx-background-radius: 10; -fx-background-color: linear-gradient(to right, "
                + Theme.hex(t.a()) + ", " + Theme.hex(t.b()) + ", " + Theme.hex(t.c()) + ");");
        Label name = new Label(t.name());
        name.getStyleClass().add("row-name");
        Label check = new Label();
        check.getStyleClass().add("ver-mark");
        VBox v = new VBox(8, preview, name, check);
        v.getStyleClass().add("swatch");
        v.setPrefWidth(144);
        Fx.hoverScale(v, 1.04);
        v.setOnMouseClicked(e -> {
            cfg.theme = t.id();
            cfg.save();
            onTheme.accept(t);
            refreshSelection();
        });
        boxes.put(t.id(), v);
        checks.put(t.id(), check);
        return v;
    }

    private void refreshSelection() {
        for (Theme t : Theme.ALL) {
            VBox v = boxes.get(t.id());
            boolean sel = t.id().equals(cfg.theme);
            v.getStyleClass().remove("swatch-selected");
            if (sel) v.getStyleClass().add("swatch-selected");
            checks.get(t.id()).setText(sel ? "✓ ВЫБРАНА" : " ");
        }
    }
}
