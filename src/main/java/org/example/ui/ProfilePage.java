package org.example.ui;

import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.example.core.Account;
import org.example.core.Config;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Профиль игрока и статистика. */
public class ProfilePage extends VBox {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final Config cfg;
    private final Runnable onLogout;
    private final HBox body = new HBox(18);

    public ProfilePage(Config cfg, Runnable onLogout) {
        super(16);
        this.cfg = cfg;
        this.onLogout = onLogout;
        Label h = new Label("Профиль");
        h.getStyleClass().add("page-title");
        getChildren().addAll(h, body);
        setPadding(new Insets(28, 36, 18, 36));
    }

    public void refresh() {
        body.getChildren().clear();
        Account a = cfg.current();
        if (a == null) {
            body.getChildren().add(new Label("Профиль не выбран"));
            return;
        }

        // ── левая карточка ──
        AvatarView av = new AvatarView(a.name, 96);
        Label name = new Label(a.name);
        name.getStyleClass().add("profile-name");
        Label type = new Label("Offline-профиль");
        type.getStyleClass().add("muted");
        Label uuid = new Label(a.uuid);
        uuid.getStyleClass().add("muted-small");
        uuid.setWrapText(true);
        uuid.setAlignment(Pos.CENTER);

        Button copy = new Button("Скопировать UUID");
        copy.getStyleClass().add("secondary-button");
        copy.setMaxWidth(Double.MAX_VALUE);
        copy.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(a.uuid);
            Clipboard.getSystemClipboard().setContent(cc);
            copy.setText("✓ Скопировано");
            PauseTransition p = new PauseTransition(Duration.millis(1500));
            p.setOnFinished(ev -> copy.setText("Скопировать UUID"));
            p.play();
        });
        Button out = new Button("Сменить профиль");
        out.getStyleClass().add("secondary-button");
        out.setMaxWidth(Double.MAX_VALUE);
        out.setOnAction(e -> onLogout.run());

        VBox left = new VBox(10, av, name, type, Fx.gap(2), uuid, Fx.gap(6), copy, out);
        left.setAlignment(Pos.CENTER);
        left.getStyleClass().add("card");
        left.setPrefWidth(270);
        left.setMinWidth(270);
        left.setMaxWidth(270);

        // ── статистика ──
        String last = cfg.lastPlayed == 0 ? "—"
                : FMT.format(Instant.ofEpochMilli(cfg.lastPlayed).atZone(ZoneId.systemDefault()));
        GridPane stats = new GridPane();
        stats.setHgap(12);
        stats.setVgap(12);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(50);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(50);
        stats.getColumnConstraints().addAll(c1, c2);
        stats.add(tile("ЗАПУСКОВ ИГРЫ", String.valueOf(cfg.launches)), 0, 0);
        stats.add(tile("ВРЕМЯ В ИГРЕ", Fx.formatDuration(cfg.playSeconds)), 1, 0);
        stats.add(tile("ПОСЛЕДНЯЯ ИГРА", last), 0, 1);
        stats.add(tile("ПОСЛЕДНЯЯ ВЕРСИЯ", cfg.lastVersion.isEmpty() ? "—" : cfg.lastVersion), 1, 1);

        // ── список профилей ──
        Label listTitle = new Label("Профили на этом компьютере");
        listTitle.getStyleClass().add("card-title");
        VBox list = new VBox(8, listTitle);
        int shown = 0;
        for (Account acc : cfg.accounts) {
            if (shown++ >= 4) break;
            Label n = new Label(acc.name);
            n.getStyleClass().add("row-name");
            HBox row = new HBox(10, new AvatarView(acc.name, 28), n, Fx.spacer());
            if (acc.name.equalsIgnoreCase(a.name)) {
                Label cur = new Label("ТЕКУЩИЙ");
                cur.getStyleClass().add("tag-hot");
                row.getChildren().add(cur);
            }
            row.setAlignment(Pos.CENTER_LEFT);
            list.getChildren().add(row);
        }
        list.getStyleClass().add("card");

        VBox right = new VBox(14, stats, list);
        HBox.setHgrow(right, Priority.ALWAYS);
        body.getChildren().addAll(left, right);
        Fx.appear(left, 16, 380, 0);
        Fx.appear(right, 16, 380, 90);
    }

    private static VBox tile(String title, String value) {
        Label t = new Label(title);
        t.getStyleClass().add("stat-title");
        Label v = new Label(value);
        v.getStyleClass().add("stat-value-big");
        VBox box = new VBox(6, t, v);
        box.getStyleClass().add("stat-card");
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }
}
