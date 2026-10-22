package org.example.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.core.Config;
import org.example.core.ServerEntry;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Список серверов: добавление, удаление, проверка доступности и запуск игры с входом на сервер. */
public class ServersPage extends VBox {
    private static final int MAX_SERVERS = 30;
    private static final String[] COLORS = {"#8b5cf6", "#6366f1", "#0ea5e9", "#10b981", "#f59e0b", "#ec4899", "#ef4444"};
    private static final ExecutorService PINGER = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "server-ping");
        t.setDaemon(true);
        return t;
    });

    private final Config cfg;
    private final Consumer<ServerEntry> onJoin;
    private final Consumer<Integer> onCount;
    private final VBox list = new VBox(10);
    private final TextField nameField = new TextField();
    private final TextField addrField = new TextField();
    private final Label error = new Label();
    private int generation = 0;

    public ServersPage(Config cfg, Consumer<ServerEntry> onJoin, Consumer<Integer> onCount) {
        super(14);
        this.cfg = cfg;
        this.onJoin = onJoin;
        this.onCount = onCount;

        Label h = new Label("Серверы");
        h.getStyleClass().add("page-title");
        Label hint = new Label("Добавьте сервер и нажмите «Играть» — игра запустится и сама зайдёт на него");
        hint.getStyleClass().add("muted");

        Button recheck = new Button("⟳  Проверить");
        recheck.getStyleClass().add("secondary-button");
        Fx.hoverScale(recheck, 1.04);
        recheck.setOnAction(e -> refresh());
        HBox head = new HBox(10, new VBox(2, h, hint), Fx.spacer(), recheck);
        head.setAlignment(Pos.CENTER_LEFT);

        nameField.setPromptText("Название (необязательно)");
        nameField.getStyleClass().addAll("input", "uc-input", "input-sm");
        nameField.setPrefWidth(200);
        addrField.setPromptText("Адрес: play.example.com или play.example.com:25565");
        addrField.getStyleClass().addAll("input", "uc-input", "input-sm");
        HBox.setHgrow(addrField, Priority.ALWAYS);
        addrField.setOnAction(e -> add());
        Button addBtn = new Button("＋  Добавить");
        addBtn.getStyleClass().add("accent-button");
        Fx.hoverScale(addBtn, 1.04);
        addBtn.setOnAction(e -> add());
        HBox form = new HBox(10, nameField, addrField, addBtn);
        form.setAlignment(Pos.CENTER_LEFT);

        error.getStyleClass().add("error-label");
        error.setManaged(false);
        error.setVisible(false);

        ScrollPane sp = new ScrollPane(list);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.getStyleClass().add("version-scroll");
        list.setPadding(new Insets(4));
        VBox.setVgrow(sp, Priority.ALWAYS);

        getChildren().addAll(head, form, error, sp);
        setPadding(new Insets(28, 34, 18, 34));
        rebuild();
    }

    /** Перерисовать список и заново проверить доступность серверов. */
    public void refresh() {
        rebuild();
    }

    private void add() {
        String addr = addrField.getText() == null ? "" : addrField.getText().trim();
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (addr.isEmpty()) { showError("Введите адрес сервера"); return; }
        if (addr.matches(".*\\s.*")) { showError("В адресе не должно быть пробелов"); return; }
        if (addr.contains("://")) { showError("Адрес без «http://» — например play.example.com"); return; }
        if (cfg.servers.size() >= MAX_SERVERS) { showError("Можно добавить не больше " + MAX_SERVERS + " серверов"); return; }
        for (ServerEntry s : cfg.servers) {
            if (s.address != null && s.address.equalsIgnoreCase(addr)) { showError("Такой сервер уже есть в списке"); return; }
        }
        cfg.servers.add(new ServerEntry(name.isEmpty() ? addr : name, addr));
        cfg.save();
        nameField.clear();
        addrField.clear();
        error.setVisible(false);
        error.setManaged(false);
        rebuild();
    }

    private void showError(String text) {
        error.setText(text);
        error.setManaged(true);
        error.setVisible(true);
        Fx.shake(addrField);
    }

    private void rebuild() {
        generation++;
        final int gen = generation;
        list.getChildren().clear();
        onCount.accept(cfg.servers.size());

        if (cfg.servers.isEmpty()) {
            Label big = new Label("☰");
            big.setStyle("-fx-font-size: 42px; -fx-text-fill: #8b8ba7;");
            Label t = new Label("Серверов пока нет");
            t.getStyleClass().add("row-name");
            Label d = new Label("Введите адрес выше и нажмите «Добавить»");
            d.getStyleClass().add("muted");
            VBox empty = new VBox(8, big, t, d);
            empty.setAlignment(Pos.CENTER);
            empty.getStyleClass().add("card");
            empty.setPrefHeight(200);
            list.getChildren().add(empty);
            return;
        }
        for (ServerEntry s : new java.util.ArrayList<>(cfg.servers)) list.getChildren().add(row(s, gen));
    }

    private HBox row(ServerEntry s, int gen) {
        String title = s.name == null || s.name.isBlank() ? s.address : s.name;
        Label letter = new Label(title.substring(0, 1).toUpperCase());
        letter.getStyleClass().add("srv-letter");
        StackPane badge = new StackPane(letter);
        badge.setMinSize(46, 46);
        badge.setPrefSize(46, 46);
        badge.setMaxSize(46, 46);
        badge.setStyle("-fx-background-radius: 13; -fx-background-color: "
                + COLORS[Math.floorMod(title.toLowerCase().hashCode(), COLORS.length)] + ";");

        Label name = new Label(title);
        name.getStyleClass().add("row-name");
        Label addr = new Label(s.address);
        addr.getStyleClass().add("muted-small");
        Label status = new Label("… проверка");
        status.getStyleClass().addAll("srv-status", "srv-wait");

        Button play = new Button("▶  Играть");
        play.getStyleClass().add("accent-button");
        Fx.hoverScale(play, 1.05);
        play.setOnAction(e -> onJoin.accept(s));

        Button del = new Button("✕");
        del.getStyleClass().add("secondary-button");
        Fx.hoverScale(del, 1.05);
        del.setOnAction(e -> {
            cfg.servers.remove(s);
            cfg.save();
            rebuild();
        });

        HBox row = new HBox(14, badge, new VBox(3, name, addr), Fx.spacer(), status, play, del);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("list-row");

        final String address = s.address;
        PINGER.execute(() -> {
            long ms = ping(address);
            Platform.runLater(() -> {
                if (gen != generation) return;
                status.getStyleClass().removeAll("srv-wait", "srv-ok", "srv-off");
                if (ms >= 0) {
                    status.setText("● онлайн  " + ms + " мс");
                    status.getStyleClass().add("srv-ok");
                } else {
                    status.setText("○ недоступен");
                    status.getStyleClass().add("srv-off");
                }
            });
        });
        return row;
    }

    /** Время TCP-подключения в мс или -1, если сервер не отвечает. */
    private static long ping(String address) {
        String host = address;
        int port = 25565;
        int idx = address.lastIndexOf(':');
        if (idx > 0 && idx < address.length() - 1) {
            try {
                port = Integer.parseInt(address.substring(idx + 1));
                host = address.substring(0, idx);
            } catch (NumberFormatException ignored) {}
        }
        long t0 = System.nanoTime();
        try (Socket sock = new Socket()) {
            sock.connect(new InetSocketAddress(host, port), 3000);
            return Math.max(1, (System.nanoTime() - t0) / 1_000_000);
        } catch (Exception e) {
            return -1;
        }
    }
}
