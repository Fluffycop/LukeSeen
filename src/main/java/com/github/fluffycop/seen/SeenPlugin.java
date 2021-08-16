package com.github.fluffycop.seen;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;

public class SeenPlugin extends JavaPlugin {
    @MonotonicNonNull
    private BukkitAudiences adventure;
    @MonotonicNonNull
    private SeenDatabase database;

    @NonNull
    public BukkitAudiences adventure() {
        if(this.adventure == null) {
            throw new IllegalStateException("Tried to access Adventure when the plugin was disabled!");
        }
        return this.adventure;
    }

    @Override
    public void onEnable() {
        SeenConfig.load(this);
        this.database = new SeenDatabase(this);
        this.adventure = BukkitAudiences.create(this);
        Bukkit.getPluginManager().registerEvents(new JoinLeaveListener(this), this);
        this.getCommand("seen").setExecutor(new CmdSeen(this));
    }

    @Override
    public void onDisable() {
        this.getCommand("seen").setExecutor(null);
        if(this.adventure != null) this.adventure.close();
        if (this.database != null) this.database.shutdown();
        SeenConfig.clear();
    }

    public SeenDatabase getDatabase() {
        return database;
    }
}
