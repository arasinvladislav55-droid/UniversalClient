package org.example.core;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Лог лаунчера и игры: %APPDATA%\.universalclient\logs\launcher.log */
public final class LogFile {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static PrintWriter out;

    private LogFile() {}

    public static synchronized void write(String line) {
        try {
            if (out == null) {
                Files.createDirectories(Dirs.LOGS);
                out = new PrintWriter(Files.newBufferedWriter(Dirs.LOGS.resolve("launcher.log"), StandardCharsets.UTF_8), true);
            }
            out.println("[" + LocalTime.now().format(FMT) + "] " + line);
        } catch (IOException ignored) {}
    }
}
