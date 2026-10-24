package org.example.ui;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.example.core.Account;
import org.example.core.Config;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Экран авторизации: одна компактная карточка по центру.
 * Сверху — марка лаунчера, затем список сохранённых профилей и форма нового.
 * Акцентный цвет берётся из темы лаунчера (переменные -uc-*).
 */
public class LoginView extends StackPane {
    private static final String NAME_RE = "[A-Za-z0-9_]{3,16}";
    private static final String HINT = "3–16 символов: латиница, цифры и «_»";
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("ru"));

    private final Config cfg;
    private final Consumer<Account> onLogin;

    private final StackPane shell = new StackPane();
    private final VBox card = new VBox();

    private final VBox savedBox = new VBox(8);
    private final VBox rows = new VBox(6);
    private final StackPane avatarSlot = new StackPane();
    private final TextField field = new TextField();
    private final Label counter = new Label("0/16");
    private final Label hint = new Label(HINT);
    private final CheckBox remember = new CheckBox("Запомнить меня на этом компьютере");
    private final Label meta = new Label();

    private StackPane welcome;
    private boolean busy;
    private Theme theme = Theme.byId("purple");

    public LoginView(Config cfg, Consumer<Account> onLogin) {
        this.cfg = cfg;
        this.onLogin = onLogin;
        getStyleClass().add("login-view");
        setAlignment(Pos.CENTER);

        buildCard();

        shell.getChildren().add(card);
        shell.getStyleClass().add("lv-shell");
        shell.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        Rectangle clip = new Rectangle();
        clip.setArcWidth(32);
        clip.setArcHeight(32);
        clip.widthProperty().bind(shell.widthProperty());
        clip.heightProperty().bind(shell.heightProperty());
        shell.setClip(clip);
        getChildren().add(shell);

        refresh();
    }

    /** Цвета берутся из CSS-переменных, поэтому достаточно запомнить тему. */
    public void setTheme(Theme t) {
        theme = t;
    }

    // ───────────────────────── Вёрстка ─────────────────────────

    private void buildCard() {
        // шапка: марка + версия лаунчера
        ImageView logo = new ImageView(new Image(getClass().getResourceAsStream("/icon.png"), 28, 28, true, true));
        Label brand = new Label("Universal Client");
        brand.getStyleClass().add("lv-brand");
        Label ver = new Label("v" + Config.LAUNCHER_VERSION);
        ver.getStyleClass().add("lv-version");
        HBox top = new HBox(10, logo, brand, Fx.spacer(), ver);
        top.setAlignment(Pos.CENTER_LEFT);

        // заголовок
        Label title = new Label("Вход");
        title.getStyleClass().add("lv-title");
        Label sub = new Label("Выберите сохранённый профиль или введите ник");
        sub.getStyleClass().add("lv-sub");
        VBox head = new VBox(4, title, sub);

        // сохранённые профили
        Label savedLabel = new Label("Профили");
        savedLabel.getStyleClass().add("lv-label");
        Region line = new Region();
        line.getStyleClass().add("lv-line");
        line.setMinHeight(1);
        line.setPrefHeight(1);
        line.setMaxHeight(1);
        savedBox.getChildren().addAll(savedLabel, rows, Fx.gap(4), line);

        // новый профиль
        Label nickLabel = new Label("Никнейм");
        nickLabel.getStyleClass().add("lv-label");
        counter.getStyleClass().add("lv-counter");
        HBox labelRow = new HBox(nickLabel, Fx.spacer(), counter);
        labelRow.setAlignment(Pos.CENTER_LEFT);

        avatarSlot.setMinSize(42, 42);
        avatarSlot.setPrefSize(42, 42);
        avatarSlot.setMaxSize(42, 42);
        field.setPromptText("Например, Steve_2026");
        field.getStyleClass().addAll("input", "uc-input", "lv-input");
        field.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().length() <= 16 ? c : null));
        field.setOnAction(e -> submit());
        field.textProperty().addListener((o, a, b) -> {
            counter.setText(b.length() + "/16");
            if (hint.getStyleClass().contains("lv-hint-error")) clearError();
            updatePreview(b);
        });
        HBox.setHgrow(field, Priority.ALWAYS);
        field.setPrefHeight(42);
        HBox nickRow = new HBox(10, avatarSlot, field);
        nickRow.setAlignment(Pos.CENTER_LEFT);

        hint.getStyleClass().add("lv-hint");
        hint.setWrapText(true);
        VBox nickBox = new VBox(6, labelRow, nickRow, hint);

        remember.getStyleClass().add("uc-check");

        Button login = new Button("Войти");
        login.getStyleClass().add("lv-primary");
        login.setMaxWidth(Double.MAX_VALUE);
        login.setPrefHeight(44);
        login.setDefaultButton(false);
        login.setOnAction(e -> submit());

        Button ms = new Button("Войти через Microsoft (скоро)");
        ms.getStyleClass().add("lv-secondary");
        ms.setMaxWidth(Double.MAX_VALUE);
        ms.setPrefHeight(40);
        ms.setDisable(true);

        // подвал
        Region footLine = new Region();
        footLine.getStyleClass().add("lv-line");
        footLine.setMinHeight(1);
        footLine.setPrefHeight(1);
        footLine.setMaxHeight(1);
        meta.getStyleClass().add("lv-meta");
        Label offline = new Label("Offline-режим · профиль хранится локально");
        offline.getStyleClass().add("lv-meta");
        VBox foot = new VBox(10, footLine, new HBox(meta, Fx.spacer(), offline));

        VBox body = new VBox(16, head, savedBox, nickBox, remember, new VBox(8, login, ms));
        body.setPadding(new Insets(24, 0, 20, 0));

        card.getChildren().addAll(top, body, foot);
        card.getStyleClass().add("lv-card");
        card.setPadding(new Insets(24, 32, 20, 32));
        card.setPrefWidth(440);
        card.setMinWidth(440);
        card.setMaxWidth(440);
    }

    private Node row(Account a) {
        AvatarView av = new AvatarView(a.name, 38);
        Label name = new Label(a.name);
        name.getStyleClass().add("lv-row-name");
        String last = a.lastLogin <= 0 ? "Новый профиль"
                : "Последний вход: " + DATE.format(Instant.ofEpochMilli(a.lastLogin).atZone(ZoneId.systemDefault()));
        Label when = new Label(last);
        when.getStyleClass().add("lv-row-meta");
        VBox text = new VBox(1, name, when);
        text.setAlignment(Pos.CENTER_LEFT);
        Label chevron = new Label("›");
        chevron.getStyleClass().add("lv-chevron");

        HBox box = new HBox(12, av, text, Fx.spacer(), chevron);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("lv-row");

        Button del = new Button("✕");
        del.getStyleClass().add("lv-del");
        del.setOpacity(0);
        Tooltip.install(del, new Tooltip("Удалить профиль"));
        del.setOnMouseClicked(MouseEvent::consume);
        del.setOnAction(e -> {
            cfg.remove(a.name);
            cfg.save();
            refresh();
        });

        StackPane wrap = new StackPane(box, del);
        StackPane.setAlignment(del, Pos.CENTER_RIGHT);
        StackPane.setMargin(del, new Insets(0, 34, 0, 0));
        wrap.hoverProperty().addListener((o, x, h) -> del.setOpacity(h ? 1 : 0));
        wrap.setCursor(Cursor.HAND);
        wrap.setOnMouseClicked(e -> finish(a));
        return wrap;
    }

    private void updatePreview(String text) {
        String t = text == null ? "" : text.trim();
        Node n;
        if (t.matches(NAME_RE)) {
            n = new AvatarView(t, 42);
        } else {
            Label q = new Label("?");
            q.getStyleClass().add("lv-avatar-empty");
            q.setMinSize(42, 42);
            q.setPrefSize(42, 42);
            q.setAlignment(Pos.CENTER);
            n = q;
        }
        avatarSlot.getChildren().setAll(n);
    }

    // ───────────────────────── Логика ─────────────────────────

    public void refresh() {
        removeWelcome();
        busy = false;

        rows.getChildren().clear();
        List<Account> list = cfg.accounts.stream()
                .sorted(Comparator.comparingLong((Account a) -> a.lastLogin).reversed())
                .limit(3)
                .toList();
        boolean any = !list.isEmpty();
        savedBox.setVisible(any);
        savedBox.setManaged(any);
        for (Account a : list) rows.getChildren().add(row(a));

        remember.setSelected(cfg.autoLogin);
        meta.setText("Minecraft " + cfg.version);
        field.clear();
        clearError();
        updatePreview("");
    }

    public void animateIn() {
        shell.setOpacity(0);
        shell.setTranslateY(10);
        FadeTransition ft = new FadeTransition(Duration.millis(320), shell);
        ft.setToValue(1);
        ft.setInterpolator(Interpolator.EASE_OUT);
        TranslateTransition tt = new TranslateTransition(Duration.millis(320), shell);
        tt.setToY(0);
        tt.setInterpolator(Interpolator.EASE_OUT);
        ft.play();
        tt.play();
        Platform.runLater(field::requestFocus);
    }

    private void showError(String text) {
        hint.setText(text);
        if (!hint.getStyleClass().contains("lv-hint-error")) hint.getStyleClass().add("lv-hint-error");
        if (!field.getStyleClass().contains("lv-input-error")) field.getStyleClass().add("lv-input-error");
    }

    private void clearError() {
        hint.setText(HINT);
        hint.getStyleClass().remove("lv-hint-error");
        field.getStyleClass().remove("lv-input-error");
    }

    private void submit() {
        String name = field.getText().trim();
        if (!name.matches(NAME_RE)) {
            showError(name.isEmpty() ? "Введите никнейм" : "Недопустимый ник. " + HINT);
            Fx.shake(field);
            return;
        }
        finish(cfg.addOrGet(name));
    }

    /** Короткое приветствие и вход. */
    private void finish(Account a) {
        if (busy) return;
        busy = true;
        cfg.autoLogin = remember.isSelected();
        cfg.selected = a.name;
        a.lastLogin = System.currentTimeMillis();
        cfg.save();

        AvatarView av = new AvatarView(a.name, 72);
        Label hi = new Label("Добро пожаловать");
        hi.getStyleClass().add("lv-welcome-sub");
        Label nm = new Label(a.name);
        nm.getStyleClass().add("lv-welcome-name");
        VBox box = new VBox(8, av, Fx.gap(6), hi, nm);
        box.setAlignment(Pos.CENTER);

        welcome = new StackPane(box);
        welcome.getStyleClass().add("lv-welcome");
        welcome.setOpacity(0);
        shell.getChildren().add(welcome);

        FadeTransition ft = new FadeTransition(Duration.millis(240), welcome);
        ft.setToValue(1);
        ft.play();

        av.setScaleX(0.9);
        av.setScaleY(0.9);
        ScaleTransition pop = new ScaleTransition(Duration.millis(320), av);
        pop.setToX(1);
        pop.setToY(1);
        pop.setInterpolator(Interpolator.EASE_OUT);
        pop.play();

        PauseTransition wait = new PauseTransition(Duration.millis(900));
        wait.setOnFinished(e -> onLogin.accept(a));
        wait.play();
    }

    private void removeWelcome() {
        if (welcome != null) {
            shell.getChildren().remove(welcome);
            welcome = null;
        }
    }
}
