package de.popcornsmp.scoreboard.rank;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import de.popcornsmp.scoreboard.PopcornScoreboardPlugin;

public final class RankManager {
    private final PopcornScoreboardPlugin plugin;
    private final Map<UUID, Rank> ranks = new ConcurrentHashMap<>();
    private FileConfiguration configuration;
    private File file;

    public RankManager(PopcornScoreboardPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        file = new File(plugin.getDataFolder(), "ranks.yml");
        if (!file.exists()) {
            configuration = new YamlConfiguration();
            save();
        }

        configuration = YamlConfiguration.loadConfiguration(file);
        for (String key : configuration.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String rankKey = configuration.getString(key);
                Rank rank = Rank.fromKey(rankKey);
                if (rank != null && rank != Rank.SPIELER) {
                    ranks.put(uuid, rank);
                }
            } catch (IllegalArgumentException ex) {
                plugin.logError("Ungültige UUID in ranks.yml: " + key);
            }
        }
    }

    public void save() {
        if (configuration == null || file == null) {
            return;
        }

        for (String key : new java.util.HashSet<>(configuration.getKeys(false))) {
            configuration.set(key, null);
        }

        for (Map.Entry<UUID, Rank> entry : ranks.entrySet()) {
            configuration.set(entry.getKey().toString(), entry.getValue().getKey());
        }

        try {
            configuration.save(file);
        } catch (IOException ex) {
            plugin.logError("Konnte ranks.yml nicht speichern: " + ex.getMessage());
        }
    }

    public Rank getRank(UUID uuid) {
        return ranks.getOrDefault(uuid, Rank.SPIELER);
    }

    public void setRank(UUID uuid, Rank rank) {
        if (rank == Rank.SPIELER) {
            ranks.remove(uuid);
        } else {
            ranks.put(uuid, rank);
        }
        save();
    }

    public Rank getRank(OfflinePlayer player) {
        return getRank(player.getUniqueId());
    }
}
