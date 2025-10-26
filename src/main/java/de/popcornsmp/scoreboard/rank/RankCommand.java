package de.popcornsmp.scoreboard.rank;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import de.popcornsmp.scoreboard.PopcornScoreboardPlugin;

public final class RankCommand implements CommandExecutor, TabCompleter {
    private final PopcornScoreboardPlugin plugin;
    private final RankManager rankManager;

    public RankCommand(PopcornScoreboardPlugin plugin, RankManager rankManager) {
        this.plugin = plugin;
        this.rankManager = rankManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(PopcornScoreboardPlugin.PREFIX + "§cDazu hast du keine Rechte.");
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage(PopcornScoreboardPlugin.PREFIX + "§7Verwendung: §6/" + label + " <Spieler> <Rang>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target == null || (target.getName() == null && !target.hasPlayedBefore())) {
            sender.sendMessage(PopcornScoreboardPlugin.PREFIX + "§cSpieler wurde nicht gefunden.");
            return true;
        }

        Rank rank = Rank.fromKey(args[1]);
        if (rank == null) {
            sender.sendMessage(PopcornScoreboardPlugin.PREFIX + "§cUnbekannter Rang. Verfügbare Ränge: §6" + getAvailableRanks());
            return true;
        }

        UUID uuid = target.getUniqueId();
        String targetName = target.getName() != null ? target.getName() : args[0];
        rankManager.setRank(uuid, rank);
        target.setOp(rank.shouldGrantOp());

        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null) {
            plugin.applyRankFormatting(onlineTarget);
            onlineTarget.sendMessage(PopcornScoreboardPlugin.PREFIX + "§7Dein Rang wurde zu §6" + rank.getKey() + " §7gesetzt.");
        }

        plugin.refreshPlayerTeams();
        plugin.updateTabListAppearance();

        sender.sendMessage(PopcornScoreboardPlugin.PREFIX + "§7Rang für §6" + targetName + " §7auf §6" + rank.getKey() + " §7gesetzt.");
        return true;
    }

    private String getAvailableRanks() {
        Rank[] ranks = Rank.values();
        String[] names = new String[ranks.length];
        for (int i = 0; i < ranks.length; i++) {
            names[i] = ranks[i].getKey();
        }
        return "§6" + String.join("§7, §6", names);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.isOp()) {
            return List.of();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    suggestions.add(player.getName());
                }
            }
            return suggestions;
        }

        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>();
            for (Rank rank : Rank.values()) {
                if (rank.getKey().startsWith(prefix)) {
                    suggestions.add(rank.getKey());
                }
            }
            return suggestions;
        }

        return List.of();
    }
}
