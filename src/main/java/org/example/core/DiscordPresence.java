package org.example.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Discord Rich Presence без внешних библиотек: общение с запущенным Discord через IPC
 * (Windows: именованный канал \\.\pipe\discord-ipc-N, Linux/macOS: unix-сокет).
 * Все операции выполняются в одном фоновом потоке.
 */
public final class DiscordPresence {
    /**
     * Application ID приложения «Universal Client» в Discord Developer Portal.
     * Это публичный идентификатор (не секрет): он одинаков для всех пользователей лаунчера,
     * поэтому статус работает «из коробки», без каких-либо настроек.
     */
    public static final String DEFAULT_APP_ID = "1562097837540646942";

    public record Activity(String details, String state, long startMillis) {}

    private record Frame(int op, String json) {}

    private static final Gson GSON = new Gson();

    private final ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "discord-rpc");
        t.setDaemon(true);
        return t;
    });
    private final String launcherVersion;
    private final Consumer<String> statusListener;

    // состояние — только из потока ex
    private boolean enabled;
    private String appId = "";
    private Activity desired;
    private String sentKey = "";
    private long lastSend;
    private Pipe pipe;
    private String userName = "";

    public DiscordPresence(String launcherVersion, Consumer<String> statusListener) {
        this.launcherVersion = launcherVersion;
        this.statusListener = statusListener;
        ex.scheduleWithFixedDelay(this::tick, 3, 10, TimeUnit.SECONDS);
    }

    public void configure(boolean enabled, String appId) {
        ex.execute(() -> {
            String id = appId == null ? "" : appId.trim();
            boolean changed = this.enabled != enabled || !this.appId.equals(id);
            this.enabled = enabled;
            this.appId = id;
            if (changed) {
                disconnect();
            }
            tick();
        });
    }

    public void set(Activity a) {
        ex.execute(() -> {
            desired = a;
            tick();
        });
    }

    /** Очищает статус и закрывает соединение (вызывать при выходе). */
    public void shutdown() {
        try {
            ex.submit(() -> {
                try {
                    if (pipe != null && enabled) sendActivity(null);
                } catch (Exception ignored) {}
                disconnect();
            }).get(1500, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {}
        ex.shutdownNow();
    }

    // ───────────────────────── логика ─────────────────────────

    private void tick() {
        try {
            if (!enabled) {
                disconnect();
                status("○ Отключено");
                return;
            }
            if (!appId.matches("\\d{15,25}")) {
                disconnect();
                status("○ Статус недоступен");
                return;
            }
            if (pipe == null && !connect()) {
                status("○ Discord не запущен");
                return;
            }
            String key = desired == null ? "-" : desired.details() + "|" + desired.state() + "|" + desired.startMillis();
            long now = System.currentTimeMillis();
            boolean changed = !key.equals(sentKey);
            if (changed && now - lastSend < 4000) {                // лимит Discord: 5 обновлений / 20 сек
                ex.schedule(this::tick, 4000 - (now - lastSend), TimeUnit.MILLISECONDS);
                return;
            }
            if (changed || now - lastSend > 30_000) {              // + «пульс», чтобы заметить разрыв
                sendActivity(desired);
                sentKey = key;
                lastSend = System.currentTimeMillis();
            }
        } catch (Exception e) {
            LogFile.write("[Discord] " + e);
            disconnect();
            status("○ " + (e.getMessage() == null ? "Нет связи с Discord" : e.getMessage()));
        }
    }

    private boolean connect() throws IOException {
        Pipe p = openPipe();
        if (p == null) return false;
        pipe = p;
        writeFrame(0, "{\"v\":1,\"client_id\":\"" + appId + "\"}");
        String user = "";
        Frame f = readFrame(2500);
        if (f != null) {
            if (f.op() == 2) throw new IOException("Discord отклонил Application ID");
            try {
                JsonObject o = JsonParser.parseString(f.json()).getAsJsonObject();
                if (o.has("data") && o.getAsJsonObject("data").has("user")) {
                    JsonObject u = o.getAsJsonObject("data").getAsJsonObject("user");
                    if (u.has("global_name") && !u.get("global_name").isJsonNull()) user = u.get("global_name").getAsString();
                    else if (u.has("username")) user = u.get("username").getAsString();
                }
            } catch (Exception ignored) {}
        }
        sentKey = "";
        lastSend = 0;
        userName = user;
        status("● Подключено" + who());
        return true;
    }

    private void disconnect() {
        if (pipe != null) {
            pipe.close();
            pipe = null;
        }
        sentKey = "";
    }

    private void sendActivity(Activity a) throws IOException {
        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());
        if (a != null) {
            JsonObject act = new JsonObject();
            act.addProperty("details", clip(a.details()));
            if (a.state() != null && a.state().length() >= 2) act.addProperty("state", clip(a.state()));
            JsonObject ts = new JsonObject();
            ts.addProperty("start", a.startMillis() / 1000);
            act.add("timestamps", ts);
            JsonObject assets = new JsonObject();
            assets.addProperty("large_image", "logo");
            assets.addProperty("large_text", "Universal Client " + launcherVersion);
            act.add("assets", assets);
            args.add("activity", act);
        }
        JsonObject cmd = new JsonObject();
        cmd.addProperty("cmd", "SET_ACTIVITY");
        cmd.add("args", args);
        cmd.addProperty("nonce", UUID.randomUUID().toString());
        writeFrame(1, GSON.toJson(cmd));

        Frame r = readFrame(1500);
        if (r == null) {
            LogFile.write("[Discord] нет ответа на SET_ACTIVITY");
            if (a != null) status("● Подключено" + who() + " (Discord не ответил)");
            return;
        }
        LogFile.write("[Discord] ответ: " + r.json());
        if (r.op() == 2) throw new IOException("Discord закрыл соединение");
        try {
            JsonObject o = JsonParser.parseString(r.json()).getAsJsonObject();
            if (o.has("evt") && !o.get("evt").isJsonNull() && "ERROR".equals(o.get("evt").getAsString())) {
                String msg = o.has("data") && o.getAsJsonObject("data").has("message")
                        ? o.getAsJsonObject("data").get("message").getAsString() : "неизвестная ошибка";
                throw new IOException("Ошибка Discord: " + msg);
            }
        } catch (IllegalStateException | ClassCastException ignored) {}
        if (a != null) status("● Подключено" + who() + " • статус обновлён");
    }

    private String who() {
        return userName.isEmpty() ? "" : " как " + userName;
    }

    private static String clip(String s) {
        if (s == null || s.length() < 2) return "  ";
        return s.length() > 120 ? s.substring(0, 120) : s;
    }

    private void status(String s) {
        if (statusListener != null) statusListener.accept(s);
    }

    // ───────────────────────── протокол ─────────────────────────

    private void writeFrame(int op, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer bb = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(op).putInt(body.length).put(body);
        pipe.write(bb.array());
    }

    private Frame readFrame(long timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        byte[] head = readFully(8, deadline);
        if (head == null) return null;
        ByteBuffer bb = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        int op = bb.getInt();
        int len = bb.getInt();
        if (len < 0 || len > 1_000_000) throw new IOException("Некорректный ответ Discord");
        byte[] body = readFully(len, deadline + 2000);
        if (body == null) return null;
        return new Frame(op, new String(body, StandardCharsets.UTF_8));
    }

    private byte[] readFully(int n, long deadline) throws IOException {
        byte[] buf = new byte[n];
        int got = 0;
        while (got < n) {
            int r = pipe.read(buf, got, n - got);
            if (r > 0) {
                got += r;
            } else {
                if (System.currentTimeMillis() > deadline) return null;
                try {
                    Thread.sleep(40);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Прервано");
                }
            }
        }
        return buf;
    }

    // ───────────────────────── каналы ─────────────────────────

    private interface Pipe {
        void write(byte[] b) throws IOException;

        /** Неблокирующее чтение: 0 — данных пока нет. */
        int read(byte[] b, int off, int len) throws IOException;

        void close();
    }

    private static Pipe openPipe() {
        boolean win = System.getProperty("os.name").toLowerCase().contains("win");
        if (win) {
            for (int i = 0; i < 10; i++) {
                try {
                    return new WinPipe("\\\\.\\pipe\\discord-ipc-" + i);
                } catch (IOException ignored) {}
            }
            return null;
        }
        Set<String> dirs = new LinkedHashSet<>();
        String xdg = System.getenv("XDG_RUNTIME_DIR");
        if (xdg != null) {
            dirs.add(xdg);
            dirs.add(xdg + "/app/com.discordapp.Discord");
            dirs.add(xdg + "/snap.discord");
        }
        for (String v : new String[]{"TMPDIR", "TMP", "TEMP"}) {
            String d = System.getenv(v);
            if (d != null) dirs.add(d);
        }
        dirs.add("/tmp");
        for (String d : dirs) {
            for (int i = 0; i < 10; i++) {
                Path p = Path.of(d, "discord-ipc-" + i);
                if (!Files.exists(p)) continue;
                try {
                    return new UnixPipe(p);
                } catch (IOException ignored) {}
            }
        }
        return null;
    }

    private static final class WinPipe implements Pipe {
        private final RandomAccessFile raf;

        WinPipe(String path) throws IOException {
            raf = new RandomAccessFile(path, "rw");
        }

        @Override
        public void write(byte[] b) throws IOException {
            raf.write(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            long avail;
            try {
                avail = raf.length();
            } catch (IOException e) {
                avail = 0;
            }
            if (avail <= 0) return 0;
            int r = raf.read(b, off, (int) Math.min(len, avail));
            return Math.max(r, 0);
        }

        @Override
        public void close() {
            try {
                raf.close();
            } catch (IOException ignored) {}
        }
    }

    private static final class UnixPipe implements Pipe {
        private final SocketChannel ch;

        UnixPipe(Path path) throws IOException {
            ch = SocketChannel.open(UnixDomainSocketAddress.of(path));
            ch.configureBlocking(false);
        }

        @Override
        public void write(byte[] b) throws IOException {
            ByteBuffer bb = ByteBuffer.wrap(b);
            int spins = 0;
            while (bb.hasRemaining()) {
                if (ch.write(bb) == 0 && ++spins > 500) throw new IOException("Не удалось записать в Discord");
                if (bb.hasRemaining()) {
                    try {
                        Thread.sleep(2);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Прервано");
                    }
                }
            }
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int r = ch.read(ByteBuffer.wrap(b, off, len));
            if (r < 0) throw new IOException("Discord закрыл соединение");
            return r;
        }

        @Override
        public void close() {
            try {
                ch.close();
            } catch (IOException ignored) {}
        }
    }
}
