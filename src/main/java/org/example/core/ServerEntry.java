package org.example.core;

/** Сервер в списке «Серверы». address — host или host:port. */
public class ServerEntry {
    public String name = "";
    public String address = "";

    public ServerEntry() {}

    public ServerEntry(String name, String address) {
        this.name = name;
        this.address = address;
    }
}
