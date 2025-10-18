package de.popcornsmp.scoreboard;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

public final class PopcornScoreboardPlugin extends JavaPlugin implements Listener {

    private static final String PREFIX = "§6§lPopcornSMP§r§8 » §r";
    private static final String SCOREBOARD_OBJECTIVE = "popcorn";
    private static final String TITLE = "§6§l§nPopcornSMP.de";

    private static final String ENTRY_BLANK_TOP = ChatColor.DARK_PURPLE.toString();
    private static final String ENTRY_BLANK_MIDDLE = ChatColor.DARK_BLUE.toString();
    private static final String ENTRY_BLANK_BOTTOM = ChatColor.DARK_GREEN.toString();

    private final Map<UUID, PlayerScoreboard> scoreboards = new HashMap<>();
    private BukkitTask updateTask;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        startUpdateTask();
        Bukkit.getOnlinePlayers().forEach(this::setupScoreboard);
        logInfo("Scoreboard Plugin aktiviert.");
    }

    @Override
    public void onDisable() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            Scoreboard main = manager.getMainScoreboard();
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.setScoreboard(main);
            }
        }

        scoreboards.clear();
        logInfo("Scoreboard Plugin deaktiviert.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        setupScoreboard(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeScoreboard(event.getPlayer());
    }

    private void setupScoreboard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            logError("Konnte ScoreboardManager nicht abrufen. Scoreboard wird nicht gesetzt.");
            return;
        }

        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(SCOREBOARD_OBJECTIVE, Criteria.DUMMY, TITLE);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        objective.getScore(ENTRY_BLANK_TOP).setScore(5);

        String initialPlaytime = formatPlaytimeLine(0, 0);
        objective.getScore(initialPlaytime).setScore(4);

        String initialDeaths = formatDeathsLine(0);
        objective.getScore(initialDeaths).setScore(3);

        objective.getScore(ENTRY_BLANK_MIDDLE).setScore(2);

        String initialPing = formatPingLine(0);
        objective.getScore(initialPing).setScore(1);

        objective.getScore(ENTRY_BLANK_BOTTOM).setScore(0);

        player.setScoreboard(scoreboard);
        scoreboards.put(player.getUniqueId(), new PlayerScoreboard(Instant.now(), scoreboard, objective, initialPlaytime, initialDeaths, initialPing));
    }

    private void removeScoreboard(Player player) {
        PlayerScoreboard data = scoreboards.remove(player.getUniqueId());
        if (data != null) {
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager != null) {
                player.setScoreboard(manager.getMainScoreboard());
            }
        }
    }

    private void startUpdateTask() {
        updateTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerScoreboard data = scoreboards.get(player.getUniqueId());
                if (data != null) {
                    updateScoreboard(player, data);
                }
            }
        }, 20L, 20L);
    }

    private void updateScoreboard(Player player, PlayerScoreboard data) {
        long minutes = Duration.between(data.sessionStart(), Instant.now()).toMinutes();
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;

        String newPlaytime = formatPlaytimeLine(hours, remainingMinutes);
        if (!newPlaytime.equals(data.playtimeLine())) {
            data.scoreboard().resetScores(data.playtimeLine());
            data.objective().getScore(newPlaytime).setScore(4);
            data.setPlaytimeLine(newPlaytime);
        }

        int deaths = player.getStatistic(Statistic.DEATHS);
        String newDeaths = formatDeathsLine(deaths);
        if (!newDeaths.equals(data.deathsLine())) {
            data.scoreboard().resetScores(data.deathsLine());
            data.objective().getScore(newDeaths).setScore(3);
            data.setDeathsLine(newDeaths);
        }

        int ping = player.getPing();
        String newPing = formatPingLine(ping);
        if (!newPing.equals(data.pingLine())) {
            data.scoreboard().resetScores(data.pingLine());
            data.objective().getScore(newPing).setScore(1);
            data.setPingLine(newPing);
        }
    }

    private String formatPlaytimeLine(long hours, long minutes) {
        return "§7Spielzeit: §6" + hours + "§7h §6" + minutes + "§7m";
    }

    private String formatDeathsLine(int deaths) {
        return "§7Tode: §6" + deaths;
    }

    private String formatPingLine(int ping) {
        return "§7Ping: §6" + ping + "§7ms";
    }

    private void logInfo(String message) {
        Bukkit.getConsoleSender().sendMessage(PREFIX + "§7" + message);
    }

    private void logError(String message) {
        Bukkit.getConsoleSender().sendMessage(PREFIX + "§c" + message);
    }

    private static final class PlayerScoreboard {
        private final Instant sessionStart;
        private final Scoreboard scoreboard;
        private final Objective objective;
        private String playtimeLine;
        private String deathsLine;
        private String pingLine;

        private PlayerScoreboard(Instant sessionStart, Scoreboard scoreboard, Objective objective, String playtimeLine, String deathsLine, String pingLine) {
            this.sessionStart = sessionStart;
            this.scoreboard = scoreboard;
            this.objective = objective;
            this.playtimeLine = playtimeLine;
            this.deathsLine = deathsLine;
            this.pingLine = pingLine;
        }

        private Instant sessionStart() {
            return sessionStart;
        }

        private Scoreboard scoreboard() {
            return scoreboard;
        }

        private Objective objective() {
            return objective;
        }

        private String playtimeLine() {
            return playtimeLine;
        }

        private void setPlaytimeLine(String playtimeLine) {
            this.playtimeLine = playtimeLine;
        }

        private String deathsLine() {
            return deathsLine;
        }

        private void setDeathsLine(String deathsLine) {
            this.deathsLine = deathsLine;
        }

        private String pingLine() {
            return pingLine;
        }

        private void setPingLine(String pingLine) {
            this.pingLine = pingLine;
        }
    }
}
