package de.popcornsmp.scoreboard;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Statistic;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import de.popcornsmp.scoreboard.rank.Rank;
import de.popcornsmp.scoreboard.rank.RankCommand;
import de.popcornsmp.scoreboard.rank.RankManager;

public final class PopcornScoreboardPlugin extends JavaPlugin implements Listener {

    public static final String PREFIX = "§6§lPopcornSMP§r§8 » §r";
    private static final String SCOREBOARD_OBJECTIVE = "popcorn";
    private static final String TITLE = "§6§l§nPopcornSMP.de";

    private static final String ENTRY_BLANK_TOP = ChatColor.DARK_PURPLE.toString();
    private static final String ENTRY_BLANK_MIDDLE = ChatColor.DARK_BLUE.toString();
    private static final String ENTRY_BLANK_BOTTOM = ChatColor.DARK_GREEN.toString();

    private final Map<UUID, PlayerScoreboard> scoreboards = new HashMap<>();
    private RankManager rankManager;
    private BukkitTask updateTask;

    @Override
    public void onEnable() {
        rankManager = new RankManager(this);
        rankManager.load();

        RankCommand rankCommand = new RankCommand(this, rankManager);
        PluginCommand command = getCommand("rang");
        if (command != null) {
            command.setExecutor(rankCommand);
            command.setTabCompleter(rankCommand);
        } else {
            logError("Befehl /rang konnte nicht registriert werden.");
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        startUpdateTask();
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyRankFormatting(player);
            setupScoreboard(player);
        }
        refreshPlayerTeams();
        logInfo("Scoreboard Plugin aktiviert.");
    }

    @Override
    public void onDisable() {
        if (rankManager != null) {
            rankManager.save();
        }
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            Scoreboard main = manager.getMainScoreboard();
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.setScoreboard(main);
                player.setPlayerListHeaderFooter(null, null);
            }
        }

        scoreboards.clear();
        logInfo("Scoreboard Plugin deaktiviert.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        applyRankFormatting(player);
        setupScoreboard(player);
        refreshPlayerTeams();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeScoreboard(event.getPlayer());
        refreshPlayerTeams();
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Rank rank = rankManager.getRank(event.getPlayer().getUniqueId());
        event.setFormat(rank.getPrefix() + rank.getNameColor() + "%1$s§7: §7%2$s");
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

        initializeRankTeams(scoreboard);

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

    public void logInfo(String message) {
        Bukkit.getConsoleSender().sendMessage(PREFIX + "§7" + message);
    }

    public void logError(String message) {
        Bukkit.getConsoleSender().sendMessage(PREFIX + "§c" + message);
    }

    public void refreshPlayerTeams() {
        if (scoreboards.isEmpty()) {
            return;
        }

        Set<String> playerNames = new HashSet<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            playerNames.add(online.getName());
        }

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            PlayerScoreboard data = scoreboards.get(viewer.getUniqueId());
            if (data == null) {
                continue;
            }

            Scoreboard scoreboard = data.scoreboard();
            initializeRankTeams(scoreboard);
            clearTeamEntries(scoreboard, playerNames);

            for (Player target : Bukkit.getOnlinePlayers()) {
                assignPlayerToTeam(scoreboard, target);
            }
        }
    }

    public void applyRankFormatting(Player player) {
        Rank rank = rankManager.getRank(player.getUniqueId());
        player.setOp(rank.shouldGrantOp());
        updatePlayerListHeaderFooter(player);
    }

    private void updatePlayerListHeaderFooter(Player player) {
        player.setPlayerListHeaderFooter("§6§lPopcornSMP.de", "");
    }

    private void initializeRankTeams(Scoreboard scoreboard) {
        for (Rank rank : Rank.values()) {
            Team team = scoreboard.getTeam(rank.getTeamName());
            if (team == null) {
                team = scoreboard.registerNewTeam(rank.getTeamName());
            }
            team.setPrefix(rank.getPrefix());
            team.setColor(rank.getNameColor());
            team.setSuffix(ChatColor.RESET.toString());
        }
    }

    private void clearTeamEntries(Scoreboard scoreboard, Set<String> validEntries) {
        for (Team team : scoreboard.getTeams()) {
            Set<String> entries = new HashSet<>(team.getEntries());
            for (String entry : entries) {
                if (!validEntries.contains(entry)) {
                    team.removeEntry(entry);
                }
            }
        }
    }

    private void assignPlayerToTeam(Scoreboard scoreboard, Player target) {
        Rank rank = rankManager.getRank(target.getUniqueId());
        Team team = scoreboard.getTeam(rank.getTeamName());
        if (team == null) {
            team = scoreboard.registerNewTeam(rank.getTeamName());
            team.setPrefix(rank.getPrefix());
            team.setColor(rank.getNameColor());
            team.setSuffix(ChatColor.RESET.toString());
        }

        for (Team existing : scoreboard.getTeams()) {
            if (!existing.equals(team) && existing.hasEntry(target.getName())) {
                existing.removeEntry(target.getName());
            }
        }

        team.addEntry(target.getName());
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
