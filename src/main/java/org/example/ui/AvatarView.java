package org.example.ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.Random;

/** Пиксельный аватар (identicon 8x8), уникальный для каждого ника. */
public class AvatarView extends Canvas {
    public AvatarView(String name, double size) {
        super(size, size);
        Random r = new Random(name.toLowerCase().hashCode() * 31L + 7);
        double hue = r.nextInt(360);
        Color fg = Color.hsb(hue, 0.60, 1.0);
        Color bg = Color.hsb(hue, 0.55, 0.22);
        GraphicsContext g = getGraphicsContext2D();
        g.setFill(bg);
        g.fillRect(0, 0, size, size);
        double px = size / 8.0;
        g.setFill(fg);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 4; x++) {
                if (r.nextDouble() < 0.52) {
                    g.fillRect(x * px, y * px, px, px);
                    g.fillRect((7 - x) * px, y * px, px, px);
                }
            }
        }
        Rectangle clip = new Rectangle(size, size);
        clip.setArcWidth(size * 0.35);
        clip.setArcHeight(size * 0.35);
        setClip(clip);
    }
}
