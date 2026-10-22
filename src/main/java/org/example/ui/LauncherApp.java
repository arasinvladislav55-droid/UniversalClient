package org.example.ui;

import com.google.gson.JsonObject;
import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.example.core.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class LauncherApp extends Application {

    private static final double TITLE_H = 44;
    private static final double W = 1000, H = 640, M = 16;

    private Config cfg;
    private Theme theme;
    private Stage primaryStage;
    private double dragX, dragY;

    // каркас окна
    private StackPane window;
    private Region shadowBase;
    private StackPane content;
    private Circle glowA, glowB;
    private ParticleCanvas particles;
    private final StackPane pages = new StackPane();
    private Node currentPage;
    private BorderPane mainArea;
    private LoginView loginView;
    private SplashView splash;
    private VBox profileCard;
    private Button logoutBtn;
    private Label crumb, discordPill, mcBadge;

    // меню и страницы
    private ToggleButton navHome, navVersions, navShots, navProfile, navTheme, navSettings, navAbout;
    private ToggleButton navServers, navStorage;
    private Node homePage, aboutPage;
    private VersionsPage versionsPage;
    private ServersPage serversPage;
    private StoragePage storagePage = new StoragePage(this::openUri);
    private ScreenshotsPage shotsPage;
    private ProfilePage profilePage;
    private AppearancePage themePage;
    private SettingsPage settingsPage;

    // главная
    private StackPane heroCard;
    private Circle heroOrb, heroDot;
    private Label heroState, greeting, status;
    private Button playBtn, versionChip;
    private ProgressBar bar;
    private DropShadow playGlow;
    private final List<CubeArt> cubes = new ArrayList<>();
    private final List<Node> statCards = new ArrayList<>();
    private Node newsCard, quickCard;
    private final Label statVersion = new Label();
    private final Label statRam = new Label();
    private final Label statPlay = new Label();
    private final Label statLaunches = new Label();

    // Discord и состояние игры
    private DiscordPresence presence;
    private final long appStart = System.currentTimeMillis();
    private boolean atLogin, installing, gameRunning;

    // обновление лаунчера (GitHub)
    private Updater.Info pendingUpdate;
    private boolean updating;
    private ScheduledExecutorService updateTimer;
    private long gameStart;
    private String gameVersion = "";

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        Dirs.init();
        cfg = Config.load();
        theme = Theme.byId(cfg.theme);
        LogFile.write("Universal Client " + Config.LAUNCHER_VERSION + " запущен");

        // ── страницы ──
        versionsPage = new VersionsPage(cfg, this::onVersionChosen);
        serversPage = new ServersPage(cfg, this::joinServer, n -> {});
        shotsPage = new ScreenshotsPage(this::openUri);
        profilePage = new ProfilePage(cfg, this::logout);
        themePage = new AppearancePage(cfg, this::applyTheme, this::applyEffects);
        settingsPage = new SettingsPage(cfg, ram -> statRam.setText(Fx.formatRam(ram)), this::applyDiscord, this::openUri);
        aboutPage = buildAboutPage();
        homePage = buildHomePage();

        // ── фон ──
        glowA = glow(300, Pos.TOP_LEFT, -140, -120);
        glowB = glow(280, Pos.BOTTOM_RIGHT, 120, 110);
        drift(glowA, 50, 40, 11);
        drift(glowB, -50, -30, 13);
        particles = new ParticleCanvas();
        particles.setManaged(false);
        particles.setMouseTransparent(true);

        mainArea = new BorderPane();
        mainArea.setLeft(buildSidebar());
        mainArea.setCenter(pages);
        mainArea.setVisible(false);

        loginView = new LoginView(cfg, this::enterMain);
        loginView.setVisible(false);
        splash = new SplashView(theme);

        content = new StackPane(glowA, glowB, particles, mainArea, loginView, splash);
        content.getStyleClass().add("center");
        content.setMinSize(0, 0);
        particles.widthProperty().bind(content.widthProperty());
        particles.heightProperty().bind(content.heightProperty());
        Rectangle contentClip = new Rectangle();
        contentClip.widthProperty().bind(content.widthProperty());
        contentClip.heightProperty().bind(content.heightProperty());
        content.setClip(contentClip);

        BorderPane body = new BorderPane(content);
        body.setPadding(new Insets(TITLE_H, 0, 0, 0));

        Region accentLine = new Region();
        accentLine.getStyleClass().add("accent-line");
        accentLine.setMaxHeight(2);
        accentLine.setMouseTransparent(true);
        StackPane.setAlignment(accentLine, Pos.TOP_CENTER);
        FadeTransition lineFade = new FadeTransition(Duration.seconds(3), accentLine);
        lineFade.setFromValue(0.55);
        lineFade.setToValue(1);
        lineFade.setAutoReverse(true);
        lineFade.setCycleCount(Animation.INDEFINITE);
        lineFade.play();

        Node titleBar = buildTitleBar(stage);
        StackPane.setAlignment(titleBar, Pos.TOP_CENTER);

        window = new StackPane(body, accentLine, titleBar);   // заголовок всегда поверх
        window.getStyleClass().add("window");
        window.setPrefSize(W, H);
        window.setMinSize(W, H);
        window.setMaxSize(W, H);
        Rectangle clip = new Rectangle();
        clip.setArcWidth(32);
        clip.setArcHeight(32);
        clip.widthProperty().bind(window.widthProperty());
        clip.heightProperty().bind(window.heightProperty());
        window.setClip(clip);

        shadowBase = new Region();
        shadowBase.getStyleClass().add("window-shadow");
        shadowBase.setMaxSize(W, H);

        StackPane frame = new StackPane(shadowBase, window);    // прозрачные поля под тень
        frame.setPadding(new Insets(M));
        frame.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(frame, W + 2 * M, H + 2 * M);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        stage.initStyle(StageStyle.TRANSPARENT);
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/icon.png")));
        stage.setTitle("Universal Client");
        stage.setResizable(false);
        stage.setScene(scene);

        applyTheme(theme);
        applyEffects();
        applyVersionUI();
        updateStats();

        presence = new DiscordPresence(Config.LAUNCHER_VERSION, st -> Platform.runLater(() -> setDiscordStatus(st)));
        presence.configure(cfg.discordEnabled, effectiveAppId());

        stage.show();
        startUpdateChecks();

        // ── открытие: окно появляется, затем играет заставка ──
        frame.setOpacity(0);
        frame.setScaleX(0.94);
        frame.setScaleY(0.94);
        FadeTransition fade = new FadeTransition(Duration.millis(550), frame);
        fade.setToValue(1);
        ScaleTransition grow = new ScaleTransition(Duration.millis(550), frame);
        grow.setToX(1);
        grow.setToY(1);
        grow.setInterpolator(Interpolator.EASE_OUT);
        fade.play();
        grow.play();
        splash.play(this::afterIntro);
    }

    private void afterIntro() {
        content.getChildren().remove(splash);
        if (cfg.autoLogin && cfg.current() != null) {
            updateProfileUI();
            mainArea.setVisible(true);
            currentPage = null;
            navHome.setSelected(true);
            show(homePage, "Главная");
        } else {
            atLogin = true;
            refreshPresence();
            loginView.setVisible(true);
            loginView.refresh();
            loginView.animateIn();
        }
    }

    @Override
    public void stop() {
        if (updateTimer != null) updateTimer.shutdownNow();
        if (presence != null) presence.shutdown();
    }

    // ───────────────────────── Вход / выход ─────────────────────────

    private void enterMain(Account a) {
        atLogin = false;
        updateProfileUI();
        currentPage = null;
        navHome.setSelected(true);
        mainArea.setVisible(true);
        show(homePage, "Главная");
        FadeTransition ft = new FadeTransition(Duration.millis(300), loginView);
        ft.setToValue(0);
        ft.setOnFinished(e -> { loginView.setVisible(false); loginView.setOpacity(1); });
        ft.play();
    }

    private void logout() {
        if (playBtn.isDisabled()) return;
        atLogin = true;
        refreshPresence();
        cfg.autoLogin = false;
        cfg.save();
        loginView.refresh();
        loginView.setOpacity(1);
        loginView.setVisible(true);
        loginView.animateIn();
        mainArea.setVisible(false);
    }

    private void updateProfileUI() {
        Account a = cfg.current();
        greeting.setText(a == null ? "Привет!" : "Привет, " + a.name);
        profileCard.getChildren().clear();
        if (a == null) return;
        Label n = new Label(a.name);
        n.getStyleClass().add("row-name");
        Label t = new Label("Offline-профиль");
        t.getStyleClass().add("muted-small");
        HBox top = new HBox(10, new AvatarView(a.name, 34), new VBox(2, n, t));
        top.setAlignment(Pos.CENTER_LEFT);
        logoutBtn = new Button("Сменить профиль");
        logoutBtn.getStyleClass().add("secondary-button");
        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        logoutBtn.setOnAction(e -> logout());
        logoutBtn.setDisable(playBtn != null && playBtn.isDisabled());
        profileCard.getChildren().addAll(top, logoutBtn);
    }

    // ───────────────────────── Заголовок окна ─────────────────────────

    private Node buildTitleBar(Stage stage) {
        ImageView icon = new ImageView(new Image(getClass().getResourceAsStream("/icon.png"), 22, 22, true, true));
        Label logo = new Label("UNIVERSAL CLIENT");
        logo.getStyleClass().add("title-logo");
        Label sep = new Label("›");
        sep.getStyleClass().add("crumb-sep");
        crumb = new Label("Главная");
        crumb.getStyleClass().add("crumb");

        discordPill = new Label("○ Discord");
        discordPill.getStyleClass().add("pill");
        Label ver = new Label("v" + Config.LAUNCHER_VERSION);
        ver.getStyleClass().add("badge");

        Button min = new Button("—");
        min.getStyleClass().add("win-btn");
        min.setFocusTraversable(false);
        min.setOnAction(e -> stage.setIconified(true));

        Button close = new Button("✕");
        close.getStyleClass().addAll("win-btn", "win-close");
        close.setFocusTraversable(false);
        close.setOnAction(e -> {
            FadeTransition ft = new FadeTransition(Duration.millis(200), stage.getScene().getRoot());
            ft.setToValue(0);
            ft.setOnFinished(ev -> { if (presence != null) presence.shutdown(); Platform.exit(); System.exit(0); });
            ft.play();
        });

        HBox bar = new HBox(10, icon, logo, sep, crumb, Fx.spacer(), discordPill, ver, min, close);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("title-bar");
        bar.setMinHeight(TITLE_H);
        bar.setPrefHeight(TITLE_H);
        bar.setMaxHeight(TITLE_H);
        bar.setOnMousePressed(e -> { dragX = stage.getX() - e.getScreenX(); dragY = stage.getY() - e.getScreenY(); });
        bar.setOnMouseDragged(e -> { stage.setX(e.getScreenX() + dragX); stage.setY(e.getScreenY() + dragY); });
        return bar;
    }

    // ───────────────────────── Боковое меню ─────────────────────────

    private Node buildSidebar() {
        ToggleGroup group = new ToggleGroup();
        navHome = nav("⌂   Главная", group, () -> homePage, "Главная");
        navVersions = nav("◈   Версии", group, () -> versionsPage, "Версии");
        navServers = nav("☰   Серверы", group, () -> { serversPage.refresh(); return serversPage; }, "Серверы");
        navShots = nav("▣   Скриншоты", group, () -> { shotsPage.refresh(); return shotsPage; }, "Скриншоты");
        navProfile = nav("☺   Профиль", group, () -> { profilePage.refresh(); return profilePage; }, "Профиль");
        navTheme = nav("✦   Оформление", group, () -> themePage, "Оформление");
        navStorage = nav("▤   Хранилище", group, () -> { storagePage.refresh(); return storagePage; }, "Хранилище");
        navSettings = nav("⚙   Настройки", group, () -> settingsPage, "Настройки");
        navAbout = nav("ⓘ   О программе", group, () -> aboutPage, "О программе");
        navHome.setSelected(true);
        group.selectedToggleProperty().addListener((o, old, now) -> { if (now == null && old != null) old.setSelected(true); });

        mcBadge = new Label();
        mcBadge.getStyleClass().add("badge");

        profileCard = new VBox(10);
        profileCard.getStyleClass().add("profile-card");

        VBox items = new VBox(3, section("ИГРА"), navHome, navVersions, navServers, navShots,
                section("АККАУНТ"), navProfile,
                section("ЛАУНЧЕР"), navTheme, navStorage, navSettings, navAbout);
        // пунктов стало больше — список прокручивается, а бейдж версии и профиль остаются внизу
        ScrollPane navScroll = new ScrollPane(items);
        navScroll.setFitToWidth(true);
        navScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        navScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        navScroll.getStyleClass().add("version-scroll");
        VBox.setVgrow(navScroll, Priority.ALWAYS);

        VBox box = new VBox(3, navScroll, mcBadge, Fx.gap(4), profileCard);
        box.setPadding(new Insets(14, 14, 16, 14));
        box.setPrefWidth(228);
        box.setMinWidth(228);
        box.getStyleClass().add("sidebar");
        return box;
    }

    private static Label section(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("nav-section");
        return l;
    }

    private ToggleButton nav(String text, ToggleGroup group, Supplier<Node> page, String title) {
        ToggleButton b = new ToggleButton(text);
        b.setToggleGroup(group);
        b.setMaxWidth(Double.MAX_VALUE);
        b.getStyleClass().add("nav-button");
        b.setOnAction(e -> show(page.get(), title));
        Fx.hoverSlide(b, 4);
        return b;
    }

    private void show(Node page, String title) {
        if (page == currentPage) return;
        currentPage = page;
        pages.getChildren().setAll(page);
        Fx.appear(page, 16, 320);
        crumb.setText(title);
        refreshPresence();
        if (page == homePage) playHomeIntro();
    }

    // ───────────────────────── Главная ─────────────────────────

    private Node buildHomePage() {
        // ── герой-карточка ──
        heroDot = new Circle(4, Color.web("#34d399"));
        heroState = new Label("ГОТОВ К ЗАПУСКУ");
        heroState.getStyleClass().add("hero-state");
        HBox state = new HBox(7, heroDot, heroState);
        state.setAlignment(Pos.CENTER_LEFT);

        greeting = new Label("Привет!");
        greeting.getStyleClass().add("hero-greeting");
        Label title = new Label("UNIVERSAL CLIENT");
        title.getStyleClass().add("hero-title2");

        playBtn = new Button("▶    ИГРАТЬ");
        playBtn.getStyleClass().add("home-play");
        playBtn.setPrefSize(236, 54);
        playBtn.setOnAction(e -> onPlayClicked());
        Fx.hoverScale(playBtn, 1.05);
        playGlow = Fx.pulseGlow(playBtn, theme.a());

        versionChip = new Button();
        versionChip.getStyleClass().add("chip-button");
        versionChip.setOnAction(e -> navVersions.fire());
        Fx.hoverScale(versionChip, 1.05);

        HBox actions = new HBox(12, playBtn, versionChip);
        actions.setAlignment(Pos.CENTER_LEFT);

        bar = new ProgressBar(0);
        bar.setPrefWidth(360);
        bar.setVisible(false);
        status = new Label("");
        status.getStyleClass().add("muted");

        VBox left = new VBox(8, state, greeting, title, Fx.gap(4), actions, bar, status);
        left.setPadding(new Insets(24, 0, 18, 30));
        left.setAlignment(Pos.CENTER_LEFT);

        // кубики справа
        StackPane cubeArea = new StackPane();
        cubeArea.setPrefWidth(290);
        cubeArea.setMinWidth(290);
        CubeArt big = new CubeArt(58, theme.a(), theme.b());
        CubeArt mid = new CubeArt(20, theme.c(), theme.a());
        CubeArt small = new CubeArt(13, theme.a(), theme.c());
        mid.setTranslateX(-98);
        mid.setTranslateY(-58);
        small.setTranslateX(104);
        small.setTranslateY(52);
        cubeArea.getChildren().addAll(big, mid, small);
        cubes.add(big);
        cubes.add(mid);
        cubes.add(small);
        Fx.floatY(big, 12, 3.2, 0);
        Fx.floatY(mid, 9, 2.6, 0.4);
        Fx.floatY(small, 10, 3.8, 0.9);
        RotateTransition sway = new RotateTransition(Duration.seconds(4), big);
        sway.setFromAngle(-4);
        sway.setToAngle(4);
        sway.setAutoReverse(true);
        sway.setCycleCount(Animation.INDEFINITE);
        sway.setInterpolator(Interpolator.EASE_BOTH);
        sway.play();

        heroOrb = new Circle(170);
        heroOrb.setMouseTransparent(true);
        StackPane.setAlignment(heroOrb, Pos.TOP_RIGHT);
        heroOrb.setTranslateX(70);
        heroOrb.setTranslateY(-80);

        HBox heroRow = new HBox(left, Fx.spacer(), cubeArea);
        heroRow.setAlignment(Pos.CENTER_LEFT);
        heroCard = new StackPane(heroOrb, heroRow);
        heroCard.getStyleClass().add("hero-card");
        heroCard.setPrefHeight(250);
        heroCard.setMinHeight(250);
        heroCard.setMaxHeight(250);
        Rectangle heroClip = new Rectangle();
        heroClip.setArcWidth(44);
        heroClip.setArcHeight(44);
        heroClip.widthProperty().bind(heroCard.widthProperty());
        heroClip.heightProperty().bind(heroCard.heightProperty());
        heroCard.setClip(heroClip);

        // ── статистика ──
        HBox stats = new HBox(14,
                statCard("◈", "ВЕРСИЯ", statVersion),
                statCard("▤", "ПАМЯТЬ", statRam),
                statCard("◷", "ВРЕМЯ В ИГРЕ", statPlay),
                statCard("▶", "ЗАПУСКОВ", statLaunches));
        statRam.setText(Fx.formatRam(cfg.ramMb));

        // ── что нового + быстрый доступ ──
        Label newsTitle = new Label("Что нового");
        newsTitle.getStyleClass().add("card-title");
        VBox news = new VBox(9, newsTitle,
                newsItem("2.3.0", "Новое меню, темы, скриншоты и профиль", true),
                newsItem("2.2.1", "Discord Rich Presence — статус в профиле", false),
                newsItem("2.1.0", "Выбор версий Minecraft 1.19 — 1.21.11", false));
        news.getStyleClass().add("card");
        news.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(news, Priority.ALWAYS);
        newsCard = news;

        Label quickTitle = new Label("Быстрый доступ");
        quickTitle.getStyleClass().add("card-title");
        GridPane quickGrid = new GridPane();
        quickGrid.setHgap(8);
        quickGrid.setVgap(8);
        ColumnConstraints q1 = new ColumnConstraints();
        q1.setPercentWidth(50);
        ColumnConstraints q2 = new ColumnConstraints();
        q2.setPercentWidth(50);
        quickGrid.getColumnConstraints().addAll(q1, q2);
        quickGrid.add(quickBtn("📁  Папка игры", () -> openUri(Dirs.GAME.toUri().toString())), 0, 0);
        quickGrid.add(quickBtn("▣  Скриншоты", () -> navShots.fire()), 1, 0);
        quickGrid.add(quickBtn("◈  Версии", () -> navVersions.fire()), 0, 1);
        quickGrid.add(quickBtn("✦  Оформление", () -> navTheme.fire()), 1, 1);
        VBox quick = new VBox(10, quickTitle, quickGrid);
        quick.getStyleClass().add("card");
        quick.setPrefWidth(290);
        quick.setMinWidth(290);
        quickCard = quick;

        HBox bottom = new HBox(14, news, quick);
        bottom.setPrefHeight(150);
        bottom.setMaxHeight(150);

        VBox page = new VBox(14, heroCard, stats, bottom);
        page.setPadding(new Insets(20, 26, 16, 26));
        return page;
    }

    private Node statCard(String icon, String title, Label value) {
        Label ic = new Label(icon);
        ic.getStyleClass().add("stat-icon");
        ic.setMinSize(38, 38);
        ic.setPrefSize(38, 38);
        ic.setAlignment(Pos.CENTER);
        Label t = new Label(title);
        t.getStyleClass().add("stat-title");
        value.getStyleClass().add("stat-value");
        HBox card = new HBox(12, ic, new VBox(2, t, value));
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("stat-card");
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMinWidth(0);
        HBox.setHgrow(card, Priority.ALWAYS);
        Fx.hoverScale(card, 1.03);
        statCards.add(card);
        return card;
    }

    private Node newsItem(String ver, String text, boolean hot) {
        Label v = new Label(ver);
        v.getStyleClass().add(hot ? "tag-hot" : "tag");
        v.setMinWidth(44);
        v.setAlignment(Pos.CENTER);
        Label t = new Label(text);
        t.getStyleClass().add("news-text");
        HBox row = new HBox(10, v, t);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Button quickBtn(String text, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add("quick-btn");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(e -> action.run());
        Fx.hoverScale(b, 1.04);
        return b;
    }

    private void playHomeIntro() {
        Fx.appear(heroCard, 20, 500, 0);
        for (int i = 0; i < statCards.size(); i++) Fx.appear(statCards.get(i), 16, 400, 180 + i * 70);
        Fx.appear(newsCard, 16, 400, 480);
        Fx.appear(quickCard, 16, 400, 550);
        for (CubeArt c : cubes) {
            c.setScaleX(0.5);
            c.setScaleY(0.5);
            ScaleTransition st = new ScaleTransition(Duration.millis(520), c);
            st.setToX(1);
            st.setToY(1);
            st.setDelay(Duration.millis(260));
            st.setInterpolator(Interpolator.EASE_OUT);
            st.play();
        }
    }

    private void setHeroState(String text, Color dot) {
        heroState.setText(text);
        heroDot.setFill(dot);
    }

    private void applyVersionUI() {
        versionChip.setText("Версия " + cfg.version + "  ›");
        statVersion.setText(cfg.version);
        mcBadge.setText("Minecraft " + cfg.version);
    }

    private void updateStats() {
        statPlay.setText(Fx.formatDuration(cfg.playSeconds));
        statLaunches.setText(String.valueOf(cfg.launches));
    }

    private void onVersionChosen() {
        applyVersionUI();
        refreshPresence();
    }

    // ───────────────────────── Запуск игры ─────────────────────────

    /** Кнопка «Играть» на странице серверов: запуск игры с автоматическим входом на сервер. */
    private void joinServer(ServerEntry s) {
        if (playBtn.isDisabled()) return;
        navHome.fire();
        if (pendingUpdate != null) {
            status.setText("Сначала обновите лаунчер — нажмите «ОБНОВИТЬ»");
            return;
        }
        play(s.address);
    }

    /** join — адрес сервера для автоматического входа (null — обычный запуск). */
    private void play(final String join) {
        if (pendingUpdate != null) {
            status.setText("Сначала обновите лаунчер — нажмите «ОБНОВИТЬ»");
            return;
        }
        Account acc = cfg.current();
        if (acc == null) {
            status.setText("Сначала войдите в профиль");
            return;
        }
        final String version = cfg.version;
        playBtn.setDisable(true);
        versionChip.setDisable(true);
        if (logoutBtn != null) logoutBtn.setDisable(true);
        playBtn.setText("ЗАГРУЗКА...");
        setHeroState("ПОДГОТОВКА ИГРЫ", Color.web("#fbbf24"));
        bar.setVisible(true);
        bar.setProgress(-1);
        LogFile.write("[Launcher] Подготовка Minecraft " + version + " для " + acc.name
                + (join != null ? " (сервер " + join + ")" : ""));
        installing = true;
        gameVersion = version;
        refreshPresence();

        Task<Process> task = new Task<>() {
            @Override
            protected Process call() throws Exception {
                JsonObject json = MinecraftInstaller.install(version, (f, text) ->
                        Platform.runLater(() -> {
                            bar.setProgress(f < 0 ? ProgressBar.INDETERMINATE_PROGRESS : f);
                            status.setText(text);
                        }));
                Platform.runLater(() -> { status.setText("Применение фирменного оформления..."); bar.setProgress(-1); });
                ResourcePackInstaller.apply(cfg, version);
                Platform.runLater(() -> status.setText("Запуск игры..."));
                return GameLauncher.launch(cfg, json, LogFile::write, join);
            }
        };
        task.setOnSucceeded(e -> {
            Process p = task.getValue();
            status.setText("Игра запущена ✓");
            bar.setProgress(1);
            playBtn.setText("ИГРА ЗАПУЩЕНА");
            setHeroState("ИГРА ЗАПУЩЕНА", theme.c());
            installing = false;
            gameRunning = true;
            gameStart = System.currentTimeMillis();
            cfg.launches++;
            cfg.lastPlayed = gameStart;
            cfg.lastVersion = version;
            cfg.save();
            updateStats();
            refreshPresence();

            boolean quitAfter = false;
            if (cfg.closeOnLaunch) {
                if (presence != null && cfg.discordEnabled && effectiveAppId().matches("\\d{15,25}")) {
                    primaryStage.setIconified(true);   // остаёмся в фоне, чтобы Discord показывал статус
                    quitAfter = true;
                } else {
                    Platform.exit();
                    return;
                }
            }
            final boolean quit = quitAfter;
            Thread waiter = new Thread(() -> {
                try { p.waitFor(); } catch (InterruptedException ignored) {}
                Platform.runLater(() -> {
                    LogFile.write("[Launcher] Игра закрыта");
                    gameRunning = false;
                    cfg.playSeconds += Math.max(0, (System.currentTimeMillis() - gameStart) / 1000);
                    cfg.save();
                    updateStats();
                    refreshPresence();
                    if (quit) {
                        Platform.exit();
                        return;
                    }
                    resetPlay("Игра закрыта");
                });
            }, "game-waiter");
            waiter.setDaemon(true);
            waiter.start();
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            LogFile.write("[ERROR] " + ex);
            installing = false;
            refreshPresence();
            resetPlay("Ошибка: " + ex.getMessage());
        });
        Thread t = new Thread(task, "installer");
        t.setDaemon(true);
        t.start();
    }

    private void resetPlay(String message) {
        playBtn.setDisable(false);
        versionChip.setDisable(false);
        refreshPlayButton();
        bar.setVisible(false);
        status.setText(message);
        if (logoutBtn != null) logoutBtn.setDisable(false);
    }

    // ───────────────────────── Обновление лаунчера ─────────────────────────

    /** Проверка version.json на GitHub: при запуске и затем каждые 15 минут. */
    private void startUpdateChecks() {
        updateTimer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "update-check");
            t.setDaemon(true);
            return t;
        });
        updateTimer.scheduleWithFixedDelay(() -> {
            if (updating) return;
            Updater.Info info = Updater.check();
            if (info != null) Platform.runLater(() -> onUpdateFound(info));
        }, 1, 15 * 60, TimeUnit.SECONDS);
    }

    private void onUpdateFound(Updater.Info info) {
        if (pendingUpdate != null && pendingUpdate.version().equals(info.version())) return;
        pendingUpdate = info;
        if (!installing && !gameRunning && !updating) {
            refreshPlayButton();
            status.setText("Вышло обновление " + info.version()
                    + (info.notes().isBlank() ? "" : " — " + info.notes()));
        }
    }

    /** Кнопка на главной: «ОБНОВИТЬ», если на GitHub вышла новая версия, иначе «ИГРАТЬ». */
    private void refreshPlayButton() {
        if (playBtn == null) return;
        if (pendingUpdate != null) {
            playBtn.setText("⟳    ОБНОВИТЬ");
            setHeroState("ДОСТУПНО ОБНОВЛЕНИЕ " + pendingUpdate.version(), Color.web("#fbbf24"));
        } else {
            playBtn.setText("▶    ИГРАТЬ");
            setHeroState("ГОТОВ К ЗАПУСКУ", Color.web("#34d399"));
        }
    }

    private void onPlayClicked() {
        if (pendingUpdate != null) startUpdate();
        else play(null);
    }

    private void startUpdate() {
        if (updating || pendingUpdate == null) return;
        final Updater.Info info = pendingUpdate;
        updating = true;
        playBtn.setDisable(true);
        versionChip.setDisable(true);
        if (logoutBtn != null) logoutBtn.setDisable(true);
        playBtn.setText("СКАЧИВАНИЕ...");
        setHeroState("ОБНОВЛЕНИЕ " + info.version(), Color.web("#fbbf24"));
        bar.setVisible(true);
        bar.setProgress(-1);
        status.setText("Скачивание обновления…");
        LogFile.write("[Updater] Скачивание " + info.url());

        Task<java.nio.file.Path> task = new Task<>() {
            @Override
            protected java.nio.file.Path call() throws Exception {
                return Updater.download(info, (f, text) -> Platform.runLater(() -> {
                    bar.setProgress(f < 0 ? ProgressBar.INDETERMINATE_PROGRESS : f);
                    status.setText(text);
                }));
            }
        };
        task.setOnSucceeded(e -> {
            try {
                status.setText("Запуск установщика… лаунчер сейчас закроется");
                bar.setProgress(1);
                Updater.runInstaller(task.getValue());
                PauseTransition pause = new PauseTransition(Duration.millis(900));
                pause.setOnFinished(ev -> {
                    if (presence != null) presence.shutdown();
                    Platform.exit();
                    System.exit(0);
                });
                pause.play();
            } catch (Exception ex) {
                updateFailed(ex);
            }
        });
        task.setOnFailed(e -> updateFailed(task.getException()));
        Thread t = new Thread(task, "updater");
        t.setDaemon(true);
        t.start();
    }

    private void updateFailed(Throwable ex) {
        LogFile.write("[Updater] Ошибка обновления: " + ex);
        updating = false;
        playBtn.setDisable(false);
        versionChip.setDisable(false);
        if (logoutBtn != null) logoutBtn.setDisable(false);
        bar.setVisible(false);
        refreshPlayButton();
        status.setText("Не удалось обновить: " + (ex == null ? "неизвестная ошибка" : ex.getMessage()));
    }

    // ───────────────────────── Тема и эффекты ─────────────────────────

    private void applyTheme(Theme t) {
        theme = t;
        cfg.theme = t.id();
        cfg.save();
        window.setStyle(t.css());
        glowA.setFill(glowFill(t.a(), 0.28));
        glowB.setFill(glowFill(t.c(), 0.26));
        heroOrb.setFill(glowFill(t.a(), 0.40));
        particles.setColors(t.a(), t.light());
        if (cubes.size() == 3) {
            cubes.get(0).setColors(t.a(), t.b());
            cubes.get(1).setColors(t.c(), t.a());
            cubes.get(2).setColors(t.a(), t.c());
        }
        shadowBase.setEffect(new DropShadow(BlurType.GAUSSIAN, t.a().deriveColor(0, 1, 1, 0.40), 36, 0.08, 0, 6));
        if (playGlow != null) playGlow.setColor(t.a());
        if (loginView != null) loginView.setTheme(t);
    }

    private void applyEffects() {
        particles.setRunning(cfg.particles);
        glowA.setVisible(cfg.glows);
        glowB.setVisible(cfg.glows);
    }

    // ───────────────────────── Discord ─────────────────────────

    private void openUri(String uri) {
        getHostServices().showDocument(uri);
    }

    private String effectiveAppId() {
        String id = cfg.discordAppId == null ? "" : cfg.discordAppId.trim();
        return id.isEmpty() ? DiscordPresence.DEFAULT_APP_ID : id;
    }

    private void applyDiscord() {
        if (presence != null) presence.configure(cfg.discordEnabled, effectiveAppId());
    }

    private void setDiscordStatus(String text) {
        settingsPage.setDiscordStatus(text);
        boolean ok = text.startsWith("●");
        discordPill.setText(ok ? "● Discord" : "○ Discord");
        discordPill.getStyleClass().remove("pill-ok");
        if (ok) discordPill.getStyleClass().add("pill-ok");
    }

    private void refreshPresence() {
        if (presence == null) return;
        Account acc = cfg.current();
        DiscordPresence.Activity a;
        if (gameRunning) {
            a = new DiscordPresence.Activity("Играет в Minecraft " + gameVersion,
                    acc != null ? "Ник: " + acc.name : "Universal Client", gameStart);
        } else if (installing) {
            a = new DiscordPresence.Activity("Скачивает Minecraft " + gameVersion, "Подготовка к запуску", appStart);
        } else if (atLogin) {
            a = new DiscordPresence.Activity("Входит в профиль", "Universal Client Launcher", appStart);
        } else if (currentPage == versionsPage) {
            a = new DiscordPresence.Activity("Выбирает версию", "Сейчас: Minecraft " + cfg.version, appStart);
        } else if (currentPage == serversPage) {
            a = new DiscordPresence.Activity("Выбирает сервер", "Universal Client Launcher", appStart);
        } else if (currentPage == settingsPage || currentPage == themePage) {
            a = new DiscordPresence.Activity("Настраивает лаунчер", "Universal Client Launcher", appStart);
        } else {
            a = new DiscordPresence.Activity("В лаунчере Universal Client", "Готов к игре • Minecraft " + cfg.version, appStart);
        }
        presence.set(a);
    }

    // ───────────────────────── О программе ─────────────────────────

    private Node buildAboutPage() {
        Label h = new Label("О программе");
        h.getStyleClass().add("page-title");
        Label t1 = new Label("Universal Client Launcher " + Config.LAUNCHER_VERSION);
        t1.getStyleClass().add("accent");
        Label t2 = new Label("Папка данных: " + Dirs.ROOT
                + "\nЛоги: " + Dirs.LOGS
                + "\nРежим входа: offline (локальные профили)"
                + "\nОформление: ресурспак Universal Client");
        t2.getStyleClass().add("muted");
        t2.setWrapText(true);
        VBox card = new VBox(12, t1, t2);
        card.getStyleClass().add("card");
        card.setMaxWidth(600);
        VBox page = new VBox(18, h, card);
        page.setPadding(new Insets(30, 40, 20, 40));
        return page;
    }

    // ───────────────────────── Утилиты ─────────────────────────

    private static Circle glow(double r, Pos pos, double tx, double ty) {
        Circle circle = new Circle(r);
        circle.setMouseTransparent(true);
        StackPane.setAlignment(circle, pos);
        circle.setTranslateX(tx);
        circle.setTranslateY(ty);
        return circle;
    }

    private static RadialGradient glowFill(Color c, double alpha) {
        return new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0, c.deriveColor(0, 1, 1, alpha)),
                new Stop(1, c.deriveColor(0, 1, 1, 0)));
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
