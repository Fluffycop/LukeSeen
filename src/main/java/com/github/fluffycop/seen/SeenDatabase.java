package com.github.fluffycop.seen;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
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
        ds = new HikariDataSource(setupConfig());
        this.setupTables();
    }

    @NonNull
    private HikariConfig setupConfig() {
        HikariConfig config = new HikariConfig();
        config.setPoolName("Lukeseen Connection Pool-1");
        config.setJdbcUrl(
                "jdbc:mysql://" + SeenConfig.get().getAddress() + ":" + SeenConfig.get().getPort() + "/" + SeenConfig.get().getDatabase() + "?useSSL=false"
        );
        config.setUsername(SeenConfig.get().getUsername());
        config.setPassword(SeenConfig.get().getPassword());
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setConnectionTestQuery("SELECT 1;");
        config.addDataSourceProperty("useLegacyDatetimeCode", "false");
        config.addDataSourceProperty("serverTimezone", "UTC");
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.setInitializationFailTimeout(0);
        return config;
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

    private final @NonNull Map<@NonNull String, @NonNull UUID> lookupCache = new ConcurrentHashMap<>();

    public @NonNull CompletableFuture<@Nullable LoginRecord> getLastLogin(@NonNull String username) {
        return CompletableFuture.supplyAsync(() -> {
            final UUID uuid;
            if (lookupCache.containsKey(username)) {
                uuid = lookupCache.get(username);
            } else {
                uuid = Bukkit.getOfflinePlayer(username).getUniqueId();
                this.lookupCache.put(username, uuid);
            }
            return getLastLoginBlocking(uuid);
        }, pool);
    }

    public @NonNull CompletableFuture<@Nullable LoginRecord> getLastLogin(@NonNull OfflinePlayer player) {
        return CompletableFuture.supplyAsync(() -> getLastLoginBlocking(player.getUniqueId()), pool);
    }

    private @Nullable LoginRecord getLastLoginBlocking(@NonNull UUID uuid) {
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

    private static final long SHUTDOWN_TIMEOUT_SEC = 30;

    public void shutdown() {
        this.pool.shutdown();
        try {
            boolean executedSuccessfully = this.pool.awaitTermination(SHUTDOWN_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!executedSuccessfully) {
                this.plugin.getLogger().log(Level.WARNING, "Database thread pool took too long to finish tasks. Some last login/seen data may be lost.");
            }
        } catch (InterruptedException e) {
            this.plugin.getLogger().log(Level.WARNING, "Database thread pool was unexpectedly interrupted while awaiting tasks to complete. Some last login/seen data may be lost.");
            e.printStackTrace();
        }
        this.ds.close();
    }
}
