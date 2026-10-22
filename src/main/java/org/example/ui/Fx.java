package org.example.ui;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/** Общие анимации и мелкие помощники для UI. */
final class Fx {
    private Fx() {}

    static Region gap(double h) {
        Region r = new Region();
        r.setMinHeight(h);
        r.setPrefHeight(h);
        return r;
    }

    static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    static void hoverScale(Node n, double to) {
        n.hoverProperty().addListener((o, a, hover) -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(150), n);
            st.setToX(hover ? to : 1);
            st.setToY(hover ? to : 1);
            st.play();
        });
    }

    static DropShadow pulseGlow(Node n, Color color) {
        DropShadow glow = new DropShadow(20, color);
        n.setEffect(glow);
        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(glow.radiusProperty(), 14)),
                new KeyFrame(Duration.seconds(1.5), new KeyValue(glow.radiusProperty(), 38, Interpolator.EASE_BOTH)));
        tl.setAutoReverse(true);
        tl.setCycleCount(Animation.INDEFINITE);
        tl.play();
        return glow;
    }

    /** Сдвиг вправо при наведении (для пунктов меню). */
    static void hoverSlide(Node n, double dx) {
        n.hoverProperty().addListener((o, a, hover) -> {
            TranslateTransition tt = new TranslateTransition(Duration.millis(140), n);
            tt.setToX(hover ? dx : 0);
            tt.play();
        });
    }

    /** Плавное «парение» вверх-вниз. */
    static void floatY(Node n, double by, double seconds, double delaySeconds) {
        TranslateTransition tt = new TranslateTransition(Duration.seconds(seconds), n);
        tt.setByY(by);
        tt.setAutoReverse(true);
        tt.setCycleCount(Animation.INDEFINITE);
        tt.setInterpolator(Interpolator.EASE_BOTH);
        tt.setDelay(Duration.seconds(delaySeconds));
        tt.play();
    }

    static String formatDuration(long sec) {
        long m = sec / 60;
        if (m < 1) return sec <= 0 ? "0 мин" : "< 1 мин";
        long h = m / 60;
        m %= 60;
        return h > 0 ? h + " ч " + String.format("%02d", m) + " мин" : m + " мин";
    }

    static String formatRam(int mb) {
        return String.format("%.1f GB", mb / 1024.0);
    }

    static void shake(Node n) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(50), n);
        tt.setByX(8);
        tt.setCycleCount(6);
        tt.setAutoReverse(true);
        tt.setOnFinished(e -> n.setTranslateX(0));
        tt.play();
    }

    /** Плавное появление снизу вверх с задержкой. */
    static void appear(Node n, double fromY, int ms, int delayMs) {
        n.setOpacity(0);
        n.setTranslateY(fromY);
        FadeTransition ft = new FadeTransition(Duration.millis(ms), n);
        ft.setToValue(1);
        ft.setDelay(Duration.millis(delayMs));
        TranslateTransition tt = new TranslateTransition(Duration.millis(ms), n);
        tt.setToY(0);
        tt.setDelay(Duration.millis(delayMs));
        tt.setInterpolator(Interpolator.EASE_OUT);
        ft.play();
        tt.play();
    }

    static void appear(Node n, double fromY, int ms) {
        appear(n, fromY, ms, 0);
    }

    /** Боковой «выезд» панели. */
    static void slide(Node n, double fromX, int ms, int delayMs) {
        n.setOpacity(0);
        n.setTranslateX(fromX);
        FadeTransition ft = new FadeTransition(Duration.millis(ms), n);
        ft.setToValue(1);
        ft.setDelay(Duration.millis(delayMs));
        TranslateTransition tt = new TranslateTransition(Duration.millis(ms), n);
        tt.setToX(0);
        tt.setDelay(Duration.millis(delayMs));
        tt.setInterpolator(Interpolator.EASE_OUT);
        ft.play();
        tt.play();
    }
}
