package org.example.ui;

import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/** Анимация открытия лаунчера: кольца, пиксельный логотип, буквы, полоса загрузки. */
public class SplashView extends StackPane {
    private static final String[] PIXEL_U = {"X...X", "X...X", "X...X", "X...X", "X...X", ".XXX."};
    private static final String WORD = "UNIVERSAL";

    private final List<Animation> loops = new ArrayList<>();
    private final List<Region> cells = new ArrayList<>();
    private final List<Label> letters = new ArrayList<>();
    private final List<Circle> rings = new ArrayList<>();
    private final Label sub = new Label("C L I E N T");
    private final Label status = new Label();
    private final Rectangle fill = new Rectangle(0, 4);

    public SplashView(Theme theme) {
        setStyle("-fx-background-color: #07070d;");
        setAlignment(Pos.CENTER);

        for (int i = 0; i < 3; i++) {
            Circle c = new Circle(70);
            c.setFill(Color.TRANSPARENT);
            c.setStroke(theme.a());
            c.setStrokeWidth(2);
            c.setOpacity(0);
            c.setMouseTransparent(true);
            rings.add(c);
            getChildren().add(c);
        }

        GridPane mark = new GridPane();
        mark.setHgap(5);
        mark.setVgap(5);
        mark.setMaxWidth(Region.USE_PREF_SIZE);
        for (int r = 0; r < PIXEL_U.length; r++) {
            for (int c = 0; c < PIXEL_U[r].length(); c++) {
                if (PIXEL_U[r].charAt(c) != 'X') continue;
                Region cell = new Region();
                cell.setMinSize(22, 22);
                cell.setPrefSize(22, 22);
                Color col = theme.a().interpolate(theme.c(), (r + c) / 9.0);
                cell.setStyle("-fx-background-color: " + Theme.hex(col) + "; -fx-background-radius: 5;");
                cell.setEffect(new DropShadow(12, col.deriveColor(0, 1, 1, 0.7)));
                mark.add(cell, c, r);
                cells.add(cell);
            }
        }

        HBox word = new HBox(3);
        word.setAlignment(Pos.CENTER);
        for (int i = 0; i < WORD.length(); i++) {
            Label l = new Label(String.valueOf(WORD.charAt(i)));
            Color col = theme.a().interpolate(theme.c(), i / (WORD.length() - 1.0));
            l.setStyle("-fx-font-size: 42px; -fx-font-weight: 900; -fx-text-fill: " + Theme.hex(col) + ";");
            l.setOpacity(0);
            letters.add(l);
            word.getChildren().add(l);
        }
        sub.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #7c7c9a;");
        sub.setOpacity(0);

        Rectangle track = new Rectangle(260, 4);
        track.setArcWidth(4);
        track.setArcHeight(4);
        track.setFill(Color.web("#1c1c2e"));
        fill.setArcWidth(4);
        fill.setArcHeight(4);
        fill.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, theme.a()), new Stop(1, theme.c())));
        StackPane bar = new StackPane(track, fill);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMaxSize(260, 4);
        status.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b6b88;");

        VBox box = new VBox(12, mark, Fx.gap(6), word, sub, Fx.gap(14), bar, status);
        box.setAlignment(Pos.CENTER);
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        getChildren().add(box);
    }

    public void play(Runnable onDone) {
        // расходящиеся кольца
        for (int i = 0; i < rings.size(); i++) {
            Circle c = rings.get(i);
            ScaleTransition st = new ScaleTransition(Duration.millis(2000), c);
            st.setFromX(0.4);
            st.setFromY(0.4);
            st.setToX(3.2);
            st.setToY(3.2);
            FadeTransition ft = new FadeTransition(Duration.millis(2000), c);
            ft.setFromValue(0.55);
            ft.setToValue(0);
            ParallelTransition pt = new ParallelTransition(st, ft);
            pt.setDelay(Duration.millis(i * 650L));
            pt.setCycleCount(Animation.INDEFINITE);
            pt.play();
            loops.add(pt);
        }

        // пиксельный логотип собирается
        for (int i = 0; i < cells.size(); i++) {
            Region cell = cells.get(i);
            cell.setScaleX(0);
            cell.setScaleY(0);
            ScaleTransition st = new ScaleTransition(Duration.millis(260), cell);
            st.setToX(1);
            st.setToY(1);
            st.setDelay(Duration.millis(200 + i * 50L));
            st.setInterpolator(Interpolator.EASE_OUT);
            st.play();
        }

        // буквы названия
        for (int i = 0; i < letters.size(); i++) Fx.appear(letters.get(i), 16, 380, 750 + i * 60);
        Fx.appear(sub, 8, 500, 1500);

        // полоса загрузки и подписи
        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(fill.widthProperty(), 0)),
                new KeyFrame(Duration.millis(2000), new KeyValue(fill.widthProperty(), 260, Interpolator.EASE_BOTH)));
        tl.setDelay(Duration.millis(300));
        tl.play();
        Timeline tx = new Timeline(
                new KeyFrame(Duration.millis(300), e -> status.setText("Инициализация...")),
                new KeyFrame(Duration.millis(900), e -> status.setText("Загрузка ресурсов...")),
                new KeyFrame(Duration.millis(1500), e -> status.setText("Подготовка интерфейса...")),
                new KeyFrame(Duration.millis(2150), e -> status.setText("Готово")));
        tx.play();

        PauseTransition wait = new PauseTransition(Duration.millis(2650));
        wait.setOnFinished(e -> exit(onDone));
        wait.play();
    }

    private void exit(Runnable onDone) {
        ScaleTransition st = new ScaleTransition(Duration.millis(450), this);
        st.setToX(1.06);
        st.setToY(1.06);
        FadeTransition ft = new FadeTransition(Duration.millis(450), this);
        ft.setToValue(0);
        ParallelTransition pt = new ParallelTransition(st, ft);
        pt.setOnFinished(e -> {
            for (Animation a : loops) a.stop();
            onDone.run();
        });
        pt.play();
    }
}
