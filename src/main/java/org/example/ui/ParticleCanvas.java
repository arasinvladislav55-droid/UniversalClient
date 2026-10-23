package org.example.ui;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.Random;

/** Анимированный фон: плавающие частицы, соединённые линиями. */
public class ParticleCanvas extends Canvas {
    public static final int MAX = 120;
    private static final double LINK = 120, LINK2 = LINK * LINK;

    private final double[] x = new double[MAX], y = new double[MAX], vx = new double[MAX], vy = new double[MAX], r = new double[MAX];
    private final AnimationTimer timer;
    private int count = 70;
    private Color lineColor = Color.web("#8b5cf6");
    private Color dotColor = Color.web("#a78bfa");

    public ParticleCanvas() {
        Random rnd = new Random();
        for (int i = 0; i < MAX; i++) {
            x[i] = rnd.nextDouble() * 900;
            y[i] = rnd.nextDouble() * 560;
            vx[i] = (rnd.nextDouble() - 0.5) * 0.5;
            vy[i] = (rnd.nextDouble() - 0.5) * 0.5;
            r[i] = 1.2 + rnd.nextDouble() * 2.0;
        }
        timer = new AnimationTimer() {
            @Override public void handle(long now) { draw(); }
        };
        timer.start();
    }

    public void setColors(Color line, Color dot) {
        this.lineColor = line;
        this.dotColor = dot;
    }

    /** Сколько частиц рисовать (10…120). */
    public void setCount(int n) {
        this.count = Math.max(10, Math.min(MAX, n));
    }

    public void setRunning(boolean run) {
        if (run) timer.start(); else timer.stop();
        setVisible(run);
    }

    private void draw() {
        double w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        GraphicsContext g = getGraphicsContext2D();
        g.clearRect(0, 0, w, h);
        final int n = count;
        for (int i = 0; i < n; i++) {
            x[i] += vx[i]; y[i] += vy[i];
            if (x[i] < 0) x[i] = w; else if (x[i] > w) x[i] = 0;
            if (y[i] < 0) y[i] = h; else if (y[i] > h) y[i] = 0;
        }
        // линии: цвет задаётся один раз, прозрачность — через globalAlpha (без создания объектов на каждый кадр)
        g.setStroke(lineColor);
        g.setLineWidth(1);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double dx = x[i] - x[j], dy = y[i] - y[j];
                double d2 = dx * dx + dy * dy;
                if (d2 < LINK2) {
                    g.setGlobalAlpha((1 - Math.sqrt(d2) / LINK) * 0.25);
                    g.strokeLine(x[i], y[i], x[j], y[j]);
                }
            }
        }
        g.setGlobalAlpha(0.65);
        g.setFill(dotColor);
        for (int i = 0; i < n; i++) g.fillOval(x[i], y[i], r[i], r[i]);
        g.setGlobalAlpha(1);
    }
}
