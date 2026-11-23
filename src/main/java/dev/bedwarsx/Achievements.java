/*
 * BedWarsX - Copyright (c) 2026 ASHRAF ELHENAWY (ItzRealAshraf). All rights reserved.
 * Copying, re-selling, re-branding or removing the credits is not allowed.
 */
package dev.bedwarsx;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Achievements: every one has a goal (a stat reaching a number) and rewards XP (which raises the BedWars level) + Coins.
 * Everything is defined in achievements.yml. Progress is read from the player's stats, so old players unlock the
 * achievements they already earned the next time they join (check-on-join).
 *
 * stat: kills, finals, beds, wins, games, beststreak, level, voidkills, flawless (lifetime)
 *       game-kills, game-finals, game-beds (inside ONE match)
 */
public final class Achievements {
    public static final class Ach {
        public final String id, name, desc, stat;
        public final Material icon;
        public final int target, xp, coins;
        /** Weekly mission: progress and completion reset every Monday (defined in weekly.yml). */
        public final boolean weekly;

        Ach(String id, String name, String desc, String stat, Material icon, int target, int xp, int coins, boolean weekly) {
            this.id = id; this.name = name; this.desc = desc; this.stat = stat;
            this.icon = icon; this.target = target; this.xp = xp; this.coins = coins; this.weekly = weekly;
        }

        public boolean perGame() { return stat.startsWith("game-"); }
    }

    private final BedWarsX pl;
    private final List<Ach> list = new ArrayList<>();
    private boolean enabled = true, joinCheck = true, title = true;

    public Achievements(BedWarsX pl) { this.pl = pl; }

    public void load(YamlConfiguration y) {
        list.clear();
        enabled = y.getBoolean("enabled", true);
        joinCheck = y.getBoolean("check-on-join", true);
        title = y.getBoolean("title", true);
        boolean ar = "ar".equalsIgnoreCase(pl.getConfig().getString("language", "en"));
        // weekly missions first (they are shown at the top of the menu), then the lifetime achievements
        YamlConfiguration wk = pl.weeklyCfg;
        if (wk != null && wk.getBoolean("enabled", true)) read(wk.getConfigurationSection("missions"), true, ar);
        read(y.getConfigurationSection("achievements"), false, ar);
    }

    private void read(ConfigurationSection sec, boolean weekly, boolean ar) {
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s == null || !s.getBoolean("enabled", true)) continue;
            Material m = Material.matchMaterial(s.getString("icon", "PAPER"));
            String name = ar && s.isSet("name-ar") ? s.getString("name-ar") : s.getString("name", id);
            String desc = ar && s.isSet("desc-ar") ? s.getString("desc-ar") : s.getString("desc", "");
            int target = Math.max(1, s.getInt("target", 1));
            list.add(new Ach(weekly ? "weekly-" + id : id, name, desc, s.getString("stat", "wins").toLowerCase(), m == null ? Material.PAPER : m,
                    target, Math.max(0, s.getInt("xp", 0)), Math.max(0, s.getInt("coins", 0)), weekly));
        }
    }

    public boolean enabled() { return enabled; }

    public boolean joinCheck() { return enabled && joinCheck; }

    public List<Ach> all() { return Collections.unmodifiableList(list); }

    /** Completed lifetime achievements (weekly missions are counted separately). */
    public int count(UUID u) {
        int n = 0;
        for (Ach a : list) if (!a.weekly && done(u, a)) n++;
        return n;
    }

    /** Number of lifetime achievements. */
    public int total() {
        int n = 0;
        for (Ach a : list) if (!a.weekly) n++;
        return n;
    }

    public int weeklyCount(UUID u) {
        int n = 0;
        for (Ach a : list) if (a.weekly && done(u, a)) n++;
        return n;
    }

    public int weeklyTotal() { return list.size() - total(); }

    public boolean done(UUID u, Ach a) { return a.weekly ? pl.stats.hasWeekly(u, a.id) : pl.stats.hasAch(u, a.id); }

    private void give(UUID u, Ach a) { if (a.weekly) pl.stats.giveWeekly(u, a.id); else pl.stats.giveAch(u, a.id); }

    /** Current progress of a stat (d = the match data of the player, or null outside a match). */
    public int value(UUID u, Ach a, Arena.PD d) {
        String stat = a.stat;
        switch (stat) {
            case "level": return pl.stats.level(u);
            case "game-kills": return d == null ? 0 : d.kills;
            case "game-finals": return d == null ? 0 : d.finals;
            case "game-beds": return d == null ? 0 : d.beds;
            default: return a.weekly ? pl.stats.weekly(u, stat) : pl.stats.get(u, stat);
        }
    }

    /** Unlocks every achievement the player reached (and gives the rewards). Safe to call as often as needed. */
    public void check(Player p, Arena.PD d) {
        if (!enabled || p == null || !p.isOnline()) return;
        UUID u = p.getUniqueId();
        boolean any = false, again;
        int guard = 0;
        do { // the XP reward can raise the level, which can unlock a level achievement in the next round
            again = false;
            for (Ach a : list) {
                if (done(u, a) || value(u, a, d) < a.target) continue;
                give(u, a);
                award(p, a);
                again = true;
                any = true;
            }
        } while (again && ++guard < 5);
        if (any) pl.stats.save();
    }

    public void check(Player p) { check(p, null); }

    private void award(Player p, Ach a) {
        UUID u = p.getUniqueId();
        if (a.coins > 0) pl.stats.add(u, "coins", a.coins);
        p.sendMessage("");
        p.sendMessage(Msg.m(a.weekly ? "ach-weekly-unlocked" : "ach-unlocked", "%name%", a.name));
        if (!a.desc.isEmpty()) p.sendMessage(Msg.m("ach-desc", "%desc%", a.desc));
        p.sendMessage(Msg.m("ach-reward", "%xp%", "" + a.xp, "%coins%", "" + a.coins));
        p.sendMessage("");
        U.sound(p, "ui.toast.challenge_complete", 1f, 1f);
        if (title && pl.mgr.of(p) == null) U.title(p, Msg.m("ach-title"), "&e" + a.name);
        if (a.xp > 0) pl.stats.xp(p, a.xp); // last: it may send the level-up message
    }
}
