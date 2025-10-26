package de.popcornsmp.scoreboard.data;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import de.popcornsmp.scoreboard.PopcornScoreboardPlugin;

public final class PlayerDataManager {
    private final PopcornScoreboardPlugin plugin;
    private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();
    private File file;
    private FileConfiguration configuration;

    public PlayerDataManager(PopcornScoreboardPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        file = new File(plugin.getDataFolder(), "scoreboard-data.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException ex) {
                plugin.logError("Konnte scoreboard-data.yml nicht erstellen: " + ex.getMessage());
            }
        }

        configuration = YamlConfiguration.loadConfiguration(file);
        for (String key : configuration.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                long playMinutes = configuration.getLong(key + ".playMinutes", 0L);
                int deaths = configuration.getInt(key + ".deaths", 0);
                data.put(uuid, new PlayerData(playMinutes, deaths));
            } catch (IllegalArgumentException ex) {
                plugin.logError("Ungültige UUID in scoreboard-data.yml: " + key);
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

        for (Map.Entry<UUID, PlayerData> entry : data.entrySet()) {
            String base = entry.getKey().toString();
            PlayerData value = entry.getValue();
            configuration.set(base + ".playMinutes", value.getPlayMinutes());
            configuration.set(base + ".deaths", value.getDeaths());
        }

        try {
            configuration.save(file);
        } catch (IOException ex) {
            plugin.logError("Konnte scoreboard-data.yml nicht speichern: " + ex.getMessage());
        }
    }

    public PlayerData updateAndGet(UUID uuid, long playMinutes, int deaths) {
        PlayerData playerData = data.computeIfAbsent(uuid, key -> new PlayerData(playMinutes, deaths));
        boolean changed = false;
        changed |= playerData.setPlayMinutes(playMinutes);
        changed |= playerData.setDeaths(deaths);
        if (changed) {
            save();
        }
        return playerData;
    }

    public PlayerData get(UUID uuid) {
        return data.get(uuid);
    }

    public static final class PlayerData {
        private long playMinutes;
        private int deaths;

        private PlayerData(long playMinutes, int deaths) {
            this.playMinutes = Math.max(playMinutes, 0L);
            this.deaths = Math.max(deaths, 0);
        }

        public long getPlayMinutes() {
            return playMinutes;
        }

        public int getDeaths() {
            return deaths;
        }

        public boolean setPlayMinutes(long playMinutes) {
            long sanitized = Math.max(playMinutes, 0L);
            if (this.playMinutes != sanitized) {
                this.playMinutes = sanitized;
                return true;
            }
            return false;
        }

        public boolean setDeaths(int deaths) {
            int sanitized = Math.max(deaths, 0);
            if (this.deaths != sanitized) {
                this.deaths = sanitized;
                return true;
            }
            return false;
        }
    }
}
