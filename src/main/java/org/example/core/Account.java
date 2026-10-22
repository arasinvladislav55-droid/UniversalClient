package org.example.core;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Локальный (offline) профиль игрока. */
public class Account {
    public String name;
    public String uuid;
    public long lastLogin;

    public Account() {}

    public Account(String name) {
        this.name = name;
        this.uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
