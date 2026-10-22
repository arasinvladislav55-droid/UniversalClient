package org.example.ui;

import javafx.scene.Group;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.StrokeLineJoin;

/** Изометрический «майнкрафтовский» кубик для оформления главной страницы. */
public class CubeArt extends Group {
    private final Polygon top = new Polygon();
    private final Polygon left = new Polygon();
    private final Polygon right = new Polygon();
    private final DropShadow glow = new DropShadow(36, Color.web("#8b5cf6", 0.6));

    public CubeArt(double s, Color a, Color b) {
        double dx = s * 0.866, h = s * 0.5;
        double[] t = {0, -s, dx, -h, 0, 0, -dx, -h};
        double[] l = {-dx, -h, 0, 0, 0, s, -dx, s - h};
        double[] r = {0, 0, dx, -h, dx, s - h, 0, s};
        shape(top, t);
        shape(left, l);
        shape(right, r);
        Group grids = new Group();
        grid(grids, t, 4);
        grid(grids, l, 4);
        grid(grids, r, 4);
        grids.setMouseTransparent(true);
        getChildren().addAll(left, right, top, grids);
        setEffect(glow);
        setColors(a, b);
    }

    public void setColors(Color a, Color b) {
        top.setFill(a.interpolate(Color.WHITE, 0.45));
        left.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE, new Stop(0, a), new Stop(1, b)));
        right.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, b.darker()), new Stop(1, b.darker().darker())));
        glow.setColor(a.deriveColor(0, 1, 1, 0.6));
    }

    private static void shape(Polygon p, double[] pts) {
        for (double v : pts) p.getPoints().add(v);
        p.setStroke(Color.web("#ffffff", 0.38));
        p.setStrokeWidth(1.2);
        p.setStrokeLineJoin(StrokeLineJoin.ROUND);
    }

    /** Сетка «пикселей» на грани (параллелограмм A-B-C-D). */
    private static void grid(Group g, double[] p, int n) {
        for (int i = 1; i < n; i++) {
            double t = i / (double) n;
            g.getChildren().add(line(lerp(p[0], p[2], t), lerp(p[1], p[3], t), lerp(p[6], p[4], t), lerp(p[7], p[5], t)));
            g.getChildren().add(line(lerp(p[0], p[6], t), lerp(p[1], p[7], t), lerp(p[2], p[4], t), lerp(p[3], p[5], t)));
        }
    }

    private static Line line(double x1, double y1, double x2, double y2) {
        Line l = new Line(x1, y1, x2, y2);
        l.setStroke(Color.web("#ffffff", 0.14));
        l.setStrokeWidth(1);
        return l;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
