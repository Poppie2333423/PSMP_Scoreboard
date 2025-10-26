package de.popcornsmp.scoreboard.rank;

import org.bukkit.ChatColor;

public enum Rank {
    OWNER("owner", "§4[Owner] §4", ChatColor.DARK_RED, "r0Owner", true),
    ADMIN("admin", "§c[Admin] §c", ChatColor.RED, "r1Admin", true),
    SUPPORT("support", "§b[Support] §b", ChatColor.AQUA, "r2Support", false),
    POPCORN("popcorn", "§6§l[Popcorn] §6§l", ChatColor.GOLD, "r3Popcorn", false),
    ELITE("elite", "§5[Elite] §5", ChatColor.DARK_PURPLE, "r4Elite", false),
    PREMIUM("premium", "§6[Premium] §6", ChatColor.GOLD, "r5Premium", false),
    SPIELER("spieler", "§7[Spieler] §7", ChatColor.GRAY, "r6Spieler", false);

    private final String key;
    private final String prefix;
    private final ChatColor nameColor;
    private final String teamName;
    private final boolean grantOp;

    Rank(String key, String prefix, ChatColor nameColor, String teamName, boolean grantOp) {
        this.key = key;
        this.prefix = prefix;
        this.nameColor = nameColor;
        this.teamName = teamName;
        this.grantOp = grantOp;
    }

    public String getKey() {
        return key;
    }

    public String getPrefix() {
        return prefix;
    }

    public ChatColor getNameColor() {
        return nameColor;
    }

    public String getTeamName() {
        return teamName;
    }

    public boolean shouldGrantOp() {
        return grantOp;
    }

    public static Rank fromKey(String input) {
        for (Rank rank : values()) {
            if (rank.key.equalsIgnoreCase(input)) {
                return rank;
            }
        }
        return null;
    }
}
