package com.github.fluffycop.seen;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

// Yes there are command libraries but this is stupid simple
public class CmdSeen implements CommandExecutor {
    @NonNull
    private final SeenPlugin plugin;

    public CmdSeen(@NonNull SeenPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Audience audience = plugin.adventure().sender(sender);
        // Honestly I have no idea if permission-message actually works in plugin.yml so this is just in case
        if (!sender.hasPermission("lukeseen.seen")) {
            audience.sendMessage(
                    Component.text("You do not have permission to use this command.")
                            .color(NamedTextColor.RED));
            return true;
        }
        if (args.length != 1) {
            audience.sendMessage(
                    Component.text("/seen <player>")
                            .color(NamedTextColor.RED)
            );
            return true;
        }
        String targetUsername = args[0];
        OfflinePlayer player = Bukkit.getOfflinePlayerIfCached(targetUsername);
        if (player == null) { // either they've never joined or the cache doesn't contain them
            audience.sendMessage(
                    Component.text("We haven't seen ")
                            .color(NamedTextColor.GRAY)
                            .toBuilder()
                            .append(
                                    Component.text(targetUsername)
                                            .color(NamedTextColor.GREEN),
                                    Component.text(" in a while. Please wait a moment while we pull up their data...")
                                            .color(NamedTextColor.GRAY)
                            )
            );
            plugin.getDatabase()
                    .getLastLogin(targetUsername)
                    .thenAccept(record -> interpretAndSend(record, targetUsername, audience));
        } else if (!player.isOnline()) { // offline on this server but in the cache
            plugin.getDatabase()
                    .getLastLogin(player)
                    .thenAccept(record -> {
                        if (record == null) { // never joined other servers
                            interpretAndSend(new LoginRecord(player.getUniqueId(), SeenConfig.get().getServerName(), player.getLastSeen(), false), targetUsername, audience);
                        } else { // usercache has a record and db has a record
                            if (record.isOnline() && !record.getServer().equals(SeenConfig.get().getServerName())) { // online but not on this server
                                interpretAndSend(record, targetUsername, audience);
                            } else { // offline on all servers, compare last seens and send most recent
                                if (record.getLastLogin() > player.getLastSeen()) { // bigger timestamp means more recent
                                    interpretAndSend(record, targetUsername, audience);
                                } else {
                                    interpretAndSend(new LoginRecord(player.getUniqueId(), SeenConfig.get().getServerName(), player.getLastSeen(), false), targetUsername, audience);
                                }
                            }
                        }
                    });
        } else { // they're online
            interpretAndSend(new LoginRecord(player.getUniqueId(), SeenConfig.get().getServerName(), player.getLastLogin(), true), targetUsername, audience);
        }
        return true;
    }

    private static String humanReadableFormat(@NonNull Duration d) {
        long daysVal = d.toDaysPart();
        String months = daysVal / 30 > 0 ? daysVal / 30 + "m " : "";
        String days = daysVal % 30 > 0 ? daysVal % 30 + "d " : "";
        String hours = d.toHoursPart() > 0 ? d.toHoursPart() + "h " : "";
        String minutes = d.toMinutesPart() > 0 ? d.toMinutesPart() + "m " : "";
        String seconds = d.toSecondsPart() > 0 ? d.toSecondsPart() + "s" : "";
        return months + days + hours + minutes + seconds;
    }

    private static void interpretAndSend(@Nullable LoginRecord record, @NonNull String user, @NonNull Audience audience) {
        if (record == null || record.getLastLogin() <= 1471558235000L) { // magic timestamp for 5 years ago
            audience.sendMessage(
                    Component.text(user)
                            .color(NamedTextColor.GREEN)
                            .append(
                                    Component.text(" has never joined before.")
                                            .color(NamedTextColor.GRAY)
                            )
            );
        } else {
            String duration = humanReadableFormat(Duration.of(System.currentTimeMillis() - record.getLastLogin(), ChronoUnit.MILLIS));
            if (record.isOnline()) {
                audience.sendMessage(
                        Component.text(user)
                                .color(NamedTextColor.GREEN)
                                .toBuilder()
                                .append(
                                        Component.text(" has been on ")
                                                .color(NamedTextColor.GRAY),
                                        Component.text(record.getServer())
                                                .color(NamedTextColor.GREEN),
                                        Component.text(" for ")
                                                .color(NamedTextColor.GRAY),
                                        Component.text(duration)
                                                .color(NamedTextColor.GREEN)
                                )
                );
            } else {
                audience.sendMessage(
                        Component.text(user)
                                .color(NamedTextColor.GREEN)
                                .toBuilder()
                                .append(
                                        Component.text(" has been offline for ")
                                                .color(NamedTextColor.GRAY),
                                        Component.text(duration)
                                                .color(NamedTextColor.GREEN),
                                        Component.text(" and was last seen on ")
                                                .color(NamedTextColor.GRAY),
                                        Component.text(record.getServer())
                                                .color(NamedTextColor.GREEN)
                                )
                );
            }
        }
    }
}
