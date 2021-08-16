package com.github.fluffycop.seen;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class SeenDatabase {
    @NonNull
    private final SeenPlugin plugin;
    @NonNull
    private final ExecutorService pool;
    @NonNull
    private final HikariDataSource ds;

    public SeenDatabase(@NonNull SeenPlugin plugin) {
        this.plugin = plugin;
        pool = Executors.newFixedThreadPool(SeenConfig.get().getThreadPoolSize());
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(
                "jdbc:mysql://" + SeenConfig.get().getInfo().getAddress() + ":" + SeenConfig.get().getInfo().getPort() + "/" + SeenConfig.get().getInfo().getDatabase()
        );
        cfg.setUsername(SeenConfig.get().getInfo().getUsername());
        cfg.setPassword(SeenConfig.get().getInfo().getPassword());
        cfg.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds = new HikariDataSource(cfg);
        this.setupTables();
    }

    private void setupTables() {
        String sql = "CREATE TABLE IF NOT EXISTS lukeseen_logins (" +
                "player_id VARCHAR(36) PRIMARY KEY," +
                "server TEXT NOT NULL," +
                "login_time BIGINT NOT NULL," +
                "online BIT(1)" +
                ");";
        try (Connection conn = ds.getConnection(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.execute();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Encountered an unexpected error when trying to create database tables");
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(plugin);
        }
    }

    public @NonNull CompletableFuture<@Nullable LoginRecord> getLastLogin(@NonNull OfflinePlayer player) {
        return CompletableFuture.supplyAsync(() -> {
            UUID uuid = player.getUniqueId();
            String sql = "SELECT server,login_time,online " +
                    "FROM lukeseen_logins " +
                    "WHERE player_id=?;";
            try (Connection conn = ds.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery(); // the expected size of the set is 1
                if (rs.next()) { // not empty
                    return new LoginRecord(
                            uuid,
                            rs.getString(1),
                            rs.getLong(2),
                            rs.getBoolean(3)
                    );
                } else { // empty
                    return null;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Encountered an unexpected error while retrieving login information for player with UUID " + uuid + " from the database");
                e.printStackTrace();
                return null;
            }
        }, pool);
    }

    public void setLastLogin(@NonNull LoginRecord record) {
        pool.execute(() -> {
            String sql = "INSERT INTO lukeseen_logins VALUES (?,?,?,?) " +
                    "ON DUPLICATE KEY UPDATE server=?,login_time=?,online=?;";
            try (Connection conn = ds.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, record.getUuid().toString());
                stmt.setString(2, record.getServer());
                stmt.setLong(3, record.getLastLogin());
                stmt.setBoolean(4, record.isOnline());

                stmt.setString(5, record.getServer());
                stmt.setLong(6, record.getLastLogin());
                stmt.setBoolean(7, record.isOnline());

                stmt.execute();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Encountered an unexpected error while writing to database: " + record);
                e.printStackTrace();
            }
        });
    }

    private static final long SHUTDOWN_TIMEOUT_SEC= 30;

    public void shutdown() {
        this.pool.shutdown();
        try {
            boolean executedSuccessfully = this.pool.awaitTermination(SHUTDOWN_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (executedSuccessfully) {
                this.plugin.getLogger().log(Level.WARNING, "Database thread pool took too long to finish tasks. Some last login/seen data may be lost.");
            }
        } catch (InterruptedException e) {
            this.plugin.getLogger().log(Level.WARNING, "Database thread pool was unexpectedly interrupted while awaiting tasks to complete. Some last login/seen data may be lost.");
            e.printStackTrace();
        }
        this.ds.close();
    }
}
