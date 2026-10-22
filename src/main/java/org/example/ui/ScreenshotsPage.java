package org.example.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import org.example.core.Dirs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Галерея скриншотов из папки игры. */
public class ScreenshotsPage extends VBox {
    private final FlowPane grid = new FlowPane(14, 14);
    private final Label state = new Label();
    private final Consumer<String> opener;

    public ScreenshotsPage(Consumer<String> opener) {
        super(14);
        this.opener = opener;

        Label h = new Label("Скриншоты");
        h.getStyleClass().add("page-title");
        Label hint = new Label("Снимки из игры (клавиша F2). Нажмите на картинку, чтобы открыть");
        hint.getStyleClass().add("muted");

        Button open = new Button("📁  Открыть папку");
        open.getStyleClass().add("secondary-button");
        Fx.hoverScale(open, 1.04);
        open.setOnAction(e -> {
            try {
                Files.createDirectories(dir());
            } catch (IOException ignored) {}
            opener.accept(dir().toUri().toString());
        });
        Button refresh = new Button("⟳  Обновить");
        refresh.getStyleClass().add("secondary-button");
        Fx.hoverScale(refresh, 1.04);
        refresh.setOnAction(e -> refresh());

        state.getStyleClass().add("muted-small");
        HBox head = new HBox(10, new VBox(2, h, hint), Fx.spacer(), state, refresh, open);
        head.setAlignment(Pos.CENTER_LEFT);

        grid.setPadding(new Insets(6));
        ScrollPane sp = new ScrollPane(grid);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.getStyleClass().add("version-scroll");
        VBox.setVgrow(sp, Priority.ALWAYS);

        getChildren().addAll(head, sp);
        setPadding(new Insets(28, 34, 18, 34));
    }

    private static Path dir() {
        return Dirs.GAME.resolve("screenshots");
    }

    /** Сколько скриншотов в папке (для счётчика в меню). */
    public static int count() {
        if (!Files.isDirectory(dir())) return 0;
        try (Stream<Path> s = Files.list(dir())) {
            return (int) s.filter(p -> {
                String n = p.getFileName().toString().toLowerCase();
                return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
            }).count();
        } catch (IOException e) {
            return 0;
        }
    }

    public void refresh() {
        grid.getChildren().clear();
        List<Path> files = List.of();
        if (Files.isDirectory(dir())) {
            try (Stream<Path> s = Files.list(dir())) {
                files = s.filter(p -> {
                    String n = p.getFileName().toString().toLowerCase();
                    return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
                }).sorted(Comparator.comparingLong(ScreenshotsPage::lastModified).reversed()).limit(60).toList();
            } catch (IOException ignored) {}
        }
        if (files.isEmpty()) {
            state.setText("");
            Label big = new Label("🖼");
            big.setStyle("-fx-font-size: 42px;");
            Label t = new Label("Скриншотов пока нет");
            t.getStyleClass().add("row-name");
            Label d = new Label("Нажмите F2 в игре — снимки появятся здесь");
            d.getStyleClass().add("muted");
            VBox empty = new VBox(8, big, t, d);
            empty.setAlignment(Pos.CENTER);
            empty.getStyleClass().add("card");
            empty.setPrefSize(560, 220);
            grid.getChildren().add(empty);
            return;
        }
        for (Path f : files) grid.getChildren().add(card(f));
        state.setText("Снимков: " + files.size());
    }

    private Node card(Path f) {
        Image img = new Image(f.toUri().toString(), 214, 0, true, true, true);
        ImageView iv = new ImageView(img);
        iv.setFitWidth(214);
        iv.setPreserveRatio(true);
        StackPane thumb = new StackPane(iv);
        thumb.setMinSize(214, 120);
        thumb.setPrefSize(214, 120);
        thumb.setMaxSize(214, 120);
        thumb.setStyle("-fx-background-color: #14142a; -fx-background-radius: 14;");
        Rectangle clip = new Rectangle(214, 120);
        clip.setArcWidth(28);
        clip.setArcHeight(28);
        thumb.setClip(clip);

        String name = f.getFileName().toString();
        int dot = name.lastIndexOf('.');
        Label cap = new Label(dot > 0 ? name.substring(0, dot) : name);
        cap.getStyleClass().add("muted-small");

        VBox card = new VBox(8, thumb, cap);
        card.getStyleClass().add("shot-card");
        Fx.hoverScale(card, 1.04);
        card.setOnMouseClicked(e -> opener.accept(f.toUri().toString()));
        return card;
    }

    private static long lastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }
}
