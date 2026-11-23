/*
 * BedWarsX - Copyright (c) 2026 ASHRAF ELHENAWY (ItzRealAshraf). All rights reserved.
 * Copying, re-selling, re-branding or removing the credits is not allowed.
 */
package dev.bedwarsx;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** BedWars-specific tab, hub sidebar and persistent leaderboard holograms. */
public final class UiManager {
    private static final String LB_TAG = "bwx_leaderboard";

    private final BedWarsX pl;
    private final Map<UUID, Scoreboard> hubBoards = new HashMap<>();
    /** Players whose TAB header/footer we changed (so we only reset those, never other plugins' TAB). */
    private final Set<UUID> tabApplied = new HashSet<>();
    private final Map<String, List<UUID>> holograms = new HashMap<>();
    private BukkitTask task, visTask;
    private int refreshTicks = 0;
    private int saveTicks = 0;

    UiManager(BedWarsX pl) { this.pl = pl; }

    public void start() {
        purgeOrphans();
        loadLeaderboards();
        visTask = Bukkit.getScheduler().runTaskTimer(pl, new Runnable() {
            @Override public void run() { syncVisibility(); }
        }, 10L, 10L);
        task = Bukkit.getScheduler().runTaskTimer(pl, new Runnable() {
            @Override public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) updatePlayer(p);
                refreshLeaderboards();
                if (++saveTicks >= 300) { saveTicks = 0; pl.stats.save(); } // autosave stats every 5 minutes
            }
        }, 20L, 20L);
    }

    // ------------------------------------------------------------------ lobby / game separation
    /** Context of a player: the match he is in (or watching with /bw tp), or null = the BedWars lobby. */
    private Arena context(Player p) {
        Arena a = pl.mgr.of(p);
        return a != null ? a : pl.mgr.byWorld(p.getWorld());
    }

    private boolean shouldSee(Player viewer, Player target) {
        Arena av = context(viewer), at = context(target);
        if (av != at) return false;           // lobby <-> game or game <-> game: never visible (so never in the TAB)
        if (av == null) return true;
        Arena.PD dv = av.players.get(viewer.getUniqueId()), dt = av.players.get(target.getUniqueId());
        // dead / respawning players are hidden from the ones who are still playing
        if (dt != null && (!dt.alive || dt.respawning) && dv != null && dv.alive && !dv.respawning) return false;
        return true;
    }

    /** Makes every player see (and list in the TAB) only the players of his own lobby / match. */
    public void syncVisibility() {
        if (!pl.tabCfg.getBoolean("separate-lobby-and-games", true)) return;
        List<Player> all = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player v : all) {
            for (Player t : all) {
                if (v == t) continue;
                boolean want = shouldSee(v, t);
                boolean sees = v.canSee(t);
                if (want && !sees) v.showPlayer(pl, t);
                else if (!want && sees) v.hidePlayer(pl, t);
            }
        }
    }

    /** Re-sync on the next tick (used right after players join / leave a match). */
    public void syncVisibilitySoon() {
        if (!pl.isEnabled()) return; // plugin is shutting down
        Bukkit.getScheduler().runTask(pl, new Runnable() { @Override public void run() { syncVisibility(); } });
    }

    public void stop() {
        if (task != null) task.cancel();
        if (visTask != null) visTask.cancel();
        removeAllHolograms();
    }

    /** Refresh configurable UI and persistent leaderboards without restarting running matches. */
    public void reload() {
        removeAllHolograms();
        purgeOrphans();
        Set<UUID> had = new HashSet<>(hubBoards.keySet());
        hubBoards.clear();
        loadLeaderboards();
        for (Player p : Bukkit.getOnlinePlayers()) {
            // Keep active-match scoreboards intact; match logic owns those boards.
            if (had.contains(p.getUniqueId()) && pl.mgr.of(p) == null) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            updatePlayer(p);
        }
    }

    /** Called when a player disconnects. */
    public void forget(UUID id) {
        hubBoards.remove(id);
        tabApplied.remove(id);
    }

    private boolean isBedWarsPlayer(Player p) {
        return pl.mgr.of(p) != null || isBedWarsHub(p.getWorld());
    }

    private boolean isBedWarsHub(World world) {
        String spawn = pl.getConfig().getString("hub.spawn", "");
        // No /bw sethub yet: the whole server counts as the BedWars lobby, so the TAB and the
        // scoreboard work right after the plugin is installed.
        if (spawn == null || spawn.isEmpty()) return true;
        String[] parts = spawn.split(";", 2);
        return parts.length == 2 && world.getName().equals(parts[0]);
    }

    /** Applies TAB + scoreboard to a player right now (used on join, no need to wait for the 1s timer). */
    public void refresh(Player p) {
        if (p != null && p.isOnline()) updatePlayer(p);
    }

    private int playersInGames() {
        int n = 0;
        for (Arena a : pl.mgr.list()) n += a.players.size();
        return n;
    }

    private static String fit(String text, int max) {
        if (text.length() <= max) return text;
        String t = text.substring(0, max);
        if (t.endsWith("\u00A7")) t = t.substring(0, max - 1); // never cut a colour code in half
        return t;
    }

    // ------------------------------------------------------------------ TAB
    private void updatePlayer(Player p) {
        UUID id = p.getUniqueId();
        if (!isBedWarsPlayer(p)) {
            if (hubBoards.remove(id) != null) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            if (tabApplied.remove(id)) {
                p.setPlayerListHeaderFooter("", "");
                p.setPlayerListName(p.getName());
            }
            return;
        }
        Arena in = pl.mgr.of(p);
        String sec = in != null ? "game" : (!pl.tabCfg.isSet("hub") && pl.tabCfg.isSet("lobby") ? "lobby" : "hub"); // "lobby" = old tab.yml
        if (!pl.tabCfg.getBoolean(sec + ".enabled", true)) {
            if (tabApplied.remove(id)) p.setPlayerListHeaderFooter("", "");
        } else {
            tabApplied.add(id);
            int inGames = playersInGames();
            Party party = pl.parties.of(p);
            String partyText = party == null ? "None" : String.valueOf(party.size());
            String header = formatTab(String.join("\n", Cfg.lines(pl.tabCfg, sec + ".header")), p, in, inGames, partyText);
            String footer = formatTab(String.join("\n", Cfg.lines(pl.tabCfg, sec + ".footer")), p, in, inGames, partyText);
            p.setPlayerListHeaderFooter(U.c(header), U.c(footer));
        }

        // In a match the arena scoreboard assigns team colours to player names in the tab list.
        if (pl.mgr.of(p) != null) return;
        p.setPlayerListName(p.getName());
        updateHubBoard(p);
    }

    private String formatTab(String input, Player p, Arena in, int inGames, String partyText) {
        UUID u = p.getUniqueId();
        Arena.PD pd = in == null ? null : in.players.get(u);
        String team = pd == null || pd.team == null ? "&7-" : pd.team.chat + pd.team.display;
        input = input.replace("%map%", in == null ? "-" : in.name).replace("%mode%", in == null ? "-" : in.mode())
                .replace("%players%", in == null ? "0" : String.valueOf(in.players.size()))
                .replace("%max%", in == null ? "0" : String.valueOf(in.maxPlayers())).replace("%team%", team);
        return input.replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%ingame%", String.valueOf(inGames))
                .replace("%player%", p.getName())
                .replace("%level%", String.valueOf(pl.stats.level(u)))
                .replace("%wins%", String.valueOf(pl.stats.get(u, "wins")))
                .replace("%kills%", String.valueOf(pl.stats.get(u, "kills")))
                .replace("%beds%", String.valueOf(pl.stats.get(u, "beds")))
                .replace("%finals%", String.valueOf(pl.stats.get(u, "finals")))
                .replace("%deaths%", String.valueOf(pl.stats.get(u, "deaths")))
                .replace("%games%", String.valueOf(pl.stats.get(u, "games")))
                .replace("%losses%", String.valueOf(pl.stats.get(u, "losses")))
                .replace("%xp%", String.valueOf(pl.stats.get(u, "xp")))
                .replace("%coins%", String.valueOf(pl.stats.get(u, "coins")))
                .replace("%streak%", String.valueOf(pl.stats.get(u, "streak")))
                .replace("%beststreak%", String.valueOf(pl.stats.get(u, "beststreak")))
                .replace("%progress%", String.valueOf(pl.stats.percent(u)))
                .replace("%party%", partyText);
    }

    // ------------------------------------------------------------------ hub scoreboard
    private void updateHubBoard(Player p) {
        Scoreboard sb = hubBoards.get(p.getUniqueId());
        if (sb == null) {
            sb = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective ob = sb.registerNewObjective("bwxhub", "dummy", U.c(pl.boardCfg.getString("hub.title", "&b&lBEDWARSX")));
            ob.setDisplaySlot(DisplaySlot.SIDEBAR);
            for (int i = 0; i < 15; i++) sb.registerNewTeam("h" + i).addEntry(ChatColor.values()[i].toString() + ChatColor.RESET);
            hubBoards.put(p.getUniqueId(), sb);
        }
        Objective ob = sb.getObjective(DisplaySlot.SIDEBAR);
        if (ob == null) return;
        List<String> lines = Cfg.lines(pl.boardCfg, "hub.lines");
        ob.setDisplayName(U.c(pl.boardCfg.getString("hub.title", "&b&lBEDWARSX")));
        Party hp = pl.parties.of(p);
        UUID u = p.getUniqueId();
        int inGames = playersInGames();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i)
                    .replace("%player%", p.getName())
                    .replace("%level%", String.valueOf(pl.stats.level(u)))
                    .replace("%wins%", String.valueOf(pl.stats.get(u, "wins")))
                    .replace("%kills%", String.valueOf(pl.stats.get(u, "kills")))
                    .replace("%beds%", String.valueOf(pl.stats.get(u, "beds")))
                    .replace("%finals%", String.valueOf(pl.stats.get(u, "finals")))
                    .replace("%coins%", String.valueOf(pl.stats.get(u, "coins")))
                    .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                    .replace("%party%", hp == null ? "None" : String.valueOf(hp.size()))
                    .replace("%ingame%", String.valueOf(inGames));
            lines.set(i, line);
        }
        for (int i = 0; i < 15; i++) {
            String entry = ChatColor.values()[i].toString() + ChatColor.RESET;
            if (i < lines.size()) {
                sb.getTeam("h" + i).setPrefix(fit(U.c(lines.get(i)), 64));
                ob.getScore(entry).setScore(Math.min(lines.size(), 15) - i);
            } else sb.resetScores(entry);
        }
        if (p.getScoreboard() != sb) p.setScoreboard(sb);
    }

    // ------------------------------------------------------------------ leaderboards
    public void createLeaderboard(Player p, String type) {
        String id = UUID.randomUUID().toString();
        Location l = p.getLocation().clone().add(0, 0.25, 0);
        String base = "leaderboards." + id;
        pl.getConfig().set(base + ".world", l.getWorld().getName());
        pl.getConfig().set(base + ".x", l.getX());
        pl.getConfig().set(base + ".y", l.getY());
        pl.getConfig().set(base + ".z", l.getZ());
        pl.getConfig().set(base + ".type", type.toLowerCase());
        pl.saveConfig();
        spawnLeaderboard(id);
    }

    public boolean removeNearestLeaderboard(Location loc, double radius) {
        String nearest = null;
        double best = radius * radius;
        if (pl.getConfig().isConfigurationSection("leaderboards")) {
            for (String id : pl.getConfig().getConfigurationSection("leaderboards").getKeys(false)) {
                Location l = boardLocation(id);
                if (l == null || !l.getWorld().equals(loc.getWorld())) continue;
                double d = l.distanceSquared(loc);
                if (d <= best) { best = d; nearest = id; }
            }
        }
        if (nearest == null) return false;
        removeHologram(nearest);
        pl.getConfig().set("leaderboards." + nearest, null);
        pl.saveConfig();
        return true;
    }

    private void loadLeaderboards() {
        if (!pl.getConfig().isConfigurationSection("leaderboards")) return;
        for (String id : pl.getConfig().getConfigurationSection("leaderboards").getKeys(false)) spawnLeaderboard(id);
    }

    private Location boardLocation(String id) {
        String b = "leaderboards." + id;
        World w = Bukkit.getWorld(pl.getConfig().getString(b + ".world", ""));
        if (w == null) return null;
        return new Location(w, pl.getConfig().getDouble(b + ".x"), pl.getConfig().getDouble(b + ".y"), pl.getConfig().getDouble(b + ".z"));
    }

    /** Leaderboard lines (gui.yml -> leaderboard). Always `size` rows: free places are filled with placeholders. */
    private List<String> boardLines(String type) {
        int size = Math.max(1, Math.min(15, pl.guiCfg.getInt("leaderboard.size", 10)));
        List<String> lines = new ArrayList<>();
        lines.add(pl.guiCfg.getString("leaderboard.title", "&b&lBEDWARS TOP %type%").replace("%type%", type.toUpperCase()));
        String fmt = pl.guiCfg.getString("leaderboard.line", "&e%rank%. &f%player% &7- &a%value%");
        String emptyName = pl.guiCfg.getString("leaderboard.empty-name", "&7---");
        String emptyValue = pl.guiCfg.getString("leaderboard.empty-value", "0");
        List<Map.Entry<String, Integer>> top = pl.stats.top(type, size);
        for (int rank = 1; rank <= size; rank++) {
            boolean has = rank <= top.size();
            lines.add(fmt.replace("%rank%", "" + rank)
                    .replace("%player%", has ? top.get(rank - 1).getKey() : emptyName)
                    .replace("%value%", has ? "" + top.get(rank - 1).getValue() : emptyValue));
        }
        return lines;
    }

    /** Spawns the hologram (only when its chunk is loaded). Stands are NOT saved with the world and are tagged. */
    private void spawnLeaderboard(String id) {
        Location loc = boardLocation(id);
        if (loc == null) return;
        removeHologram(id);
        World w = loc.getWorld();
        if (!w.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;
        String type = pl.getConfig().getString("leaderboards." + id + ".type", "wins");
        List<String> lines = boardLines(type);
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Location at = loc.clone().add(0, (lines.size() - i - 1) * 0.26, 0);
            ArmorStand stand = w.spawn(at, ArmorStand.class);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setSmall(true);
            stand.setMarker(true);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setPersistent(false);
            stand.addScoreboardTag(LB_TAG);
            stand.setCustomName(U.c(lines.get(i)));
            stand.setCustomNameVisible(true);
            ids.add(stand.getUniqueId());
        }
        holograms.put(id, ids);
    }

    /** Update the existing hologram in place; respawn only if a stand is gone (chunk unloaded, killed...). */
    private void refreshOne(String id) {
        Location loc = boardLocation(id);
        if (loc == null) { removeHologram(id); return; }
        String type = pl.getConfig().getString("leaderboards." + id + ".type", "wins");
        List<String> lines = boardLines(type);
        List<UUID> ids = holograms.get(id);
        if (ids == null || ids.size() != lines.size()) { spawnLeaderboard(id); return; }
        List<ArmorStand> stands = new ArrayList<>();
        for (UUID uuid : ids) {
            Entity e = Bukkit.getEntity(uuid);
            if (!(e instanceof ArmorStand) || !e.isValid()) { spawnLeaderboard(id); return; }
            stands.add((ArmorStand) e);
        }
        for (int i = 0; i < stands.size(); i++) stands.get(i).setCustomName(U.c(lines.get(i)));
    }

    private void removeHologram(String id) {
        List<UUID> old = holograms.remove(id);
        if (old != null) for (UUID uuid : old) { Entity e = Bukkit.getEntity(uuid); if (e != null) e.remove(); }
    }

    private void removeAllHolograms() {
        for (String id : new ArrayList<>(holograms.keySet())) removeHologram(id);
        holograms.clear();
    }

    private void refreshLeaderboards() {
        if (!pl.getConfig().isConfigurationSection("leaderboards")) return;
        refreshTicks++;
        if (refreshTicks < 30) return;
        refreshTicks = 0;
        for (String id : pl.getConfig().getConfigurationSection("leaderboards").getKeys(false)) refreshOne(id);
    }

    private Set<UUID> trackedIds() {
        Set<UUID> t = new HashSet<>();
        for (List<UUID> l : holograms.values()) t.addAll(l);
        return t;
    }

    /** Our hologram stand? Also recognises stands left behind by older versions (they were saved in the world). */
    private boolean isLeaderboardStand(ArmorStand st) {
        if (st.getScoreboardTags().contains(LB_TAG)) return true;
        if (st.isVisible() || !st.isMarker() || !st.isSmall() || st.hasGravity()) return false;
        String n = st.getCustomName();
        if (n == null) return false;
        n = ChatColor.stripColor(n);
        return n.startsWith("BEDWARS TOP ") || n.equals("No stats yet") || n.matches("\\d+\\. .+ - \\d+");
    }

    /** Remove leaderboard stands that are not ours (duplicates / leftovers from crashes or old versions). */
    private void purgeOrphans() {
        Set<UUID> tracked = trackedIds();
        for (World w : Bukkit.getWorlds())
            for (ArmorStand st : w.getEntitiesByClass(ArmorStand.class))
                if (!tracked.contains(st.getUniqueId()) && isLeaderboardStand(st)) st.remove();
    }

    /** Chunk loaded: clean leftovers in it and (re)spawn the leaderboards that live there. */
    public void onChunkLoad(Chunk c) {
        Set<UUID> tracked = trackedIds();
        for (Entity e : c.getEntities())
            if (e instanceof ArmorStand && !tracked.contains(e.getUniqueId()) && isLeaderboardStand((ArmorStand) e)) e.remove();
        if (!pl.getConfig().isConfigurationSection("leaderboards")) return;
        for (String id : pl.getConfig().getConfigurationSection("leaderboards").getKeys(false)) {
            Location l = boardLocation(id);
            if (l == null || !l.getWorld().equals(c.getWorld())) continue;
            if ((l.getBlockX() >> 4) != c.getX() || (l.getBlockZ() >> 4) != c.getZ()) continue;
            final String boardId = id;
            Bukkit.getScheduler().runTask(pl, new Runnable() {
                @Override public void run() { refreshOne(boardId); }
            });
        }
    }
}
