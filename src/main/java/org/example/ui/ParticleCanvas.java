package org.example.ui;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.Random;

/** Анимированный фон: плавающие частицы, соединённые линиями. */
public class ParticleCanvas extends Canvas {
    private static final int N = 70;
    private final double[] x = new double[N], y = new double[N], vx = new double[N], vy = new double[N], r = new double[N];
    private final AnimationTimer timer;
    private Color lineColor = Color.web("#8b5cf6");
    private Color dotColor = Color.web("#a78bfa");

    public ParticleCanvas() {
        Random rnd = new Random();
        for (int i = 0; i < N; i++) {
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

    public void setRunning(boolean run) {
        if (run) timer.start(); else timer.stop();
        setVisible(run);
    }

    private void draw() {
        double w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        GraphicsContext g = getGraphicsContext2D();
        g.clearRect(0, 0, w, h);
        for (int i = 0; i < N; i++) {
            x[i] += vx[i]; y[i] += vy[i];
            if (x[i] < 0) x[i] = w; else if (x[i] > w) x[i] = 0;
            if (y[i] < 0) y[i] = h; else if (y[i] > h) y[i] = 0;
        }
        g.setLineWidth(1);
        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                double d = Math.hypot(x[i] - x[j], y[i] - y[j]);
                if (d < 120) {
                    g.setStroke(new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), (1 - d / 120) * 0.25));
                    g.strokeLine(x[i], y[i], x[j], y[j]);
                }
            }
        }
        g.setFill(new Color(dotColor.getRed(), dotColor.getGreen(), dotColor.getBlue(), 0.65));
        for (int i = 0; i < N; i++) g.fillOval(x[i], y[i], r[i], r[i]);
    }
}
