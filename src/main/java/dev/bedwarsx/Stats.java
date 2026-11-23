/*
 * BedWarsX - Copyright (c) 2026 ASHRAF ELHENAWY (ItzRealAshraf). All rights reserved.
 * Copying, re-selling, re-branding or removing the credits is not allowed.
 */
package dev.bedwarsx;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Stats {
    private final File file;
    private final YamlConfiguration y;


    public Stats(BedWarsX pl) {
        pl.getDataFolder().mkdirs();
        file = new File(pl.getDataFolder(), "stats.yml");
        y = YamlConfiguration.loadConfiguration(file);
    }

    public int get(UUID u, String k) { return y.getInt(u + "." + k, 0); }

    public void add(UUID u, String k, int n) {
        y.set(u + "." + k, get(u, k) + n);
        if (n > 0 && WEEKLY_KEYS.contains(k)) { // the same counter for the weekly missions (reset every Monday)
            weekCheck(u);
            y.set(u + ".w." + k, y.getInt(u + ".w." + k, 0) + n);
        }
    }

    // ---------------------------------------------------------------- weekly missions
    /** Stats that also count towards the weekly missions. */
    private static final java.util.Set<String> WEEKLY_KEYS = new java.util.HashSet<>(java.util.Arrays.asList(
            "kills", "finals", "beds", "wins", "games", "losses", "deaths", "voidkills", "flawless"));

    /** Number of the current week; a new week starts every Monday (server time). */
    public static int currentWeek() { return (int) ((java.time.LocalDate.now().toEpochDay() + 3) / 7); }

    /** Days left until the weekly missions reset (1..7). */
    public static int weekDaysLeft() { return 7 - (int) ((java.time.LocalDate.now().toEpochDay() + 3) % 7); }

    /** New week -> the weekly counters and completed weekly missions of this player are wiped (lifetime stats are NOT touched). */
    private void weekCheck(UUID u) {
        int w = currentWeek();
        if (y.getInt(u + ".wid", 0) != w) {
            y.set(u + ".w", null);
            y.set(u + ".wach", null);
            y.set(u + ".wid", w);
        }
    }

    private boolean thisWeek(UUID u) { return y.getInt(u + ".wid", 0) == currentWeek(); }

    /** Progress of a stat during the current week. */
    public int weekly(UUID u, String k) { return thisWeek(u) ? y.getInt(u + ".w." + k, 0) : 0; }

    public boolean hasWeekly(UUID u, String id) { return thisWeek(u) && y.getStringList(u + ".wach").contains(id); }

    public void giveWeekly(UUID u, String id) {
        weekCheck(u);
        List<String> l = new ArrayList<>(y.getStringList(u + ".wach"));
        if (!l.contains(id)) { l.add(id); y.set(u + ".wach", l); }
    }

    public void setName(UUID u, String name) { y.set(u + ".name", name); }

    /** Player's personal Quick Buy list (extra feature: shift-click in the shop to edit). */
    public List<String> fav(UUID u) {
        List<String> l = y.getStringList(u + ".quickbuy");
        if (l.isEmpty()) {
            l = new ArrayList<>();
            l.addAll(Menus.DEFAULT_QUICK);
        }
        return l;
    }

    public void toggleFav(UUID u, String id) {
        List<String> l = new ArrayList<>(fav(u));
        if (l.contains(id)) l.remove(id);
        else if (l.size() < 36) l.add(id);
        y.set(u + ".quickbuy", l);
    }

    public List<Map.Entry<String, Integer>> top(String key, int n) {
        Map<String, Integer> m = new HashMap<>();
        for (String u : y.getKeys(false)) {
            int v = y.getInt(u + "." + key, 0);
            if (v > 0) m.put(y.getString(u + ".name", u), v);
        }
        List<Map.Entry<String, Integer>> l = new ArrayList<>(m.entrySet());
        Collections.sort(l, new Comparator<Map.Entry<String, Integer>>() {
            @Override public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return b.getValue() - a.getValue();
            }
        });
        return l.size() > n ? l.subList(0, n) : l;
    }

    public String nameOf(UUID u) { return y.getString(u + ".name", u.toString().substring(0, 8)); }

    public int level(UUID u) { return 1 + (int) Math.sqrt(get(u, "xp") / 100.0); }

    public void xp(Player p, int n) {
        if (n <= 0) return;
        UUID u = p.getUniqueId();
        int before = level(u);
        add(u, "xp", n);
        if (level(u) > before) {
            p.sendMessage(Msg.m("level-up", "%level%", "" + level(u)));
            U.sound(p, "entity.player.levelup", 1f, 1f);
        }
    }

    // ---------------------------------------------------------------- level / progress
    /** XP needed to REACH a level (level 1 = 0 xp). Matches level() = 1 + sqrt(xp / 100). */
    public static int xpForLevel(int level) { int l = Math.max(1, level) - 1; return l * l * 100; }

    /** XP collected inside the current level. */
    public int xpInto(UUID u) { return get(u, "xp") - xpForLevel(level(u)); }

    /** XP the current level needs in total to reach the next one. */
    public int xpNeeded(UUID u) { int l = level(u); return xpForLevel(l + 1) - xpForLevel(l); }

    /** Progress to the next level, 0..100. */
    public int percent(UUID u) {
        int need = xpNeeded(u);
        return need <= 0 ? 100 : Math.max(0, Math.min(100, (int) Math.floor(xpInto(u) * 100.0 / need)));
    }

    /** Coloured progress bar, e.g. &a■■■■■&7■■■■■ */
    public String bar(UUID u, int len) {
        int on = Math.round(percent(u) / 100f * len);
        StringBuilder sb = new StringBuilder("&a");
        for (int i = 0; i < len; i++) {
            if (i == on) sb.append("&7");
            sb.append("\u25A0");
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- game results
    /** A finished game that the player won: wins, win streak and best win streak. */
    public void win(UUID u) {
        add(u, "games", 1);
        add(u, "wins", 1);
        int st = get(u, "streak") + 1;
        y.set(u + ".streak", st);
        if (st > get(u, "beststreak")) y.set(u + ".beststreak", st);
    }

    /** A finished game that the player lost (or left): the win streak is broken. */
    public void lose(UUID u) {
        add(u, "games", 1);
        add(u, "losses", 1);
        y.set(u + ".streak", 0);
    }

    /** Score of one game: kills + final kills x2 + beds x3 (weights in config.yml -> stats.performance). */
    public int score(BedWarsX pl, int kills, int finals, int beds) {
        return kills * pl.getConfig().getInt("stats.performance.kill", 1)
                + finals * pl.getConfig().getInt("stats.performance.final-kill", 2)
                + beds * pl.getConfig().getInt("stats.performance.bed", 3);
    }

    /** Remembers the best single game (highest score) with its kills / finals / beds. */
    public void game(BedWarsX pl, UUID u, int kills, int finals, int beds) {
        int sc = score(pl, kills, finals, beds);
        if (sc <= 0 || sc <= get(u, "bestscore")) return;
        y.set(u + ".bestscore", sc);
        y.set(u + ".bestkills", kills);
        y.set(u + ".bestfinals", finals);
        y.set(u + ".bestbeds", beds);
    }

    public String ratio(int a, int b) {
        return String.format(java.util.Locale.US, "%.2f", b <= 0 ? (double) a : (double) a / b);
    }

    // ---------------------------------------------------------------- coins + cosmetics
    public int coins(UUID u) { return get(u, "coins"); }

    public void setCoins(UUID u, int n) { y.set(u + ".coins", Math.max(0, n)); }

    public boolean owns(UUID u, String key) { return y.getStringList(u + ".cos.owned").contains(key); }

    public void unlock(UUID u, String key) {
        List<String> l = new ArrayList<>(y.getStringList(u + ".cos.owned"));
        if (!l.contains(key)) { l.add(key); y.set(u + ".cos.owned", l); }
    }

    /** Selected cosmetic id of a category, "" when none. */
    public String selected(UUID u, String cat) { return y.getString(u + ".cos.sel." + cat, ""); }

    public void select(UUID u, String cat, String id) { y.set(u + ".cos.sel." + cat, id == null || id.isEmpty() ? null : id); }

    // ---------------------------------------------------------------- achievements
    public boolean hasAch(UUID u, String id) { return y.getStringList(u + ".ach").contains(id); }

    public void giveAch(UUID u, String id) {
        List<String> l = new ArrayList<>(y.getStringList(u + ".ach"));
        if (!l.contains(id)) { l.add(id); y.set(u + ".ach", l); }
    }

    public int achCount(UUID u) { return y.getStringList(u + ".ach").size(); }

    public void save() {
        try { y.save(file); } catch (IOException ignored) {}
    }
}
