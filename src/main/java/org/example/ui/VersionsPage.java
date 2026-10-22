package org.example.ui;

import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.example.core.Config;
import org.example.core.LogFile;
import org.example.core.VersionManifest;

import java.util.ArrayList;
import java.util.List;

/** Страница выбора версии Minecraft. */
public class VersionsPage extends VBox {
    private final Config cfg;
    private final Runnable onSelected;
    private final FlowPane grid = new FlowPane(12, 12);
    private final Label state = new Label();
    private final TextField search = new TextField();
    private List<VersionManifest.Entry> versions = new ArrayList<>();
    private String chipFilter = "Все";
    private String popId;

    public VersionsPage(Config cfg, Runnable onSelected) {
        super(16);
        this.cfg = cfg;
        this.onSelected = onSelected;

        Label h = new Label("Версии");
        h.getStyleClass().add("page-title");
        Label hint = new Label("Выберите версию Minecraft — она скачается автоматически при запуске");
        hint.getStyleClass().add("muted");

        Button refresh = new Button("⟳  Обновить");
        refresh.getStyleClass().add("secondary-button");
        refresh.setOnAction(e -> reload());
        Fx.hoverScale(refresh, 1.04);

        HBox head = new HBox(new VBox(2, h, hint), Fx.spacer(), refresh);
        head.setAlignment(Pos.CENTER_LEFT);

        search.setPromptText("🔍  Поиск версии...");
        search.getStyleClass().addAll("input", "uc-input");
        search.setPrefWidth(220);
        search.textProperty().addListener((o, a, b) -> rebuild());

        ToggleGroup chipGroup = new ToggleGroup();
        HBox chips = new HBox(8);
        chips.setAlignment(Pos.CENTER_LEFT);
        for (String c : new String[]{"Все", "1.21", "1.20", "1.19", "Установленные"}) {
            ToggleButton b = new ToggleButton(c);
            b.setToggleGroup(chipGroup);
            b.getStyleClass().add("chip");
            if (c.equals("Все")) b.setSelected(true);
            b.setOnAction(e -> { chipFilter = c; rebuild(); });
            chips.getChildren().add(b);
        }
        chipGroup.selectedToggleProperty().addListener((o, old, now) -> { if (now == null && old != null) old.setSelected(true); });

        state.getStyleClass().add("muted-small");
        HBox filters = new HBox(14, search, chips, Fx.spacer(), state);
        filters.setAlignment(Pos.CENTER_LEFT);

        grid.setPadding(new Insets(6));
        ScrollPane sp = new ScrollPane(grid);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.getStyleClass().add("version-scroll");
        VBox.setVgrow(sp, Priority.ALWAYS);

        getChildren().addAll(head, filters, sp);
        setPadding(new Insets(28, 34, 18, 34));
        reload();
    }

    public void reload() {
        state.setText("Загрузка списка версий...");
        Task<List<VersionManifest.Entry>> t = new Task<>() {
            @Override
            protected List<VersionManifest.Entry> call() throws Exception {
                return VersionManifest.fetch();
            }
        };
        t.setOnSucceeded(e -> { versions = t.getValue(); rebuild(); });
        t.setOnFailed(e -> {
            LogFile.write("[Versions] " + t.getException());
            versions = VersionManifest.installed();
            rebuild();
            state.setText("Нет подключения — показаны только скачанные версии");
        });
        Thread th = new Thread(t, "versions");
        th.setDaemon(true);
        th.start();
    }

    public void rebuild() {
        grid.getChildren().clear();
        String q = search.getText() == null ? "" : search.getText().trim().toLowerCase();
        int shown = 0;
        for (VersionManifest.Entry e : versions) {
            if (!q.isEmpty() && !e.id().toLowerCase().contains(q)) continue;
            boolean installed = VersionManifest.isInstalled(e.id());
            if (chipFilter.equals("Установленные")) {
                if (!installed) continue;
            } else if (!chipFilter.equals("Все")) {
                if (!(e.id().equals(chipFilter) || e.id().startsWith(chipFilter + "."))) continue;
            }
            grid.getChildren().add(card(e, installed));
            shown++;
        }
        state.setText(shown == 0 ? "Ничего не найдено" : "Найдено: " + shown);
    }

    private Node card(VersionManifest.Entry e, boolean installed) {
        boolean selected = e.id().equals(cfg.version);
        boolean recommended = e.id().equals(Config.DEFAULT_VERSION);

        Label id = new Label(e.id());
        id.getStyleClass().add("ver-id");
        Label tag = new Label(recommended ? "★ РЕКОМЕНДУЕМАЯ" : "РЕЛИЗ");
        tag.getStyleClass().add(recommended ? "tag-hot" : "tag");

        Label date = new Label(e.releaseTime().length() >= 10 ? formatDate(e.releaseTime().substring(0, 10)) : "—");
        date.getStyleClass().add("muted-small");

        Label st = new Label(installed ? "● Установлена" : "○ Будет скачана");
        st.getStyleClass().add(installed ? "ver-installed" : "muted-small");
        Label mark = new Label(selected ? "✓ ВЫБРАНА" : "");
        mark.getStyleClass().add("ver-mark");

        HBox top = new HBox(id, Fx.spacer(), tag);
        top.setAlignment(Pos.CENTER_LEFT);
        HBox bottom = new HBox(st, Fx.spacer(), mark);
        bottom.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(6, top, date, Fx.gap(4), bottom);
        card.getStyleClass().add("ver-card");
        if (selected) card.getStyleClass().add("ver-selected");
        card.setPrefWidth(200);
        Fx.hoverScale(card, 1.03);
        card.setOnMouseClicked(ev -> {
            cfg.version = e.id();
            cfg.save();
            popId = e.id();
            onSelected.run();
            rebuild();
        });
        if (e.id().equals(popId)) {
            popId = null;
            card.setScaleX(0.93);
            card.setScaleY(0.93);
            ScaleTransition pop = new ScaleTransition(Duration.millis(260), card);
            pop.setToX(1);
            pop.setToY(1);
            pop.setInterpolator(Interpolator.EASE_OUT);
            pop.play();
        }
        return card;
    }

    private static String formatDate(String iso) {
        String[] p = iso.split("-");
        return p.length == 3 ? p[2] + "." + p[1] + "." + p[0] : iso;
    }
}
