package org.example.ui;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.example.core.Account;
import org.example.core.Config;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Экран авторизации. Слева — брендовая панель (парящие кубики, смена слайдов),
 * справа — быстрый вход по профилям и создание нового. Цвета берутся из темы лаунчера.
 */
public class LoginView extends StackPane {
    private static final String NAME_RE = "[A-Za-z0-9_]{3,16}";
    private static final String[][] SLIDES = {
            {"Фирменный стиль", "Своё меню, логотип и кнопки Minecraft"},
            {"Любая версия", "Релизы от 1.19 до 1.21.11 в один клик"},
            {"Статус в Discord", "Друзья видят, что вы играете в Universal Client"},
            {"Свои темы", "Пять цветовых тем оформления лаунчера"}
    };

    private final Config cfg;
    private final Consumer<Account> onLogin;

    private final StackPane shell = new StackPane();
    private final HBox card = new HBox();
    private final StackPane left = new StackPane();
    private final VBox right = new VBox(14);

    // левая панель
    private final List<CubeArt> cubes = new ArrayList<>();
    private Circle orb1, orb2;
    private final Label versionBadge = new Label();
    private final VBox slideBox = new VBox(4);
    private final Label slideTitle = new Label();
    private final Label slideDesc = new Label();
    private final HBox dots = new HBox(6);
    private final List<Region> dotNodes = new ArrayList<>();
    private Timeline carousel;
    private int slideIdx;

    // правая панель
    private final Label hello = new Label();
    private final VBox savedBox = new VBox(10);
    private final HBox tiles = new HBox(10);
    private final StackPane avatarSlot = new StackPane();
    private final TextField field = new TextField();
    private final Label counter = new Label("0/16");
    private final Label error = new Label();
    private final CheckBox remember = new CheckBox("Запомнить меня");
    private DropShadow loginGlow;
    private final List<Node> staggered = new ArrayList<>();

    private StackPane welcome;
    private boolean busy;
    private Theme theme = Theme.byId("purple");

    public LoginView(Config cfg, Consumer<Account> onLogin) {
        this.cfg = cfg;
        this.onLogin = onLogin;
        getStyleClass().add("login-view");
        setAlignment(Pos.CENTER);

        buildLeft();
        buildRight();

        card.getChildren().addAll(left, right);
        card.getStyleClass().add("login-card");
        Rectangle clip = new Rectangle();
        clip.setArcWidth(56);
        clip.setArcHeight(56);
        clip.widthProperty().bind(card.widthProperty());
        clip.heightProperty().bind(card.heightProperty());
        card.setClip(clip);

        shell.getChildren().add(card);
        shell.getStyleClass().add("login-shell");
        shell.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        getChildren().add(shell);

        visibleProperty().addListener((o, a, v) -> {
            if (carousel == null) return;
            if (v) carousel.play(); else carousel.pause();
        });

        setTheme(theme);
        refresh();
    }

    /** Перекрашивает элементы, нарисованные кодом (кубики, свечения). */
    public void setTheme(Theme t) {
        theme = t;
        if (cubes.size() == 3) {
            cubes.get(0).setColors(t.a().interpolate(Color.WHITE, 0.25), t.b().interpolate(Color.WHITE, 0.15));
            cubes.get(1).setColors(t.c().interpolate(Color.WHITE, 0.30), t.a().interpolate(Color.WHITE, 0.20));
            cubes.get(2).setColors(t.a().interpolate(Color.WHITE, 0.35), t.c().interpolate(Color.WHITE, 0.20));
        }
        if (orb1 != null) orb1.setFill(orbFill(Color.WHITE, 0.20));
        if (orb2 != null) orb2.setFill(orbFill(t.c(), 0.40));
        if (loginGlow != null) loginGlow.setColor(t.a());
    }

    // ───────────────────────── Левая панель ─────────────────────────

    private void buildLeft() {
        Region bg = new Region();
        bg.getStyleClass().add("login-left-bg");

        orb1 = orb(190, Pos.TOP_RIGHT, 70, -60);
        orb2 = orb(170, Pos.BOTTOM_LEFT, -60, 70);
        drift(orb1, -30, 30, 9);
        drift(orb2, 30, -25, 11);

        StackPane cubeArea = new StackPane();
        cubeArea.setMinHeight(176);
        cubeArea.setPrefHeight(176);
        cubeArea.setMaxHeight(176);
        CubeArt c1 = new CubeArt(46, Color.WHITE, Color.WHITE);
        CubeArt c2 = new CubeArt(18, Color.WHITE, Color.WHITE);
        CubeArt c3 = new CubeArt(12, Color.WHITE, Color.WHITE);
        c2.setTranslateX(-96);
        c2.setTranslateY(-44);
        c3.setTranslateX(98);
        c3.setTranslateY(38);
        cubeArea.getChildren().addAll(c1, c2, c3);
        cubes.add(c1);
        cubes.add(c2);
        cubes.add(c3);
        Fx.floatY(c1, 12, 3.2, 0);
        Fx.floatY(c2, 9, 2.6, 0.4);
        Fx.floatY(c3, 10, 3.8, 0.9);

        Label brand = new Label("UNIVERSAL");
        brand.getStyleClass().add("login-brand");
        Label brandSub = new Label("C L I E N T");
        brandSub.getStyleClass().add("login-brand-sub");

        // смена слайдов
        slideTitle.getStyleClass().add("login-slide-title");
        slideDesc.getStyleClass().add("login-slide-desc");
        slideDesc.setWrapText(true);
        slideBox.getChildren().addAll(slideTitle, slideDesc);
        slideBox.setMinHeight(58);
        for (int i = 0; i < SLIDES.length; i++) {
            Region d = new Region();
            d.getStyleClass().add("login-dot");
            d.setMinHeight(4);
            d.setPrefHeight(4);
            d.setMaxHeight(4);
            dotNodes.add(d);
            dots.getChildren().add(d);
        }
        dots.setAlignment(Pos.CENTER_LEFT);
        applySlide(0);
        carousel = new Timeline(new KeyFrame(Duration.seconds(3.8), e -> nextSlide()));
        carousel.setCycleCount(Animation.INDEFINITE);

        versionBadge.getStyleClass().add("login-badge");
        Label ver = new Label("v" + Config.LAUNCHER_VERSION);
        ver.getStyleClass().add("login-ver");
        HBox bottom = new HBox(versionBadge, Fx.spacer(), ver);
        bottom.setAlignment(Pos.CENTER_LEFT);

        Region sp = new Region();
        VBox.setVgrow(sp, Priority.ALWAYS);
        VBox info = new VBox(6, cubeArea, brand, brandSub, Fx.gap(18), slideBox, Fx.gap(2), dots, sp, bottom);
        info.setPadding(new Insets(30, 32, 26, 36));

        left.getChildren().addAll(bg, orb1, orb2, info);
        left.setPrefWidth(350);
        left.setMinWidth(350);
        left.setMaxWidth(350);
        Rectangle lc = new Rectangle();
        lc.widthProperty().bind(left.widthProperty());
        lc.heightProperty().bind(left.heightProperty());
        left.setClip(lc);
    }

    private void applySlide(int i) {
        slideIdx = i;
        slideTitle.setText(SLIDES[i][0]);
        slideDesc.setText(SLIDES[i][1]);
        for (int k = 0; k < dotNodes.size(); k++) {
            Region d = dotNodes.get(k);
            boolean active = k == i;
            double w = active ? 22 : 6;
            d.setMinWidth(w);
            d.setPrefWidth(w);
            d.setMaxWidth(w);
            d.getStyleClass().remove("login-dot-active");
            if (active) d.getStyleClass().add("login-dot-active");
        }
    }

    private void nextSlide() {
        FadeTransition out = new FadeTransition(Duration.millis(180), slideBox);
        out.setToValue(0);
        out.setOnFinished(e -> {
            applySlide((slideIdx + 1) % SLIDES.length);
            slideBox.setTranslateY(8);
            FadeTransition in = new FadeTransition(Duration.millis(260), slideBox);
            in.setToValue(1);
            TranslateTransition tt = new TranslateTransition(Duration.millis(260), slideBox);
            tt.setToY(0);
            in.play();
            tt.play();
        });
        out.play();
    }

    // ───────────────────────── Правая панель ─────────────────────────

    private void buildRight() {
        hello.getStyleClass().add("login-hello");
        Label sub = new Label("Выберите профиль или создайте новый");
        sub.getStyleClass().add("muted");
        VBox head = new VBox(4, hello, sub);

        Label savedTitle = new Label("БЫСТРЫЙ ВХОД");
        savedTitle.getStyleClass().add("section-label");
        Region l1 = new Region();
        l1.getStyleClass().add("login-div");
        l1.setMinHeight(1);
        l1.setPrefHeight(1);
        l1.setMaxHeight(1);
        HBox.setHgrow(l1, Priority.ALWAYS);
        Label or = new Label("или создайте новый");
        or.getStyleClass().add("muted-small");
        Region l2 = new Region();
        l2.getStyleClass().add("login-div");
        l2.setMinHeight(1);
        l2.setPrefHeight(1);
        l2.setMaxHeight(1);
        HBox.setHgrow(l2, Priority.ALWAYS);
        HBox divider = new HBox(10, l1, or, l2);
        divider.setAlignment(Pos.CENTER);
        savedBox.getChildren().addAll(savedTitle, tiles, divider);

        Label nickLabel = new Label("НИКНЕЙМ");
        nickLabel.getStyleClass().add("section-label");
        counter.getStyleClass().add("login-counter");
        HBox labelRow = new HBox(nickLabel, Fx.spacer(), counter);
        labelRow.setAlignment(Pos.CENTER_LEFT);

        avatarSlot.setMinSize(46, 46);
        avatarSlot.setPrefSize(46, 46);
        avatarSlot.setMaxSize(46, 46);
        field.setPromptText("Введите ник");
        field.getStyleClass().addAll("input", "uc-input");
        field.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().length() <= 16 ? c : null));
        field.setOnAction(e -> submit());
        field.textProperty().addListener((o, a, b) -> {
            counter.setText(b.length() + "/16");
            updatePreview(b);
        });
        HBox nickRow = new HBox(12, avatarSlot, field);
        HBox.setHgrow(field, Priority.ALWAYS);
        nickRow.setAlignment(Pos.CENTER_LEFT);

        error.getStyleClass().add("error-label");
        error.setWrapText(true);
        error.setManaged(false);
        error.setVisible(false);
        VBox nickBox = new VBox(8, labelRow, nickRow, error);

        remember.getStyleClass().add("uc-check");

        Button login = new Button("ВОЙТИ   →");
        login.getStyleClass().add("login-go");
        login.setMaxWidth(Double.MAX_VALUE);
        login.setPrefHeight(50);
        login.setOnAction(e -> submit());
        Fx.hoverScale(login, 1.03);
        loginGlow = Fx.pulseGlow(login, theme.a());

        Button ms = new Button("Microsoft  •  скоро");
        ms.getStyleClass().add("ghost-button");
        ms.setMaxWidth(Double.MAX_VALUE);
        ms.setDisable(true);

        Label foot = new Label("Offline-режим • профиль хранится локально на этом компьютере");
        foot.getStyleClass().add("login-footer");

        right.getChildren().addAll(head, savedBox, nickBox, remember, login, ms, foot);
        right.getStyleClass().add("login-right");
        right.setPadding(new Insets(30, 36, 24, 36));
        right.setPrefWidth(450);
        right.setMinWidth(450);
        staggered.addAll(List.of(head, savedBox, nickBox, remember, login, ms, foot));
    }

    private void updatePreview(String text) {
        String t = text == null ? "" : text.trim();
        boolean valid = t.matches(NAME_RE);
        Node n;
        if (valid) {
            n = new AvatarView(t, 46);
        } else {
            Label q = new Label("?");
            q.getStyleClass().add("avatar-placeholder");
            q.setMinSize(46, 46);
            q.setPrefSize(46, 46);
            q.setAlignment(Pos.CENTER);
            n = q;
        }
        avatarSlot.getChildren().setAll(n);
        if (valid) {
            n.setScaleX(0.8);
            n.setScaleY(0.8);
            ScaleTransition st = new ScaleTransition(Duration.millis(140), n);
            st.setToX(1);
            st.setToY(1);
            st.play();
        }
    }

    // ───────────────────────── Логика ─────────────────────────

    public void refresh() {
        removeWelcome();
        busy = false;

        int h = LocalTime.now().getHour();
        String greet = h >= 5 && h < 12 ? "Доброе утро" : h < 18 && h >= 12 ? "Добрый день" : h >= 18 && h < 23 ? "Добрый вечер" : "Доброй ночи";
        hello.setText(greet + " 👋");

        tiles.getChildren().clear();
        List<Account> list = cfg.accounts.stream()
                .sorted(Comparator.comparingLong((Account a) -> a.lastLogin).reversed())
                .limit(3)
                .toList();
        boolean any = !list.isEmpty();
        savedBox.setVisible(any);
        savedBox.setManaged(any);
        for (Account a : list) tiles.getChildren().add(tile(a));

        remember.setSelected(cfg.autoLogin);
        versionBadge.setText("Minecraft " + cfg.version);
        error.setVisible(false);
        error.setManaged(false);
        field.clear();
        updatePreview("");
    }

    public void animateIn() {
        shell.setOpacity(0);
        shell.setScaleX(0.95);
        shell.setScaleY(0.95);
        FadeTransition ft = new FadeTransition(Duration.millis(450), shell);
        ft.setToValue(1);
        ScaleTransition st = new ScaleTransition(Duration.millis(450), shell);
        st.setToX(1);
        st.setToY(1);
        st.setInterpolator(Interpolator.EASE_OUT);
        ft.play();
        st.play();

        Fx.slide(left, -50, 550, 0);
        Fx.slide(right, 50, 550, 0);
        for (int i = 0; i < staggered.size(); i++) Fx.appear(staggered.get(i), 16, 420, 260 + i * 65);

        for (CubeArt c : cubes) {
            c.setScaleX(0);
            c.setScaleY(0);
            ScaleTransition pop = new ScaleTransition(Duration.millis(520), c);
            pop.setToX(1);
            pop.setToY(1);
            pop.setDelay(Duration.millis(350));
            pop.setInterpolator(Interpolator.EASE_OUT);
            pop.play();
        }
        applySlide(0);
        slideBox.setOpacity(1);
        slideBox.setTranslateY(0);
        carousel.playFromStart();
        Platform.runLater(field::requestFocus);
    }

    private Node tile(Account a) {
        AvatarView av = new AvatarView(a.name, 54);
        Label name = new Label(a.name);
        name.getStyleClass().add("tile-name");
        name.setMaxWidth(84);
        VBox v = new VBox(7, av, name);
        v.setAlignment(Pos.CENTER);
        v.getStyleClass().add("profile-tile");
        v.setPrefWidth(100);
        v.setMinWidth(100);

        Button del = new Button("✕");
        del.getStyleClass().add("tile-del");
        del.setOpacity(0);
        del.setOnMouseClicked(MouseEvent::consume);
        del.setOnAction(e -> {
            cfg.remove(a.name);
            cfg.save();
            refresh();
        });

        StackPane st = new StackPane(v, del);
        StackPane.setAlignment(del, Pos.TOP_RIGHT);
        StackPane.setMargin(del, new Insets(5, 5, 0, 0));
        st.hoverProperty().addListener((o, x, h) -> del.setOpacity(h ? 1 : 0));
        st.setCursor(Cursor.HAND);
        st.setOnMouseClicked(e -> finish(a));
        Fx.hoverScale(st, 1.05);
        return st;
    }

    private void submit() {
        String name = field.getText().trim();
        if (!name.matches(NAME_RE)) {
            error.setText("Ник: 3–16 символов — латиница, цифры и _");
            error.setVisible(true);
            error.setManaged(true);
            Fx.shake(field);
            return;
        }
        finish(cfg.addOrGet(name));
    }

    /** Приветственная анимация и вход. */
    private void finish(Account a) {
        if (busy) return;
        busy = true;
        cfg.autoLogin = remember.isSelected();
        cfg.selected = a.name;
        a.lastLogin = System.currentTimeMillis();
        cfg.save();

        Circle ring = new Circle(48);
        ring.setFill(Color.TRANSPARENT);
        ring.setStroke(theme.a());
        ring.setStrokeWidth(2);
        ring.setMouseTransparent(true);
        AvatarView av = new AvatarView(a.name, 92);
        StackPane avatarWrap = new StackPane(ring, av);

        Label hi = new Label("Добро пожаловать,");
        hi.getStyleClass().add("login-welcome-sub");
        Label nm = new Label(a.name);
        nm.getStyleClass().add("login-welcome-name");
        VBox box = new VBox(10, avatarWrap, Fx.gap(4), hi, nm);
        box.setAlignment(Pos.CENTER);

        welcome = new StackPane(box);
        welcome.getStyleClass().add("login-welcome");
        welcome.setOpacity(0);
        shell.getChildren().add(welcome);

        FadeTransition ft = new FadeTransition(Duration.millis(260), welcome);
        ft.setToValue(1);
        ft.play();

        av.setScaleX(0.4);
        av.setScaleY(0.4);
        ScaleTransition pop = new ScaleTransition(Duration.millis(480), av);
        pop.setToX(1);
        pop.setToY(1);
        pop.setInterpolator(Interpolator.EASE_OUT);
        pop.play();

        ScaleTransition rs = new ScaleTransition(Duration.millis(900), ring);
        rs.setFromX(1);
        rs.setFromY(1);
        rs.setToX(2.6);
        rs.setToY(2.6);
        FadeTransition rf = new FadeTransition(Duration.millis(900), ring);
        rf.setFromValue(0.8);
        rf.setToValue(0);
        rs.play();
        rf.play();

        Fx.appear(hi, 10, 360, 220);
        Fx.appear(nm, 10, 360, 320);

        PauseTransition wait = new PauseTransition(Duration.millis(1100));
        wait.setOnFinished(e -> onLogin.accept(a));
        wait.play();
    }

    private void removeWelcome() {
        if (welcome != null) {
            shell.getChildren().remove(welcome);
            welcome = null;
        }
    }

    // ───────────────────────── Утилиты ─────────────────────────

    private static RadialGradient orbFill(Color c, double alpha) {
        return new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0, c.deriveColor(0, 1, 1, alpha)),
                new Stop(1, c.deriveColor(0, 1, 1, 0)));
    }

    private static Circle orb(double r, Pos pos, double tx, double ty) {
        Circle o = new Circle(r);
        o.setMouseTransparent(true);
        StackPane.setAlignment(o, pos);
        o.setTranslateX(tx);
        o.setTranslateY(ty);
        return o;
    }

    private static void drift(Node n, double dx, double dy, double seconds) {
        TranslateTransition tt = new TranslateTransition(Duration.seconds(seconds), n);
        tt.setByX(dx);
        tt.setByY(dy);
        tt.setAutoReverse(true);
        tt.setCycleCount(Animation.INDEFINITE);
        tt.setInterpolator(Interpolator.EASE_BOTH);
        tt.play();
    }
}
