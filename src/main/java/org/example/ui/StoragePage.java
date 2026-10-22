package org.example.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.example.core.Dirs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Хранилище: сколько места занимают папки лаунчера, быстрый переход к ним и очистка логов. */
public class StoragePage extends VBox {
    private record Item(String title, String hint, Path dir) {}

    private static final Item[] ITEMS = {
            new Item("Версии Minecraft", "Клиенты и natives", Dirs.VERSIONS),
            new Item("Библиотеки", "Библиотеки игры", Dirs.LIBRARIES),
            new Item("Ресурсы (assets)", "Звуки и языки", Dirs.ASSETS),
            new Item("Папка игры", "Миры, скриншоты, настройки", Dirs.GAME),
            new Item("Логи", "Журналы лаунчера и игры", Dirs.LOGS),
    };

    private final Consumer<String> opener;
    private final VBox rows = new VBox(10);
    private final Label total = new Label();
    private int generation = 0;

    public StoragePage(Consumer<String> opener) {
        super(14);
        this.opener = opener;

        Label h = new Label("Хранилище");
        h.getStyleClass().add("page-title");
        Label hint = new Label("Сколько места занимают данные лаунчера на диске");
        hint.getStyleClass().add("muted");

        Button clear = new Button("🗑  Очистить логи");
        clear.getStyleClass().add("secondary-button");
        Fx.hoverScale(clear, 1.04);
        clear.setOnAction(e -> {
            clearLogs();
            refresh();
        });
        Button reload = new Button("⟳  Пересчитать");
        reload.getStyleClass().add("secondary-button");
        Fx.hoverScale(reload, 1.04);
        reload.setOnAction(e -> refresh());

        total.getStyleClass().add("accent");
        HBox head = new HBox(10, new VBox(2, h, hint), Fx.spacer(), total, reload, clear);
        head.setAlignment(Pos.CENTER_LEFT);

        rows.setPadding(new Insets(4));
        ScrollPane sp = new ScrollPane(rows);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.getStyleClass().add("version-scroll");
        VBox.setVgrow(sp, Priority.ALWAYS);

        getChildren().addAll(head, sp);
        setPadding(new Insets(28, 34, 18, 34));
    }

    /** Пересчитать размеры папок (в фоне, чтобы не подвисал интерфейс). */
    public void refresh() {
        final int gen = ++generation;
        total.setText("Подсчёт…");
        Thread t = new Thread(() -> {
            long[] sizes = new long[ITEMS.length];
            long sum = 0;
            for (int i = 0; i < ITEMS.length; i++) {
                sizes[i] = size(ITEMS[i].dir());
                sum += sizes[i];
            }
            final long all = sum;
            Platform.runLater(() -> {
                if (gen != generation) return;
                rows.getChildren().clear();
                for (int i = 0; i < ITEMS.length; i++) rows.getChildren().add(row(ITEMS[i], sizes[i]));
                total.setText("Всего: " + format(all));
            });
        }, "storage-scan");
        t.setDaemon(true);
        t.start();
    }

    private HBox row(Item item, long bytes) {
        Label name = new Label(item.title());
        name.getStyleClass().add("row-name");
        Label path = new Label(item.hint() + "  •  " + item.dir());
        path.getStyleClass().add("muted-small");
        Label size = new Label(format(bytes));
        size.getStyleClass().add("accent");

        Button open = new Button("📁  Открыть");
        open.getStyleClass().add("secondary-button");
        Fx.hoverScale(open, 1.04);
        open.setOnAction(e -> {
            try {
                Files.createDirectories(item.dir());
            } catch (IOException ignored) {}
            opener.accept(item.dir().toUri().toString());
        });

        HBox row = new HBox(14, new VBox(3, name, path), Fx.spacer(), size, open);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("list-row");
        return row;
    }

    private static void clearLogs() {
        if (!Files.isDirectory(Dirs.LOGS)) return;
        try (Stream<Path> s = Files.list(Dirs.LOGS)) {
            s.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().equals("launcher.log"))   // он сейчас открыт лаунчером
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {}
    }

    private static long size(Path dir) {
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile).mapToLong(p -> {
                try { return Files.size(p); } catch (IOException e) { return 0; }
            }).sum();
        } catch (IOException e) {
            return 0;
        }
    }

    private static String format(long b) {
        if (b < 1024) return b + " Б";
        if (b < 1024L * 1024) return String.format("%.0f КБ", b / 1024.0);
        if (b < 1024L * 1024 * 1024) return String.format("%.1f МБ", b / 1048576.0);
        return String.format("%.2f ГБ", b / 1073741824.0);
    }
}
