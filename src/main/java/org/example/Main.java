package org.example;

import org.example.ui.LauncherApp;

/** Отдельный класс-запускатор (не наследует Application), чтобы JavaFX работал и из IDE, и из fat-jar. */
public class Main {
    public static void main(String[] args) {
        LauncherApp.main(args);
    }
}
