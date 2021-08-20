package com.github.fluffycop.seen;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.checkerframework.checker.nullness.qual.NonNull;

public class JoinLeaveListener implements Listener {
    @NonNull
    private final SeenPlugin plugin;

    public JoinLeaveListener(@NonNull SeenPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onJoin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        plugin.getDatabase().setLastLogin(
                new LoginRecord(
                        player.getUniqueId(),
                        SeenConfig.get().getServerName(),
                        System.currentTimeMillis(),
                        true
                )
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getDatabase().setLastLogin(
                new LoginRecord(
                        player.getUniqueId(),
                        SeenConfig.get().getServerName(),
                        System.currentTimeMillis(),
                        false
                )
        );
    }
}
