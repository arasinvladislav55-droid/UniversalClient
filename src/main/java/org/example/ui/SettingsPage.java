package org.example.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.example.core.Config;
import org.example.core.Dirs;

import java.util.function.Consumer;

/** Настройки: память, Java, оформление игры, Discord. */
public class SettingsPage extends VBox {
    private final Label discordStatus = new Label("○ Отключено");

    public SettingsPage(Config cfg, Consumer<Integer> onRam, Runnable onDiscord, Consumer<String> opener) {
        super(14);

        Label h = new Label("Настройки");
        h.getStyleClass().add("page-title");

        // ── основное ──
        long totalMb = totalRamMb();
        int max = (int) Math.max(2048, Math.min(totalMb - 1024, 16384));
        Slider ram = new Slider(1024, max, Math.min(cfg.ramMb, max));
        ram.setBlockIncrement(512);
        ram.setMaxWidth(Double.MAX_VALUE);
        Label ramLabel = new Label(Fx.formatRam(cfg.ramMb));
        ramLabel.getStyleClass().add("accent");
        ram.valueProperty().addListener((o, a, b) -> {
            int v = (int) (Math.round(b.doubleValue() / 512.0) * 512);
            cfg.ramMb = v;
            ramLabel.setText(Fx.formatRam(v));
            onRam.accept(v);
            cfg.save();
        });
        HBox ramHead = new HBox(new Label("Оперативная память"), Fx.spacer(), ramLabel);

        TextField javaField = new TextField(cfg.javaPath);
        javaField.setPromptText("Авто (Java, на которой запущен лаунчер)");
        javaField.getStyleClass().addAll("input", "uc-input");
        javaField.textProperty().addListener((o, a, b) -> { cfg.javaPath = b; cfg.save(); });

        CheckBox pack = new CheckBox("Фирменное оформление Minecraft (меню, логотип, кнопки)");
        pack.getStyleClass().add("uc-check");
        pack.setSelected(cfg.visualPack);
        pack.selectedProperty().addListener((o, a, b) -> { cfg.visualPack = b; cfg.save(); });

        CheckBox close = new CheckBox("Закрывать лаунчер после запуска (с Discord — свернуть до конца игры)");
        close.getStyleClass().add("uc-check");
        close.setSelected(cfg.closeOnLaunch);
        close.selectedProperty().addListener((o, a, b) -> { cfg.closeOnLaunch = b; cfg.save(); });

        Button openGame = new Button("📁  Папка игры");
        openGame.getStyleClass().add("secondary-button");
        Fx.hoverScale(openGame, 1.04);
        openGame.setOnAction(e -> opener.accept(Dirs.GAME.toUri().toString()));

        Button openLogs = new Button("📄  Папка логов");
        openLogs.getStyleClass().add("secondary-button");
        Fx.hoverScale(openLogs, 1.04);
        openLogs.setOnAction(e -> opener.accept(Dirs.LOGS.toUri().toString()));

        VBox general = new VBox(14, ramHead, ram, Fx.gap(4), new Label("Путь к Java (необязательно)"), javaField,
                Fx.gap(4), pack, close, Fx.gap(4), new HBox(10, openGame, openLogs));
        general.getStyleClass().add("card");

        // ── Discord ──
        Label dTitle = new Label("Статус в Discord");
        dTitle.getStyleClass().add("accent");
        CheckBox dEnable = new CheckBox("Показывать «Играет в Universal Client» в профиле Discord");
        dEnable.getStyleClass().add("uc-check");
        dEnable.setSelected(cfg.discordEnabled);
        discordStatus.getStyleClass().add("discord-status");
        Label dHint = new Label("Работает автоматически — просто запустите Discord на этом компьютере. "
                + "Друзья увидят версию Minecraft, время игры и ваш ник.");
        dHint.getStyleClass().add("muted-small");
        dHint.setWrapText(true);
        dEnable.selectedProperty().addListener((o, a, b) -> { cfg.discordEnabled = b; cfg.save(); onDiscord.run(); });

        VBox discord = new VBox(12, dTitle, dEnable, discordStatus, dHint);
        discord.getStyleClass().add("card");

        VBox cards = new VBox(16, general, discord);
        cards.setMaxWidth(620);
        cards.setPadding(new Insets(4));
        ScrollPane sp = new ScrollPane(cards);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.getStyleClass().add("version-scroll");
        VBox.setVgrow(sp, Priority.ALWAYS);

        getChildren().addAll(h, sp);
        setPadding(new Insets(28, 36, 16, 36));
    }

    public void setDiscordStatus(String text) {
        discordStatus.setText(text);
        discordStatus.getStyleClass().remove("discord-ok");
        if (text.startsWith("●")) discordStatus.getStyleClass().add("discord-ok");
    }

    private static long totalRamMb() {
        try {
            var os = (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            return os.getTotalMemorySize() / 1048576L;
        } catch (Throwable t) {
            return 8192;
        }
    }
}
