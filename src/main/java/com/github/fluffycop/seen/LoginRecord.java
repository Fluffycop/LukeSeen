package com.github.fluffycop.seen;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.UUID;

public final class LoginRecord {
    @NonNull
    private final UUID uuid;
    @NonNull
    private final String server;
    private final long lastLogin;
    private final boolean online;

    public LoginRecord(@NonNull UUID uuid, @NonNull String server, long lastLogin, boolean online) {
        this.uuid = uuid;
        this.server = server;
        this.lastLogin = lastLogin;
        this.online = online;
    }

    @NonNull
    public UUID getUuid() {
        return uuid;
    }

    public boolean isOnline() {
        return online;
    }

    @NonNull
    public String getServer() {
        return server;
    }

    public long getLastLogin() {
        return lastLogin;
    }

    @Override
    public String toString() {
        return "LoginRecord{" +
                "uuid=" + uuid +
                ", server='" + server + '\'' +
                ", lastLogin=" + lastLogin +
                ", online=" + online +
                '}';
    }
}
