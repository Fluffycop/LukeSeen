package com.github.fluffycop.seen;

import org.bukkit.Bukkit;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.logging.Level;

@ConfigSerializable
class SeenConfig {
    @Comment("The name that this server will appear as.")
    private @NonNull String serverName = "server";

    @Comment("The size of the thread pool that will be executing database queries. For databases hosted on the same machine, you really only need 1 or 2.")
    private int threadPoolSize = 4;

    @Comment("Standard database connection stuff.")
    private @NonNull DatabaseInfo info = new DatabaseInfo();

    @ConfigSerializable
    static class DatabaseInfo {
        private @NonNull String address = "localhost";
        private int port = 3306;
        private @NonNull String username = "root";
        private @NonNull String password = "";
        private @NonNull String database = "database";

        @NonNull
        public String getDatabase() {
            return database;
        }

        @NonNull
        public String getAddress() {
            return address;
        }

        public int getPort() {
            return port;
        }

        @NonNull
        public String getUsername() {
            return username;
        }

        @NonNull
        public String getPassword() {
            return password;
        }
    }

    @NonNull
    public String getServerName() {
        return serverName;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    @NonNull
    public DatabaseInfo getInfo() {
        return info;
    }

    // singleton stuff
    private static @MonotonicNonNull SeenConfig INSTANCE;

    @NonNull
    public static SeenConfig get() {
        return INSTANCE;
    }

    public static void load(@NonNull SeenPlugin plugin) {
        File file = new File(plugin.getDataFolder() + File.separator + "config.conf");
        if (!file.exists()) {
            plugin.getLogger().log(Level.INFO, "No config.conf detected, creating a new one...");
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
                FileUtil.copyInputStreamToFile(plugin.getResource("config.conf"), file);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Encountered an unexpected error while creating the config.conf.");
                file.delete();
                e.printStackTrace();
                Bukkit.getPluginManager().disablePlugin(plugin);
            }
            INSTANCE = new SeenConfig();
        } else {
            HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
                    .path(Paths.get(file.getPath()))
                    .build();
            try {
                INSTANCE = loader.load().get(SeenConfig.class);
            } catch (ConfigurateException e) {
                plugin.getLogger().log(Level.SEVERE, "Encountered an unexpected error while reading config.conf.");
                e.printStackTrace();
                Bukkit.getPluginManager().disablePlugin(plugin);
            }
        }
    }

    public static void clear() {
        INSTANCE = null;
    }
}
