package de.popcornsmp.scoreboard;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Statistic;
import org.bukkit.Sound;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.scheduler.BukkitTask;

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

    private static final long TAB_ROTATION_INTERVAL_TICKS = 20L * 10L;
    private static final long CHAT_BROADCAST_INTERVAL_TICKS = 20L * 60L * 10L;
    private static final String TAB_SEPARATOR = "§8§m--------------------";
    private static final String CHAT_SEPARATOR = "§8§m------------------------------";
    private static final List<String> ANNOUNCEMENT_MESSAGES = List.of(
            "§7Joine gerne unserem Discord Server §6discord.gg/popcornsmp",
            "§7Du möchtest dem Server spenden und Vorteile erhalten? Das geht auf unserem Discord Server §6discord.gg/popcornsmp",
            "§7Poste Servervorschläge auf unserem Discord! §6discord.gg/popcornsmp",
            "§7Benutze §6/chunk §7um auf das Chunkmenü zuzugreifen und Chunks zu claimen und zu Verwalten!",
            "§7Mit §6/rtp §7kannst du dich an einen zufälligen Standort Telepotieren!"
    );

    private final Map<UUID, PlayerScoreboard> scoreboards = new HashMap<>();
    private RankManager rankManager;
    private BukkitTask updateTask;
    private BukkitTask tabRotationTask;
    private BukkitTask chatBroadcastTask;
    private int tabAnnouncementIndex;
    private int chatAnnouncementIndex;

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
        startTabRotationTask();
        startChatBroadcastTask();
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyRankFormatting(player);
            setupScoreboard(player);
        }
        refreshPlayerTeams();
        updateTabListAppearance();
        logInfo("Scoreboard Plugin aktiviert.");
    }

    @Override
    public void onDisable() {
        if (rankManager != null) {
            rankManager.save();
        }
        updateTask = cancelTask(updateTask);
        tabRotationTask = cancelTask(tabRotationTask);
        chatBroadcastTask = cancelTask(chatBroadcastTask);

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            Scoreboard main = manager.getMainScoreboard();
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.setScoreboard(main);
                player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
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
        updateTabListAppearance();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeScoreboard(event.getPlayer());
        refreshPlayerTeams();
        updateTabListAppearance();
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

        long playTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        TimeValues playtime = calculatePlaytime(playTicks);
        String initialPlaytime = formatPlaytimeLine(playtime.hours(), playtime.minutes());
        objective.getScore(initialPlaytime).setScore(4);

        String initialDeaths = formatDeathsLine(player.getStatistic(Statistic.DEATHS));
        objective.getScore(initialDeaths).setScore(3);

        objective.getScore(ENTRY_BLANK_MIDDLE).setScore(2);

        String initialPing = formatPingLine(player.getPing());
        objective.getScore(initialPing).setScore(1);

        objective.getScore(ENTRY_BLANK_BOTTOM).setScore(0);

        player.setScoreboard(scoreboard);
        scoreboards.put(player.getUniqueId(), new PlayerScoreboard(scoreboard, objective, initialPlaytime, initialDeaths, initialPing));
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

    private void startTabRotationTask() {
        tabRotationTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            advanceTabAnnouncement();
            updateTabListAppearance();
        }, TAB_ROTATION_INTERVAL_TICKS, TAB_ROTATION_INTERVAL_TICKS);
    }

    private void startChatBroadcastTask() {
        chatBroadcastTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            String message = ANNOUNCEMENT_MESSAGES.get(chatAnnouncementIndex);
            broadcastAnnouncement(message);
            chatAnnouncementIndex = (chatAnnouncementIndex + 1) % ANNOUNCEMENT_MESSAGES.size();
        }, CHAT_BROADCAST_INTERVAL_TICKS, CHAT_BROADCAST_INTERVAL_TICKS);
    }

    private void updateScoreboard(Player player, PlayerScoreboard data) {
        long playTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        TimeValues playtime = calculatePlaytime(playTicks);

        String newPlaytime = formatPlaytimeLine(playtime.hours(), playtime.minutes());
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
        player.playerListName(LegacyComponentSerializer.legacySection().deserialize(rank.getPrefix() + rank.getNameColor() + player.getName()));
    }

    public void updateTabListAppearance() {
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        String headerText = "§6§lPopcornSMP.de\n§8§m--------------------\n§7Online: §6" + online + "§7/§6" + max;
        String footerText = TAB_SEPARATOR + "\n" + getCurrentTabAnnouncement() + "\n" + TAB_SEPARATOR;
        Component header = LegacyComponentSerializer.legacySection().deserialize(headerText);
        Component footer = LegacyComponentSerializer.legacySection().deserialize(footerText);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendPlayerListHeaderAndFooter(header, footer);
        }
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

    private TimeValues calculatePlaytime(long playTicks) {
        long totalMinutes = playTicks / (20 * 60);
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return new TimeValues(hours, minutes);
    }

    private void advanceTabAnnouncement() {
        tabAnnouncementIndex = (tabAnnouncementIndex + 1) % ANNOUNCEMENT_MESSAGES.size();
    }

    private String getCurrentTabAnnouncement() {
        return ANNOUNCEMENT_MESSAGES.get(tabAnnouncementIndex);
    }

    private void broadcastAnnouncement(String message) {
        String prefixedMessage = PREFIX + message;
        String prefixedSeparator = PREFIX + CHAT_SEPARATOR;
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(prefixedSeparator);
            player.sendMessage(prefixedMessage);
            player.sendMessage(prefixedSeparator);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        }
        logInfo("Announcement gesendet: " + ChatColor.stripColor(message));
    }

    private BukkitTask cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
        return null;
    }

    private record TimeValues(long hours, long minutes) {
    }

    private static final class PlayerScoreboard {
        private final Scoreboard scoreboard;
        private final Objective objective;
        private String playtimeLine;
        private String deathsLine;
        private String pingLine;

        private PlayerScoreboard(Scoreboard scoreboard, Objective objective, String playtimeLine, String deathsLine, String pingLine) {
            this.scoreboard = scoreboard;
            this.objective = objective;
            this.playtimeLine = playtimeLine;
            this.deathsLine = deathsLine;
            this.pingLine = pingLine;
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
