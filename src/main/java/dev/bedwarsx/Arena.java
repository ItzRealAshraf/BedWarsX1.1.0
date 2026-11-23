/*
 * BedWarsX - Copyright (c) 2026 ASHRAF ELHENAWY (ItzRealAshraf). All rights reserved.
 * Copying, re-selling, re-branding or removing the credits is not allowed.
 */
package dev.bedwarsx;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/** One arena = one map definition + (at most) one running game instance on a temporary copy of the world. */
public class Arena {

    public enum State { WAITING, STARTING, PLAYING, ENDING }

    public enum Trap {
        TRAP("It's a trap!"), COUNTER("Counter-Offensive"), ALARM("Alarm"), FATIGUE("Miner Fatigue");
        public final String label;
        Trap(String l) { label = l; }
    }

    /** Team (setup data + runtime upgrades). */
    public static class BT {
        public final TeamColor color;
        public String spawn, bed;
        /** Setup-only data: custom team name, item-shop NPC, upgrade NPC and the team iron/gold generator spot. */
        public String name, shop, upgrade, gen;
        public boolean bedAlive = true, heal;
        public int sharp, prot, miner, forge;
        public final List<Trap> traps = new ArrayList<>();
        public long trapCd;
        public double ironP, goldP, emP;
        public BT(TeamColor c) { color = c; }
        public String label() { return name == null || name.isEmpty() ? color.display : name; }
        void reset() {
            bedAlive = true; heal = false; sharp = prot = miner = forge = 0;
            traps.clear(); trapCd = 0; ironP = goldP = emP = 0;
        }
    }

    /** Per player game data. */
    public static class PD {
        public TeamColor team;
        public int armor, pick, axe, kills, finals, beds, deaths;
        public boolean shears, alive = true, respawning, chosen;
        public UUID lastHit;
        public long lastHitAt, offlineAt, voidHitAt;
        ItemStack[] inv, arm, gameInv, gameArm, ender;
        GameMode gm;
        Location loc;
        double health = 20;
        int food = 20, lvl;
        float exp;
        boolean flight;
    }

    public static class Gen {
        String type;
        Location loc;
        int tier = 1;
        double prog;
        ArmorStand h1, h2, spin;
        double baseY;
    }

    static final String[] EN = {"Diamond II", "Emerald II", "Diamond III", "Emerald III", "Bed Gone", "Sudden Death", "Game End"};
    static final String[] EK = {"diamond-2", "emerald-2", "diamond-3", "emerald-3", "bed-gone", "sudden-death", "end"};
    static final int[] ED = {360, 720, 1080, 1440, 1800, 2400, 3000};

    final BedWarsX pl;
    public final String name;

    // ---- setup data ----
    public String template, lobby, spec;
    /** Chosen game type (solo, doubles, 3v3v3v3, 4v4v4v4, 1v1, 4v4) or null when /bw setType was not run yet. */
    public String type;
    /** Waiting-lobby region corners (blocks inside are removed when the game starts), like BedWars1058 waitingPos 1/2. */
    public String waitPos1, waitPos2;
    public int min = 2, teamSize = 1, voidY = 0;
    public boolean enabled;
    public final Map<TeamColor, BT> teams = new LinkedHashMap<>();
    public final List<String> genCfg = new ArrayList<>();
    public final List<String> shopCfg = new ArrayList<>();

    // ---- runtime ----
    public State state = State.WAITING;
    public World world;
    String worldName;
    public final Map<UUID, PD> players = new LinkedHashMap<>();
    public final Set<String> placed = new HashSet<>();
    public final Map<UUID, TeamColor> mobTeam = new HashMap<>();
    public final Map<UUID, String> shopMap = new HashMap<>();
    public final Map<UUID, UUID> tntOwner = new HashMap<>();
    public final Map<UUID, Long> immune = new HashMap<>();
    final Map<UUID, Scoreboard> boards = new HashMap<>();
    final List<Gen> gens = new ArrayList<>();
    final List<Entity> spawned = new ArrayList<>();
    BukkitTask loop, spinTask;
    int countdown, elapsed;
    long tick;
    TeamColor winner;

    public Arena(BedWarsX pl, String name) { this.pl = pl; this.name = name; }

    // =====================================================================  config
    public static Arena load(BedWarsX pl, File f) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        Arena a = new Arena(pl, y.getString("name", f.getName().replace(".yml", "")));
        a.template = y.getString("template");
        a.min = y.getInt("min", 2);
        a.teamSize = y.getInt("team-size", 1);
        a.voidY = y.getInt("void-y", 0);
        a.enabled = y.getBoolean("enabled", false);
        a.lobby = y.getString("lobby");
        a.spec = y.getString("spectator");
        a.type = y.getString("type");
        a.waitPos1 = y.getString("waiting-pos1");
        a.waitPos2 = y.getString("waiting-pos2");
        if (y.isConfigurationSection("teams")) {
            for (String k : y.getConfigurationSection("teams").getKeys(false)) {
                TeamColor c = TeamColor.of(k);
                if (c == null) continue;
                BT t = new BT(c);
                t.spawn = y.getString("teams." + k + ".spawn");
                t.bed = y.getString("teams." + k + ".bed");
                t.name = y.getString("teams." + k + ".name");
                t.shop = y.getString("teams." + k + ".shop");
                t.upgrade = y.getString("teams." + k + ".upgrade");
                t.gen = y.getString("teams." + k + ".gen");
                a.teams.put(c, t);
            }
        }
        a.genCfg.addAll(y.getStringList("generators"));
        a.shopCfg.addAll(y.getStringList("shops"));
        if (a.type == null && a.teams.size() >= 2) {
            // maps made before /bw setType existed: derive the type from team count and team size
            a.type = canonType(a.mode());
        }
        return a;
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("name", name);
        y.set("template", template);
        y.set("min", min);
        y.set("team-size", teamSize);
        y.set("void-y", voidY);
        y.set("enabled", enabled);
        y.set("lobby", lobby);
        y.set("spectator", spec);
        y.set("type", type);
        y.set("waiting-pos1", waitPos1);
        y.set("waiting-pos2", waitPos2);
        for (BT t : teams.values()) {
            y.set("teams." + t.color.name() + ".name", t.name);
            y.set("teams." + t.color.name() + ".shop", t.shop);
            y.set("teams." + t.color.name() + ".upgrade", t.upgrade);
            y.set("teams." + t.color.name() + ".gen", t.gen);
            y.set("teams." + t.color.name() + ".spawn", t.spawn);
            y.set("teams." + t.color.name() + ".bed", t.bed);
        }
        y.set("generators", genCfg);
        y.set("shops", shopCfg);
        try { y.save(new File(pl.mgr.dir(), name + ".yml")); } catch (IOException e) { e.printStackTrace(); }
    }

    /** Teams a game type needs (solo/doubles = 8, 3v3v3v3/4v4v4v4 = 4, 1v1/4v4 = 2); 0 when no type is set. */
    public static int teamsOf(String type) {
        if (type == null) return 0;
        switch (type.toLowerCase()) {
            case "solo": case "doubles": return 8;
            case "3v3v3v3": case "4v4v4v4": return 4;
            case "1v1": case "4v4": return 2;
            default: return 0;
        }
    }

    public static int sizeOf(String type) {
        if (type == null) return 0;
        switch (type.toLowerCase()) {
            case "solo": case "1v1": return 1;
            case "doubles": return 2;
            case "3v3v3v3": return 3;
            case "4v4v4v4": case "4v4": return 4;
            default: return 0;
        }
    }

    public static String canonType(String type) {
        if (type == null) return null;
        switch (type.toLowerCase()) {
            case "solo": return "Solo";
            case "doubles": return "Doubles";
            case "3v3v3v3": return "3v3v3v3";
            case "4v4v4v4": return "4v4v4v4";
            case "1v1": return "1v1";
            case "4v4": return "4v4";
            default: return null;
        }
    }

    /** Team by custom name or colour name (case-insensitive). */
    public BT findTeam(String token) {
        if (token == null) return null;
        for (BT t : teams.values()) if (t.label().equalsIgnoreCase(token) || t.color.name().equalsIgnoreCase(token)) return t;
        return null;
    }

    /** Returns null if ready, otherwise what is missing. */
    public String missing() {
        if (template == null) return "template world";
        if (type == null) return "game type (edit <arena> setType)";
        if (lobby == null) return "waiting spawn (edit <arena> setWaitingSpawn)";
        if (teams.size() < 2) return "at least 2 teams (edit <arena> createTeam)";
        // fewer teams than the full mode are allowed (minimum 2): e.g. a Solo map with only 4 of the 8 teams
        if (teams.size() > teamsOf(type)) return "at most " + teamsOf(type) + " teams for " + canonType(type) + " (you have " + teams.size() + ")";
        for (BT t : teams.values()) {
            if (t.spawn == null) return "spawn of " + t.label() + " (edit <arena> setSpawn)";
            if (t.bed == null) return "bed of " + t.label() + " (edit <arena> setBed)";
        }
        if (shopCfg.isEmpty()) {
            for (BT t : teams.values()) {
                if (t.shop == null) return "shop of " + t.label() + " (edit <arena> setShop)";
                if (t.upgrade == null) return "upgrade shop of " + t.label() + " (edit <arena> setUpgrade)";
            }
        }
        return null;
    }

    public int maxPlayers() { return teams.size() * teamSize; }

    public String mode() {
        String ct = canonType(type);
        if (ct != null) return ct;
        if (teams.size() == 2) return teamSize + "v" + teamSize;
        if (teamSize == 1 && teams.size() > 2) return "Solo";
        if (teamSize == 2 && teams.size() == 8) return "Doubles";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < teams.size(); i++) { if (i > 0) sb.append("v"); sb.append(teamSize); }
        return sb.toString();
    }

    /** Spectator spawn; when /bw setspec was not used, a point above the middle of all team spawns. */
    public Location specLoc() {
        if (spec != null) return L(spec);
        double x = 0, z = 0, y = 64; int n = 0;
        for (BT t : teams.values()) {
            if (t.spawn == null) continue;
            Location l = L(t.spawn);
            x += l.getX(); z += l.getZ(); y = Math.max(y, l.getY()); n++;
        }
        if (n == 0) return L(lobby);
        return new Location(world, x / n, y + 15, z / n);
    }

    /** Removes the waiting-lobby build (region between waitingPos 1 and 2) when the match starts. */
    void clearWaitingRegion() {
        if (waitPos1 == null || waitPos2 == null || world == null) return;
        try {
            Location a = L(waitPos1), b = L(waitPos2);
            final int x1 = Math.min(a.getBlockX(), b.getBlockX()), x2 = Math.max(a.getBlockX(), b.getBlockX());
            final int y1 = Math.min(a.getBlockY(), b.getBlockY()), y2 = Math.max(a.getBlockY(), b.getBlockY());
            final int z1 = Math.min(a.getBlockZ(), b.getBlockZ()), z2 = Math.max(a.getBlockZ(), b.getBlockZ());
            final World w = world;
            new BukkitRunnable() {
                int x = x1;
                @Override public void run() {
                    // one X-slice per tick keeps big lobbies from lagging the server
                    if (x > x2 || state == State.ENDING) { cancel(); return; }
                    for (int y = y1; y <= y2; y++)
                        for (int z = z1; z <= z2; z++) {
                            Block bl = w.getBlockAt(x, y, z);
                            if (bl.getType() != Material.AIR) bl.setType(Material.AIR, false);
                        }
                    x++;
                }
            }.runTaskTimer(pl, 1L, 1L);
        } catch (Throwable t) { t.printStackTrace(); }
    }

    Location L(String s) { return U.sl(s, world); }

    public Location spawnOf(TeamColor c) { return L(teams.get(c).spawn); }

    public void broadcast(String m) {
        for (UUID u : players.keySet()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.sendMessage(U.c(m));
        }
    }

    public int countTeam(TeamColor c) {
        int n = 0;
        for (PD d : players.values()) if (d.team == c) n++;
        return n;
    }

    public int aliveCount(TeamColor c) {
        int n = 0;
        for (PD d : players.values()) if (d.team == c && d.alive) n++;
        return n;
    }

    public List<Player> online(TeamColor c) {
        List<Player> l = new ArrayList<>();
        for (Map.Entry<UUID, PD> e : players.entrySet()) {
            if (e.getValue().team != c) continue;
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null && e.getValue().offlineAt == 0) l.add(p);
        }
        return l;
    }

    // =====================================================================  world handling
    boolean loadWorld() {
        if (world != null) return true;
        try {
            worldName = "bwx_" + name.toLowerCase() + "_" + (System.currentTimeMillis() % 100000);
            File src = new File(Bukkit.getWorldContainer(), template);
            File dst = new File(Bukkit.getWorldContainer(), worldName);
            World tw = Bukkit.getWorld(template);
            if (tw != null) tw.save();
            if (!src.isDirectory()) return false;
            copyDir(src, dst);
            WorldCreator wc = new WorldCreator(worldName);
            if (tw != null) wc.environment(tw.getEnvironment());
            world = Bukkit.createWorld(wc);
            if (world == null) return false;
            world.setAutoSave(false);
            world.setDifficulty(Difficulty.NORMAL);
            world.setTime(6000);
            world.setStorm(false);
            rule("doDaylightCycle", "false");
            rule("doWeatherCycle", "false");
            rule("doMobSpawning", "false");
            rule("announceAdvancements", "false");
            rule("doImmediateRespawn", "true");
            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    void rule(String k, String v) {
        try { world.setGameRuleValue(k, v); } catch (Throwable ignored) {}
    }

    static void copyDir(File s, File d) throws IOException {
        if (!d.exists()) d.mkdirs();
        File[] fs = s.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            String n = f.getName();
            if (n.equals("uid.dat") || n.equals("session.lock") || n.equals("playerdata") || n.equals("stats")
                    || n.equals("advancements")) continue;
            File t = new File(d, n);
            if (f.isDirectory()) copyDir(f, t);
            else Files.copy(f.toPath(), t.toPath());
        }
    }

    public static void deleteDir(File f) {
        File[] fs = f.listFiles();
        if (fs != null) for (File c : fs) deleteDir(c);
        f.delete();
    }

    void unloadWorld() {
        if (world == null) return;
        World w = world;
        world = null;
        for (Player p : w.getPlayers()) p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        Bukkit.unloadWorld(w, false);
        final File dir = new File(Bukkit.getWorldContainer(), worldName);
        if (pl.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(pl, new Runnable() {
                @Override public void run() { deleteDir(dir); }
            });
        } else deleteDir(dir);
    }

    // =====================================================================  join / leave
    void backup(Player p, PD d) {
        d.inv = p.getInventory().getContents();
        d.arm = p.getInventory().getArmorContents();
        d.ender = p.getEnderChest().getContents(); // the real ender chest comes back after the match
        d.gm = p.getGameMode();
        d.loc = p.getLocation();
        d.health = p.getHealth();
        d.food = p.getFoodLevel();
        d.lvl = p.getLevel();
        d.exp = p.getExp();
        d.flight = p.getAllowFlight();
    }

    void restore(Player p, PD d) {
        boards.remove(p.getUniqueId());
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        pl.ui.syncVisibilitySoon(); // tab / visibility is handled centrally (lobby and games are separate)
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.setFlying(false);
        p.setAllowFlight(d.flight);
        p.setCollidable(true);
        if (d.gm != null) p.setGameMode(d.gm);
        if (d.inv != null) p.getInventory().setContents(d.inv);
        if (d.arm != null) p.getInventory().setArmorContents(d.arm);
        if (d.ender != null && pl.getConfig().getBoolean("clear-enderchest", true)) p.getEnderChest().setContents(d.ender); // match items are dropped
        p.setHealth(Math.max(1.0, Math.min(20.0, d.health)));
        p.setFoodLevel(d.food);
        p.setLevel(d.lvl);
        p.setExp(d.exp);
        p.setFireTicks(0);
        if (d.loc != null && d.loc.getWorld() != null) p.teleport(d.loc);
    }

    void resetPlayer(Player p, GameMode gm) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[4]);
        if (pl.getConfig().getBoolean("clear-enderchest", true)) p.getEnderChest().clear(); // every match starts with an empty ender chest
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.setGameMode(gm);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setLevel(0);
        p.setExp(0);
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.setFlying(false);
        p.setAllowFlight(false);
        p.setCollidable(true);
    }

    public boolean join(Player p) {
        if (!enabled) { p.sendMessage(Msg.m("arena-disabled")); return false; }
        if (state != State.WAITING && state != State.STARTING) { p.sendMessage(Msg.m("game-started")); return false; }
        if (players.size() >= maxPlayers()) { p.sendMessage(Msg.m("arena-full")); return false; }
        if (world == null && !loadWorld()) { p.sendMessage(Msg.m("map-error")); return false; }
        pl.tpKit.restore(p); // an admin coming from /bw tp: real items first, so the backup below is the real inventory
        PD d = new PD();
        backup(p, d);
        players.put(p.getUniqueId(), d);
        pl.mgr.byPlayer.put(p.getUniqueId(), this);
        pl.stats.setName(p.getUniqueId(), p.getName());
        d.team = smallestTeam();
        d.chosen = false;
        resetPlayer(p, GameMode.ADVENTURE);
        p.teleport(L(lobby));
        p.sendMessage(Msg.m("team-auto", "%team%", d.team.chat + d.team.display));
        HubMenus.setHot(pl, p, "hotbar.waiting.team-selector");
        HubMenus.setHot(pl, p, "hotbar.waiting.leave");
        broadcast(Msg.m("player-joined", "%player%", p.getName(), "%n%", "" + players.size(), "%max%", "" + maxPlayers()));
        if (loop == null) loop = Bukkit.getScheduler().runTaskTimer(pl, this::tickLoop, 10L, 10L);
        checkStart();
        return true;
    }

    public void selectTeam(Player p, TeamColor c) {
        PD d = players.get(p.getUniqueId());
        if (d == null || (state != State.WAITING && state != State.STARTING)) return;
        if (countTeam(c) >= teamSize && d.team != c) { p.sendMessage(Msg.m("team-full")); return; }
        d.team = c;
        d.chosen = true;
        p.sendMessage(Msg.m("team-joined", "%team%", c.chat + c.display));
        balance();
    }

    void checkStart() {
        if (state == State.WAITING && players.size() >= min) {
            state = State.STARTING;
            countdown = pl.getConfig().getInt("start-countdown", 20);
        } else if (state == State.STARTING && players.size() < min) {
            state = State.WAITING;
            broadcast(Msg.m("countdown-cancelled"));
        }
        if (state == State.STARTING && players.size() >= maxPlayers() && countdown > 10) countdown = 10;
    }

    public void forceStart() {
        if (state == State.WAITING || state == State.STARTING) {
            if (players.isEmpty()) return;
            start();
        }
    }

    /** Player runs /bw leave or clicks the leave item. */
    public void leave(Player p) {
        PD d = players.get(p.getUniqueId());
        if (d == null) return;
        if (state == State.PLAYING && d.alive) {
            broadcast(Msg.m("player-left-game", "%player%", tn(d, p)));
            pl.stats.game(pl, p.getUniqueId(), d.kills, d.finals, d.beds);
            pl.stats.lose(p.getUniqueId());
            d.alive = false;
        }
        removeFinal(p);
        if (state == State.PLAYING) checkWin();
    }

    /**
     * Disconnect. It counts as a death: the items are lost (the last attacker gets the resources).
     * Bed still there -> the player may come back and respawn from scratch. Bed gone -> final death, no way back.
     */
    public void quit(Player p) {
        PD d = players.get(p.getUniqueId());
        if (d == null) return;
        if (state == State.PLAYING && d.alive) {
            final UUID id = p.getUniqueId();
            BT t = teams.get(d.team);
            boolean fin = t == null || !t.bedAlive;
            if (!d.respawning) disconnectDeath(p, d, fin);
            else if (fin) broadcast(Msg.m("player-left-game", "%player%", tn(d, p)));
            if (fin) {
                pl.stats.game(pl, id, d.kills, d.finals, d.beds);
                pl.stats.lose(id);
                d.alive = false;
                removeFinal(p);
                if (state == State.PLAYING) checkWin();
                return;
            }
            d.respawning = true;
            d.offlineAt = System.currentTimeMillis();
            d.gameInv = null;
            d.gameArm = null;
            pl.mgr.byPlayer.remove(id);
            pl.mgr.rejoin.put(id, this);
            broadcast(Msg.m("player-disconnected", "%player%", tn(d, p), "%sec%", "" + pl.getConfig().getInt("offline-rejoin-seconds", 120)));
            restore(p, d);
            return;
        }
        removeFinal(p);
        if (state == State.PLAYING) checkWin();
    }

    /** What a disconnect does to a living player: death message, kill credit + loot for the last attacker, items gone. */
    private void disconnectDeath(Player p, PD d, boolean fin) {
        Player killer = findKiller(p, d);
        PD kd = killer != null ? players.get(killer.getUniqueId()) : null;
        if (kd != null && killer.equals(p)) kd = null;
        if (fin || kd != null) {
            String line = d.team.chat + p.getName() + " &7"
                    + (kd != null ? Msg.m("verb-default") + " " + kd.team.chat + killer.getName() : Msg.m("die-disconnect"));
            if (fin) line += " " + Msg.m("final-kill");
            broadcast(line);
        }
        d.deaths++;
        pl.stats.add(p.getUniqueId(), "deaths", 1);
        creditKill(p, killer, kd, fin);
        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[4]);
        d.gameInv = null;
        d.gameArm = null;
        d.lastHit = null;
        if (d.pick > 1) d.pick--;
        if (d.axe > 1) d.axe--;
    }

    /** A disconnected player can never come back (bed destroyed / time ran out): count the loss and forget him. */
    void eliminateOffline(UUID u) {
        PD d = players.remove(u);
        pl.mgr.rejoin.remove(u);
        if (d == null) return;
        d.alive = false;
        pl.stats.game(pl, u, d.kills, d.finals, d.beds);
        pl.stats.lose(u);
    }

    void removeFinal(Player p) {
        PD d = players.remove(p.getUniqueId());
        pl.mgr.byPlayer.remove(p.getUniqueId());
        if (d != null) restore(p, d);
        if (state == State.WAITING || state == State.STARTING) {
            broadcast(Msg.m("player-left-lobby", "%player%", p.getName(), "%n%", "" + players.size(), "%max%", "" + maxPlayers()));
            balance();
            checkStart();
        }
        if (players.isEmpty() && state != State.ENDING) finish();
    }

    public boolean rejoin(Player p) {
        PD d = players.get(p.getUniqueId());
        if (d == null || state != State.PLAYING || !d.alive || d.offlineAt == 0) return false;
        BT t = teams.get(d.team);
        if (t == null || !t.bedAlive) { // the bed was destroyed while he was away -> final death
            eliminateOffline(p.getUniqueId());
            p.sendMessage(Msg.m("rejoin-bed-gone"));
            checkWin();
            return false;
        }
        backup(p, d);
        d.offlineAt = 0;
        d.gameInv = null;
        d.gameArm = null;
        pl.mgr.byPlayer.put(p.getUniqueId(), this);
        pl.mgr.rejoin.remove(p.getUniqueId());
        resetPlayer(p, GameMode.ADVENTURE);
        d.respawning = true;
        spectate(p, false);
        broadcast(Msg.m("player-reconnected", "%player%", tn(d, p)));
        startRespawn(p.getUniqueId());
        return true;
    }

    // =====================================================================  loop
    void tickLoop() {
        tick++;
        if (state == State.PLAYING) { gensTick(); voidCheck(); }
        if (tick % 2 == 0) second();
    }

    void second() {
        switch (state) {
            case STARTING:
                countdown--;
                if (countdown <= 0) start();
                else if (countdown <= 5 || countdown % 10 == 0) {
                    broadcast(Msg.m("starting-in", "%sec%", "" + countdown));
                    for (UUID u : players.keySet()) {
                        Player p = Bukkit.getPlayer(u);
                        if (p != null) U.sound(p, "block.note_block.hat", 1f, 1f);
                    }
                }
                break;
            case PLAYING:
                elapsed++;
                events();
                effects();
                offlineCheck();
                break;
            case ENDING:
                countdown--;
                if (countdown <= 0) finish();
                break;
            default:
                break;
        }
        refreshBoards();
    }

    void voidCheck() {
        for (Map.Entry<UUID, PD> e : new ArrayList<>(players.entrySet())) {
            PD d = e.getValue();
            if (!d.alive || d.respawning || d.offlineAt > 0) continue;
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null && p.getWorld().equals(world) && p.getLocation().getY() < voidY) {
                Player k = null;
                if (d.lastHit != null && System.currentTimeMillis() - d.lastHitAt < 15000) k = Bukkit.getPlayer(d.lastHit);
                if (!pl.getConfig().getBoolean("void.damage-mode", true)) { onDeath(p, k, EntityDamageEvent.DamageCause.VOID); continue; }
                // damage mode: the void hurts every few ticks instead of killing at once; the player dies when the health runs out
                long now = System.currentTimeMillis();
                long gap = Math.max(1, pl.getConfig().getInt("void.interval-ticks", 10)) * 50L;
                if (now - d.voidHitAt < gap) continue;
                d.voidHitAt = now;
                double dmg = Math.max(0.5, pl.getConfig().getDouble("void.damage", 4.0));
                if (p.getHealth() - dmg <= 0) { onDeath(p, k, EntityDamageEvent.DamageCause.VOID); continue; }
                p.setHealth(p.getHealth() - dmg);
                try { p.playEffect(org.bukkit.EntityEffect.HURT); } catch (Throwable ignored) { /* effect missing on this version */ }
                U.sound(p, "entity.player.hurt", 1f, 1f);
            }
        }
    }

    void offlineCheck() {
        long now = System.currentTimeMillis();
        long to = pl.getConfig().getInt("offline-rejoin-seconds", 120) * 1000L;
        List<UUID> rm = new ArrayList<>();
        for (Map.Entry<UUID, PD> e : players.entrySet())
            if (e.getValue().offlineAt > 0 && now - e.getValue().offlineAt > to) rm.add(e.getKey());
        if (rm.isEmpty()) return;
        for (UUID u : rm) eliminateOffline(u);
        checkWin();
    }

    // =====================================================================  start
    void start() {
        state = State.PLAYING;
        elapsed = 0;
        for (BT t : teams.values()) t.reset();
        for (PD d : players.values()) if (d.team == null) d.team = smallestTeam();
        for (BT t : teams.values()) {
            t.bedAlive = countTeam(t.color) > 0;
            if (!t.bedAlive) removeBedBlocks(t);
        }
        for (Map.Entry<UUID, PD> e : players.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            PD d = e.getValue();
            d.alive = true;
            resetPlayer(p, GameMode.SURVIVAL);
            giveGear(p, d);
            p.teleport(spawnOf(d.team));
            U.title(p, Msg.m("title-start"), Msg.m("subtitle-start"));
            p.sendMessage(Msg.m("team-assigned", "%team%", d.team.chat + d.team.display));
            U.sound(p, "entity.ender_dragon.growl", 0.6f, 1f);
        }
        broadcast("&7Map: &e" + name);
        clearWaitingRegion();
        spawnShops();
        pl.cos.applyBeds(this);
        spawnGens();
        refreshBoards();
    }

    TeamColor smallestTeam() {
        TeamColor best = null;
        int bn = 999;
        for (TeamColor c : teams.keySet()) {
            int n = countTeam(c);
            if (n < teamSize && n < bn) { bn = n; best = c; }
        }
        return best != null ? best : teams.keySet().iterator().next();
    }

    void spawnShops() {
        List<String> all = new ArrayList<>(shopCfg);
        for (BT t : teams.values()) {
            if (t.shop != null) all.add("ITEM@" + t.shop + "@" + t.color.name());
            if (t.upgrade != null) all.add("UPGRADE@" + t.upgrade + "@" + t.color.name());
        }
        for (String s : all) {
            try {
                String[] a = s.split("@");
                Location l = U.sl(a[1], world);
                l.getChunk().load(true);
                // a team member's equipped "Shop NPC" cosmetic changes how this team's shop / upgrade NPC looks
                Cosmetics.Item skin = a.length > 2 ? pl.cos.npcFor(this, teams.get(TeamColor.valueOf(a[2]))) : null;
                Entity v = pl.cos.spawnNpc(l, skin);
                if (v instanceof LivingEntity) {
                    LivingEntity le = (LivingEntity) v;
                    le.setAI(false);
                    le.setSilent(true);
                    le.setCollidable(false);
                    le.setRemoveWhenFarAway(false);
                }
                v.setInvulnerable(true);
                v.setCustomName(U.c(a[0].equals("ITEM") ? "&e&lITEM SHOP" : "&b&lTEAM UPGRADES"));
                v.setCustomNameVisible(true);
                spawned.add(v);
                shopMap.put(v.getUniqueId(), a[0]);
            } catch (Throwable t) { t.printStackTrace(); }
        }
    }

    ArmorStand stand(Location l, String n) {
        ArmorStand a = (ArmorStand) world.spawnEntity(l, EntityType.ARMOR_STAND);
        a.setVisible(false);
        a.setGravity(false);
        a.setMarker(true);
        a.setInvulnerable(true);
        a.setCustomName(U.c(n));
        a.setCustomNameVisible(true);
        spawned.add(a);
        return a;
    }

    void spawnGens() {
        gens.clear();
        for (String s : genCfg) {
            try {
                String[] a = s.split("@", 2);
                Gen g = new Gen();
                g.type = a[0];
                g.loc = U.sl(a[1], world);
                g.loc.getChunk().load(true);
                boolean dia = g.type.equals("DIAMOND");
                g.h1 = stand(g.loc.clone().add(0, 2.9, 0), Msg.m(dia ? "holo-diamond" : "holo-emerald") + " " + Msg.m("holo-tier", "%tier%", "I"));
                g.h2 = stand(g.loc.clone().add(0, 2.55, 0), upgradeText(g));
                // spinning Diamond / Emerald block above the generator
                g.baseY = g.loc.getY() + 1.3;
                ArmorStand sp = (ArmorStand) world.spawnEntity(g.loc.clone().add(0, 1.3, 0), EntityType.ARMOR_STAND);
                sp.setVisible(false);
                sp.setGravity(false);
                sp.setMarker(true);
                sp.setSmall(true);
                sp.setInvulnerable(true);
                if (sp.getEquipment() != null) sp.getEquipment().setHelmet(new ItemStack(dia ? Material.DIAMOND_BLOCK : Material.EMERALD_BLOCK));
                spawned.add(sp);
                g.spin = sp;
                gens.add(g);
            } catch (Throwable t) { t.printStackTrace(); }
        }
        if (spinTask != null) spinTask.cancel();
        spinTask = Bukkit.getScheduler().runTaskTimer(pl, new Runnable() {
            double ang = 0;
            @Override public void run() {
                ang += 0.3;
                for (Gen g : gens) {
                    if (g.spin == null || !g.spin.isValid()) continue;
                    g.spin.setHeadPose(new org.bukkit.util.EulerAngle(0, ang, 0));
                    Location at = g.spin.getLocation();
                    at.setY(g.baseY + Math.sin(ang / 2) * 0.12);
                    g.spin.teleport(at);
                }
            }
        }, 2L, 2L);
    }

    /** Base protection: true when l is inside the protected area around a team's spawn (or team generator). own = the builder's team. */
    public boolean inBase(Location l, TeamColor own) {
        if (world == null || l == null || !world.equals(l.getWorld())) return false;
        if (!pl.getConfig().getBoolean("base-protection.enabled", true)) return false;
        int r = Math.max(0, pl.getConfig().getInt("base-protection.radius", 3));
        boolean ownOk = pl.getConfig().getBoolean("base-protection.allow-own-team", false);
        for (BT t : teams.values()) {
            if (ownOk && t.color == own) continue;
            if (nearSpot(l, t.spawn, r) || nearSpot(l, t.gen, r)) return true;
        }
        return false;
    }

    private boolean nearSpot(Location l, String spot, int r) {
        if (spot == null || spot.isEmpty()) return false;
        Location c = L(spot);
        return Math.abs(l.getBlockX() - c.getBlockX()) <= r && Math.abs(l.getBlockZ() - c.getBlockZ()) <= r
                && Math.abs(l.getBlockY() - c.getBlockY()) <= r;
    }

    /** Hologram text under the generator name: when it upgrades next and how much time is left. */
    String upgradeText(Gen g) {
        if (g.tier >= 3) return Msg.m("holo-max");
        boolean dia = g.type.equals("DIAMOND");
        int[] ev = evTimes();
        int idx = dia ? (g.tier < 2 ? 0 : 2) : (g.tier < 2 ? 1 : 3);
        String[] roman = {"", "I", "II", "III"};
        return Msg.m("holo-upgrade", "%tier%", roman[g.tier + 1], "%time%", U.mmss(Math.max(0, ev[idx] - elapsed)));
    }

    // =====================================================================  generators
    void drop(Location l, Material m, int cap) {
        int c = 0;
        for (Entity en : world.getNearbyEntities(l, 1.5, 1.5, 1.5))
            if (en instanceof Item && ((Item) en).getItemStack().getType() == m) c += ((Item) en).getItemStack().getAmount();
        if (c >= cap) return;
        Item it = world.dropItem(l.clone().add(0, 0.4, 0), new ItemStack(m, 1));
        it.setVelocity(new Vector(0, 0.05, 0));
    }

    void gensTick() {
        org.bukkit.configuration.file.YamlConfiguration gc = pl.genCfg;
        double iron = gc.getDouble("team.iron.interval", 1.0);
        double gold = gc.getDouble("team.gold.interval", 5.0);
        double em = gc.getDouble("team.emerald.interval", 30.0);
        int ironMax = gc.getInt("team.iron.max-stack", 64), goldMax = gc.getInt("team.gold.max-stack", 32), emMax = gc.getInt("team.emerald.max-stack", 8);
        int emForge = gc.getInt("team.emerald.requires-forge", 3);
        double em4 = gc.getDouble("team.emerald.forge-4-speed", 1.5);
        List<Double> mult = gc.getDoubleList("forge-multipliers");
        if (mult.isEmpty()) mult = Arrays.asList(1.0, 1.5, 2.0, 2.0, 3.0);
        for (BT t : teams.values()) {
            if (countTeam(t.color) == 0) continue;
            Location l = t.gen != null ? L(t.gen) : spawnOf(t.color);
            double m = mult.get(Math.min(mult.size() - 1, t.forge));
            t.ironP += 0.5 * m;
            t.goldP += 0.5 * m;
            while (t.ironP >= iron) { t.ironP -= iron; drop(l, Material.IRON_INGOT, ironMax); }
            while (t.goldP >= gold) { t.goldP -= gold; drop(l, Material.GOLD_INGOT, goldMax); }
            if (t.forge >= emForge) {
                t.emP += 0.5 * (t.forge >= 4 ? em4 : 1.0);
                while (t.emP >= em) { t.emP -= em; drop(l, Material.EMERALD, emMax); }
            }
        }
        for (Gen g : gens) {
            boolean dia = g.type.equals("DIAMOND");
            List<?> raw = gc.getList((dia ? "diamond" : "emerald") + ".tiers");
            double iv = dia ? 30 : 60;
            if (raw != null && !raw.isEmpty()) {
                Object o = raw.get(Math.min(raw.size(), g.tier) - 1);
                if (o instanceof Number) iv = ((Number) o).doubleValue();
            }
            g.prog += 0.5;
            if (g.prog >= iv) { g.prog = 0; drop(g.loc, dia ? Material.DIAMOND : Material.EMERALD, gc.getInt((dia ? "diamond" : "emerald") + ".max-stack", 8)); }
            if (tick % 2 == 0) {
                String[] roman = {"", "I", "II", "III"};
                if (g.h1 != null) g.h1.setCustomName(Msg.m(dia ? "holo-diamond" : "holo-emerald") + " " + Msg.m("holo-tier", "%tier%", roman[Math.min(3, g.tier)]));
                if (g.h2 != null) g.h2.setCustomName(upgradeText(g));
            }
        }
    }

    // =====================================================================  events / effects
    int[] evTimes() {
        int[] r = new int[EK.length];
        for (int i = 0; i < r.length; i++) r[i] = pl.genCfg.getInt("events." + EK[i], ED[i]);
        return r;
    }

    void events() {
        int[] ev = evTimes();
        for (int i = 0; i < ev.length; i++) {
            if (ev[i] != elapsed) continue;
            switch (i) {
                case 0: tier("DIAMOND", 2); break;
                case 1: tier("EMERALD", 2); break;
                case 2: tier("DIAMOND", 3); break;
                case 3: tier("EMERALD", 3); break;
                case 4:
                    broadcast(Msg.m("all-beds-gone"));
                    for (BT t : teams.values()) if (t.bedAlive) bedBroken(t, null);
                    break;
                case 5:
                    broadcast(Msg.m("sudden-death"));
                    for (BT t : teams.values()) {
                        if (countTeam(t.color) == 0) continue;
                        Entity dr = world.spawnEntity(specLoc().clone().add(0, 15, 0), EntityType.ENDER_DRAGON);
                        spawned.add(dr);
                    }
                    break;
                case 6:
                    end(null);
                    break;
                default: break;
            }
        }
    }

    void tier(String type, int t) {
        for (Gen g : gens) if (g.type.equals(type) && g.tier < t) g.tier = t;
        broadcast(Msg.m("gen-upgraded", "%type%", Msg.m(type.equals("DIAMOND") ? "diamond" : "emerald"), "%tier%", "" + t));
    }

    boolean near(Player p, Location l, double r) {
        return p.getWorld().equals(l.getWorld()) && p.getLocation().distanceSquared(l) <= r * r;
    }

    void effects() {
        long now = System.currentTimeMillis();
        for (BT t : teams.values()) {
            if (countTeam(t.color) == 0) continue;
            Location sp = spawnOf(t.color);
            for (Map.Entry<UUID, PD> en : new ArrayList<>(players.entrySet())) {
                PD d = en.getValue();
                if (!d.alive || d.respawning || d.offlineAt > 0) continue;
                Player p = Bukkit.getPlayer(en.getKey());
                if (p == null) continue;
                if (d.team == t.color) {
                    if (t.miner > 0) U.eff(p, "haste", 60, t.miner - 1);
                    if (t.heal && near(p, sp, 15)) U.eff(p, "regeneration", 60, 0);
                } else if (!t.traps.isEmpty() && now > t.trapCd && near(p, sp, 7)) {
                    Long im = immune.get(p.getUniqueId());
                    if (im != null && im > now) continue;
                    trigger(t, p);
                }
            }
        }
    }

    void trigger(BT t, Player enemy) {
        Trap tr = t.traps.remove(0);
        t.trapCd = System.currentTimeMillis() + 30000L;
        switch (tr) {
            case TRAP:
                U.eff(enemy, "blindness", 160, 0);
                U.eff(enemy, "slowness", 160, 0);
                break;
            case COUNTER:
                for (Player m : online(t.color)) {
                    if (near(m, spawnOf(t.color), 20)) { U.eff(m, "speed", 300, 1); U.eff(m, "jump_boost", 300, 1); }
                }
                break;
            case FATIGUE:
                U.eff(enemy, "mining_fatigue", 200, 0);
                break;
            case ALARM:
                PotionEffectType_removeInvis(enemy);
                break;
            default: break;
        }
        for (Player m : online(t.color)) {
            U.title(m, Msg.m("trap-title"), Msg.m("trap-name-" + tr.name().toLowerCase()));
            U.sound(m, "entity.wither.spawn", 0.5f, 1.5f);
        }
    }

    void PotionEffectType_removeInvis(Player p) {
        org.bukkit.potion.PotionEffectType t = U.pot("invisibility");
        if (t != null) p.removePotionEffect(t);
    }

    // =====================================================================  gear
    ItemStack leather(String part, TeamColor c) {
        ItemStack i = new ItemStack(U.mat("LEATHER_" + part));
        LeatherArmorMeta m = (LeatherArmorMeta) i.getItemMeta();
        if (m != null) { m.setColor(c.rgb()); m.setUnbreakable(true); i.setItemMeta(m); }
        return i;
    }

    public void giveArmor(Player p, PD d) {
        String[] tier = {"LEATHER", "CHAINMAIL", "IRON", "DIAMOND"};
        String m = tier[Math.min(3, d.armor)];
        PlayerInventory pi = p.getInventory();
        pi.setHelmet(leather("HELMET", d.team));
        pi.setChestplate(leather("CHESTPLATE", d.team));
        pi.setLeggings(m.equals("LEATHER") ? leather("LEGGINGS", d.team) : U.unbreakable(new ItemStack(U.mat(m + "_LEGGINGS"))));
        pi.setBoots(m.equals("LEATHER") ? leather("BOOTS", d.team) : U.unbreakable(new ItemStack(U.mat(m + "_BOOTS"))));
    }

    public void giveGear(Player p, PD d) {
        PlayerInventory pi = p.getInventory();
        pi.clear();
        pi.addItem(U.unbreakable(new ItemStack(Material.WOODEN_SWORD)));
        giveArmor(p, d);
        if (d.shears) pi.addItem(U.unbreakable(new ItemStack(Material.SHEARS)));
        if (d.pick > 0) pi.addItem(Menus.tool(0, d.pick - 1));
        if (d.axe > 0) pi.addItem(Menus.tool(1, d.axe - 1));
        upgrades(p);
    }

    /** Applies team upgrades (sharpness / protection) to everything the player carries. */
    public void upgrades(Player p) {
        PD d = players.get(p.getUniqueId());
        if (d == null || d.team == null) return;
        BT t = teams.get(d.team);
        PlayerInventory inv = p.getInventory();
        if (t.sharp > 0) {
            for (int s = 0; s < inv.getSize(); s++) {
                ItemStack i = inv.getItem(s);
                if (i != null && i.getType().name().endsWith("_SWORD")) { U.addEnch(i, "sharpness", 1); inv.setItem(s, i); }
            }
        }
        if (t.prot > 0) {
            ItemStack[] ar = inv.getArmorContents();
            for (ItemStack i : ar) if (i != null) U.addEnch(i, "protection", t.prot);
            inv.setArmorContents(ar);
        }
    }

    // =====================================================================  beds / death / win
    void removeBedBlocks(BT t) {
        Location b = L(t.bed);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                Block bl = world.getBlockAt(b.getBlockX() + dx, b.getBlockY(), b.getBlockZ() + dz);
                if (bl.getType().name().endsWith("_BED")) bl.setType(Material.AIR, false);
            }
    }

    /** Which team's bed is at this block? */
    public BT bedAt(Block b) {
        if (!b.getType().name().endsWith("_BED")) return null;
        for (BT t : teams.values()) {
            if (t.bed == null) continue;
            Location l = L(t.bed);
            int dx = Math.abs(l.getBlockX() - b.getX()), dz = Math.abs(l.getBlockZ() - b.getZ());
            if (l.getBlockY() == b.getY() && dx + dz <= 1) return t;
        }
        return null;
    }

    public void bedBroken(BT t, Player breaker) {
        if (!t.bedAlive) return;
        t.bedAlive = false;
        removeBedBlocks(t);
        String by = Msg.m("the-game");
        if (breaker != null) {
            PD bd = players.get(breaker.getUniqueId());
            if (bd != null) { bd.beds++; by = bd.team.chat + breaker.getName(); }
            pl.stats.add(breaker.getUniqueId(), "beds", 1);
            pl.stats.xp(breaker, pl.getConfig().getInt("xp.bed", 25));
            pl.cos.reward(breaker, "bed", 15);
            if (pl.ach != null) pl.ach.check(breaker, bd);
        }
        broadcast(Msg.m("bed-destroyed", "%team%", t.color.chat + t.color.display, "%by%", by));
        for (Player m : online(t.color)) {
            U.title(m, Msg.m("bed-title"), Msg.m("bed-subtitle"));
            U.sound(m, "entity.wither.death", 1f, 1f);
        }
        // teammates who are disconnected can never respawn now: their final death
        boolean out = false;
        for (UUID u : new ArrayList<>(players.keySet())) {
            PD od = players.get(u);
            if (od != null && od.team == t.color && od.offlineAt > 0) { eliminateOffline(u); out = true; }
        }
        if (out) checkWin();
    }

    Player findKiller(Player victim, PD d) {
        if (d.lastHit != null && System.currentTimeMillis() - d.lastHitAt < 15000) return Bukkit.getPlayer(d.lastHit);
        return null;
    }

    /** Kill / final-kill stats, XP and the victim's resources for the killer. */
    void creditKill(Player v, Player killer, PD kd, boolean fin) {
        if (kd != null && kd.alive) {
            kd.kills++;
            pl.stats.xp(killer, pl.getConfig().getInt(fin ? "xp.final-kill" : "xp.kill", fin ? 25 : 10));
            pl.stats.add(killer.getUniqueId(), "kills", 1);
            if (fin) { kd.finals++; pl.stats.add(killer.getUniqueId(), "finals", 1); }
            pl.cos.reward(killer, fin ? "final-kill" : "kill", fin ? 10 : 5);
            pl.cos.onKill(killer, v, fin);
            Map<Material, Integer> got = new LinkedHashMap<>();
            for (ItemStack i : v.getInventory().getContents()) {
                if (i == null) continue;
                Material m = i.getType();
                if (m == Material.IRON_INGOT || m == Material.GOLD_INGOT || m == Material.DIAMOND || m == Material.EMERALD) {
                    killer.getInventory().addItem(i.clone());
                    got.merge(m, i.getAmount(), Integer::sum);
                }
            }
            for (Map.Entry<Material, Integer> g : got.entrySet())
                killer.sendMessage(U.c("&a+" + g.getValue() + " &7" + g.getKey().name().replace("_INGOT", "").toLowerCase()));
            U.sound(killer, "entity.experience_orb.pickup", 1f, 1f);
            if (pl.ach != null) pl.ach.check(killer, kd);
        }
    }

    /** Respawn countdown (death, or coming back after a disconnect). Bed gone at the end of it = final death. */
    void startRespawn(final UUID id) {
        new BukkitRunnable() {
            int left = pl.getConfig().getInt("respawn-seconds", 5);
            @Override public void run() {
                Player p = Bukkit.getPlayer(id);
                PD pd = players.get(id);
                if (p == null || pd == null || state != State.PLAYING || !pd.respawning) { cancel(); return; }
                if (left <= 0) {
                    cancel();
                    if (!teams.get(pd.team).bedAlive) {
                        pd.respawning = false;
                        pd.alive = false;
                        spectate(p, true);
                        U.title(p, Msg.m("died-title"), Msg.m("bed-gone-sub"));
                        checkWin();
                    } else respawn(p);
                    return;
                }
                U.title(p, Msg.m("died-title"), Msg.m("respawning-sub", "%sec%", "" + left));
                left--;
            }
        }.runTaskTimer(pl, 0L, 20L);
    }

    public void onDeath(Player v, Player killer, EntityDamageEvent.DamageCause cause) {
        PD d = players.get(v.getUniqueId());
        if (d == null || state != State.PLAYING || !d.alive || d.respawning) return;
        BT t = teams.get(d.team);
        boolean fin = !t.bedAlive;
        PD kd = killer != null ? players.get(killer.getUniqueId()) : null;
        if (kd != null && killer.equals(v)) kd = null;

        String verb;
        switch (cause) {
            case VOID: verb = Msg.m("verb-void"); break;
            case ENTITY_EXPLOSION: case BLOCK_EXPLOSION: verb = Msg.m("verb-explosion"); break;
            case PROJECTILE: verb = Msg.m("verb-projectile"); break;
            default: verb = Msg.m("verb-default"); break;
        }
        String alone;
        switch (cause) {
            case VOID: alone = Msg.m("die-void"); break;
            case FALL: alone = Msg.m("die-fall"); break;
            case FIRE: case FIRE_TICK: case LAVA: alone = Msg.m("die-fire"); break;
            case ENTITY_EXPLOSION: case BLOCK_EXPLOSION: alone = Msg.m("die-explosion"); break;
            default: alone = Msg.m("die-default"); break;
        }
        String msg = d.team.chat + v.getName() + " &7" + (kd != null ? verb + " " + kd.team.chat + killer.getName() : alone);
        if (fin) msg += " " + Msg.m("final-kill");
        broadcast(msg);

        d.deaths++;
        pl.stats.add(v.getUniqueId(), "deaths", 1);
        if (kd != null && kd.alive && cause == EntityDamageEvent.DamageCause.VOID) pl.stats.add(killer.getUniqueId(), "voidkills", 1);
        creditKill(v, killer, kd, fin);
        pl.cos.onDeath(v);

        v.getInventory().clear();
        v.getInventory().setArmorContents(new ItemStack[4]);
        for (PotionEffect e : v.getActivePotionEffects()) v.removePotionEffect(e.getType());
        v.setHealth(20.0);
        v.setFoodLevel(20);
        v.setFireTicks(0);
        v.setFallDistance(0);
        d.lastHit = null;
        if (d.pick > 1) d.pick--;
        if (d.axe > 1) d.axe--;

        if (fin) {
            d.alive = false;
            spectate(v, true);
            U.title(v, Msg.m("died-title"), Msg.m("eliminated-sub"));
            checkWin();
        } else {
            d.respawning = true;
            spectate(v, false);
            startRespawn(v.getUniqueId());
        }
    }

    public void spectate(Player p, boolean permanent) {
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(true);
        p.setFlying(true);
        p.setCollidable(false);
        p.teleport(specLoc());
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(p)) continue;
            PD od = players.get(o.getUniqueId());
            if (od != null && od.alive && !od.respawning) o.hidePlayer(pl, p);
        }
        if (permanent) {
            HubMenus.setHot(pl, p, "hotbar.spectator.teleporter");
            HubMenus.setHot(pl, p, "hotbar.spectator.play-again");
            HubMenus.setHot(pl, p, "hotbar.spectator.lobby");
        }
    }

    public void respawn(Player p) {
        PD d = players.get(p.getUniqueId());
        if (d == null) return;
        d.respawning = false;
        pl.ui.syncVisibilitySoon();
        p.setFlying(false);
        p.setAllowFlight(false);
        p.setCollidable(true);
        p.setGameMode(GameMode.SURVIVAL);
        p.teleport(spawnOf(d.team));
        giveGear(p, d);
        U.title(p, Msg.m("respawned-title"), "");
        U.sound(p, "entity.player.levelup", 0.6f, 1.2f);
    }

    public void checkWin() {
        if (state != State.PLAYING) return;
        List<TeamColor> alive = new ArrayList<>();
        for (BT t : teams.values()) if (aliveCount(t.color) > 0) alive.add(t.color);
        if (alive.size() <= 1) end(alive.isEmpty() ? null : alive.get(0));
    }

    void end(TeamColor w) {
        if (state != State.PLAYING) return;
        state = State.ENDING;
        winner = w;
        countdown = pl.getConfig().getInt("end-countdown", 10);
        for (Map.Entry<UUID, PD> e : players.entrySet()) {
            PD d = e.getValue();
            boolean win = w != null && d.team == w;
            pl.stats.game(pl, e.getKey(), d.kills, d.finals, d.beds);
            if (win) pl.stats.win(e.getKey()); else pl.stats.lose(e.getKey());
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            pl.stats.xp(p, pl.getConfig().getInt("xp.play", 20) + (win ? pl.getConfig().getInt("xp.win", 100) : 0));
            pl.cos.reward(p, "play", 10);
            if (win) { pl.cos.reward(p, "win", 50); pl.cos.onWin(this, p); }
            if (win && d.deaths == 0) pl.stats.add(e.getKey(), "flawless", 1);
            if (pl.ach != null) pl.ach.check(p, d);
            if (d.respawning) { d.respawning = false; d.alive = false; spectate(p, true); }
            p.setAllowFlight(true);
            if (win) { U.title(p, Msg.m("victory-title"), Msg.m("victory-sub")); U.sound(p, "ui.toast.challenge_complete", 1f, 1f); }
            else U.title(p, Msg.m("gameover-title"), w == null ? Msg.m("draw") : Msg.m("team-won", "%team%", w.chat + w.display));
        }
        broadcast(w == null ? Msg.m("draw-broadcast") : Msg.m("winner-broadcast", "%team%", w.chat + w.display));
        for (Entity en : spawned) en.remove();
        spawned.clear();
        topKillers();
        pl.stats.save();
    }

    /** Chat at the end of the match: the 3 players with the most kills (final kills and beds as tie-breakers). */
    void topKillers() {
        List<Map.Entry<UUID, PD>> l = new ArrayList<>();
        for (Map.Entry<UUID, PD> e : players.entrySet()) if (e.getValue().kills > 0 && e.getValue().team != null) l.add(e);
        if (l.isEmpty()) return;
        Collections.sort(l, new Comparator<Map.Entry<UUID, PD>>() {
            @Override public int compare(Map.Entry<UUID, PD> x, Map.Entry<UUID, PD> y) {
                PD a = x.getValue(), b = y.getValue();
                if (b.kills != a.kills) return b.kills - a.kills;
                if (b.finals != a.finals) return b.finals - a.finals;
                return b.beds - a.beds;
            }
        });
        String[] col = {"&6", "&7", "&c"};
        broadcast(Msg.m("top-header"));
        for (int i = 0; i < l.size() && i < 3; i++) {
            PD d = l.get(i).getValue();
            String nm = pl.stats.nameOf(l.get(i).getKey());
            Player op = Bukkit.getPlayer(l.get(i).getKey());
            if (op != null) nm = op.getName();
            broadcast(Msg.m("top-line", "%color%", col[i], "%rank%", "" + (i + 1), "%player%", d.team.chat + nm,
                    "%kills%", "" + d.kills, "%finals%", "" + d.finals, "%beds%", "" + d.beds));
        }
        broadcast(Msg.m("top-footer"));
        if (pl.getConfig().getBoolean("end-podium.enabled", true)) {
            try { Podium.show(pl, this, l.size() > 3 ? new ArrayList<>(l.subList(0, 3)) : l); }
            catch (Throwable t) { pl.getLogger().warning("End podium failed: " + t); }
        }
    }

    public void forceStop() {
        if (world == null && players.isEmpty()) return;
        finish();
    }

    void finish() {
        if (loop != null) { loop.cancel(); loop = null; }
        if (spinTask != null) { spinTask.cancel(); spinTask = null; }
        for (Entity en : spawned) en.remove();
        spawned.clear();
        for (Map.Entry<UUID, PD> e : new ArrayList<>(players.entrySet())) {
            pl.mgr.byPlayer.remove(e.getKey()); // first: the "no creative in a game" lock must not apply to the restore below
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null && e.getValue().offlineAt == 0) restore(p, e.getValue());
        }
        players.clear();
        boards.clear();
        placed.clear();
        gens.clear();
        mobTeam.clear();
        shopMap.clear();
        tntOwner.clear();
        immune.clear();
        pl.mgr.rejoin.values().removeIf(x -> x == this);
        for (BT t : teams.values()) t.reset();
        unloadWorld();
        state = State.WAITING;
        countdown = 0;
        elapsed = 0;
        winner = null;
    }

    // =====================================================================  summons (Dream Defender / Bed Bug)
    public void summon(final Player owner, final PD d, EntityType type, Location loc, final int lifeSec) {
        final Mob m = (Mob) world.spawnEntity(loc, type);
        m.setCustomName(U.c(d.team.chat + d.team.display + "'s " + (type == EntityType.IRON_GOLEM ? "Defender" : "Bed Bug")));
        m.setCustomNameVisible(true);
        mobTeam.put(m.getUniqueId(), d.team);
        spawned.add(m);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t++;
                if (!m.isValid() || t > lifeSec * 2 || state != State.PLAYING) { m.remove(); cancel(); return; }
                Player best = null;
                double bd = 15 * 15;
                for (Map.Entry<UUID, PD> e : players.entrySet()) {
                    PD pd = e.getValue();
                    if (pd.team == d.team || !pd.alive || pd.respawning || pd.offlineAt > 0) continue;
                    Player p = Bukkit.getPlayer(e.getKey());
                    if (p == null || !p.getWorld().equals(m.getWorld())) continue;
                    double ds = p.getLocation().distanceSquared(m.getLocation());
                    if (ds < bd) { bd = ds; best = p; }
                }
                if (best != null) m.setTarget(best);
            }
        }.runTaskTimer(pl, 10L, 10L);
    }

    // =====================================================================  auto team / party / rematch / tower
    String tn(PD d, Player p) {
        return (d.team == null ? ChatColor.GRAY.toString() : d.team.chat.toString()) + p.getName();
    }

    /** Auto-team: keeps lobby teams even; only moves players who did not choose a team themselves. */
    void balance() {
        if (state != State.WAITING && state != State.STARTING) return;
        for (int guard = 0; guard < 20; guard++) {
            TeamColor big = null, small = null;
            int bn = -1, sn = 999;
            for (TeamColor c : teams.keySet()) {
                int n = countTeam(c);
                if (n > bn) { bn = n; big = c; }
                if (n < sn) { sn = n; small = c; }
            }
            if (bn - sn < 2 || sn >= teamSize) break;
            UUID mv = null;
            for (Map.Entry<UUID, PD> e : players.entrySet())
                if (e.getValue().team == big && !e.getValue().chosen) { mv = e.getKey(); break; }
            if (mv == null) break;
            players.get(mv).team = small;
            Player mp = Bukkit.getPlayer(mv);
            if (mp != null) mp.sendMessage(Msg.m("team-balanced", "%team%", small.chat + small.display));
        }
    }

    /** Put a party in one team. */
    public void groupTeam(List<Player> g) {
        TeamColor best = null;
        int bf = -1;
        for (TeamColor c : teams.keySet()) {
            int inG = 0;
            for (Player m : g) { PD d = players.get(m.getUniqueId()); if (d != null && d.team == c) inG++; }
            int free = teamSize - (countTeam(c) - inG);
            if (free >= g.size() && free > bf) { bf = free; best = c; }
        }
        if (best == null) return;
        for (Player m : g) {
            PD d = players.get(m.getUniqueId());
            if (d != null) { d.team = best; d.chosen = true; }
        }
        balance();
    }

    /** Spectator "Play Again" item. */
    public void rematch(Player p) {
        PD d = players.get(p.getUniqueId());
        if (d == null || (d.alive && !d.respawning)) return;
        Party pt = pl.parties.of(p);
        int need = 1;
        if (pt != null) {
            if (!pt.leader.equals(p.getUniqueId())) { p.sendMessage(Msg.m("party-leader-only")); return; }
            for (UUID m : pt.members) {
                PD md = players.get(m);
                if (md != null && md.alive && !md.respawning) { p.sendMessage(Msg.m("rematch-party-alive")); return; }
            }
            need = pt.size();
        }
        Arena na = pl.mgr.quick(mode(), need);
        if (na == null || na == this) { p.sendMessage(Msg.m("rematch-none")); return; }
        if (pt != null) {
            for (UUID m : new ArrayList<>(pt.members)) {
                Player mp = Bukkit.getPlayer(m);
                if (mp != null && pl.mgr.of(mp) == this) leave(mp);
            }
        } else leave(p);
        pl.parties.tryJoin(p, na);
    }

    static BlockFace faceOf(int x, int z) {
        if (z == 1) return BlockFace.SOUTH;
        if (z == -1) return BlockFace.NORTH;
        if (x == 1) return BlockFace.EAST;
        return BlockFace.WEST;
    }

    /** Pop-up Tower: 3x3 wool tower, 5 high, ladder inside, door on the placing player's side. */
    public void popTower(final Player p, final Block b, final PD d) {
        float yaw = (p.getLocation().getYaw() % 360 + 360) % 360;
        final int lx, lz;
        if (yaw >= 315 || yaw < 45) { lx = 0; lz = 1; }
        else if (yaw < 135) { lx = -1; lz = 0; }
        else if (yaw < 225) { lx = 0; lz = -1; }
        else { lx = 1; lz = 0; }
        final Material wool = d.team.block("WOOL");
        Bukkit.getScheduler().runTask(pl, new Runnable() {
            @Override public void run() {
                if (world == null || state != State.PLAYING) return;
                World w = b.getWorld();
                int bx = b.getX(), by = b.getY(), bz = b.getZ();
                b.setType(Material.AIR, false);
                for (int dy = 0; dy < 5; dy++)
                    for (int dx = -1; dx <= 1; dx++)
                        for (int dz = -1; dz <= 1; dz++)
                            if (w.getBlockAt(bx + dx, by + dy, bz + dz).getType() != Material.AIR) {
                                Menus.give(p, U.item(Material.CHEST, 1, "&ePop-up Tower", "&7Place it to build a tower"));
                                p.sendMessage(Msg.m("tower-nospace"));
                                return;
                            }
                BlockFace face = faceOf(-lx, -lz);
                for (int dy = 0; dy < 5; dy++)
                    for (int dx = -1; dx <= 1; dx++)
                        for (int dz = -1; dz <= 1; dz++) {
                            Block t = w.getBlockAt(bx + dx, by + dy, bz + dz);
                            boolean center = dx == 0 && dz == 0;
                            boolean door = dx == -lx && dz == -lz && dy < 2;
                            if (center) {
                                t.setType(Material.LADDER, false);
                                BlockData bd = t.getBlockData();
                                if (bd instanceof Directional) { ((Directional) bd).setFacing(face); t.setBlockData(bd, false); }
                                placed.add(U.bk(t.getLocation()));
                            } else if (!door) {
                                t.setType(wool, false);
                                placed.add(U.bk(t.getLocation()));
                            }
                        }
                U.sound(p, "block.wool.place", 1f, 1f);
            }
        });
    }

    // =====================================================================  scoreboard
    String entry(int i) { return ChatColor.values()[i].toString() + ChatColor.RESET; }

    private String sbState() {
        switch (state) {
            case WAITING: return "waiting";
            case STARTING: return "starting";
            case ENDING: return "ending";
            default: return "playing";
        }
    }

    void refreshBoards() {
        org.bukkit.configuration.file.YamlConfiguration bc = pl.boardCfg;
        String key = sbState();
        // waiting / starting / ending boards can be switched off in scoreboard.yml
        if (!key.equals("playing") && !bc.getBoolean("game." + key + ".enabled", true)) {
            for (UUID uuid : new ArrayList<>(boards.keySet())) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
            boards.clear();
            return;
        }
        String title = U.c(bc.getString("game.title", "&e&lBED WARS"));
        for (Map.Entry<UUID, PD> en : players.entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null || en.getValue().offlineAt > 0) continue;
            Scoreboard sb = boards.get(p.getUniqueId());
            if (sb == null) {
                sb = Bukkit.getScoreboardManager().getNewScoreboard();
                sb.registerNewObjective("bwx", "dummy", title).setDisplaySlot(DisplaySlot.SIDEBAR);
                for (int i = 0; i < 15; i++) sb.registerNewTeam("l" + i).addEntry(entry(i));
                for (TeamColor c : TeamColor.values()) {
                    org.bukkit.scoreboard.Team nt = sb.registerNewTeam("t_" + c.name());
                    nt.setColor(c.chat);
                    nt.setPrefix(c.chat + "");
                }
                boards.put(p.getUniqueId(), sb);
                p.setScoreboard(sb);
            }
            Objective side = sb.getObjective(DisplaySlot.SIDEBAR);
            if (side == null) continue;
            side.setDisplayName(title);
            List<String> lines = fit15(lines(en.getKey(), en.getValue()));
            for (int i = 0; i < 15; i++) {
                if (i < lines.size()) {
                    String s = U.c(lines.get(i));
                    sb.getTeam("l" + i).setPrefix(s.length() > 64 ? s.substring(0, 64) : s);
                    side.getScore(entry(i)).setScore(15 - i);
                } else sb.resetScores(entry(i));
            }
            for (Map.Entry<UUID, PD> o2 : players.entrySet()) {
                Player q = Bukkit.getPlayer(o2.getKey());
                if (q == null || o2.getValue().team == null) continue;
                org.bukkit.scoreboard.Team nt = sb.getTeam("t_" + o2.getValue().team.name());
                if (nt != null && !nt.hasEntry(q.getName())) nt.addEntry(q.getName());
            }
        }
    }

    /** Lines of the sidebar, from scoreboard.yml (BedWars1058 style). */
    /** Max 15 lines on a sidebar: when there are too many, empty spacer lines are dropped (from the bottom) before anything is cut. */
    static List<String> fit15(List<String> in) {
        List<String> l = new ArrayList<>(in);
        while (l.size() > 15) {
            int blank = -1;
            for (int i = l.size() - 2; i >= 0; i--) if (ChatColor.stripColor(U.c(l.get(i))).trim().isEmpty()) { blank = i; break; }
            if (blank < 0) break;
            l.remove(blank);
        }
        return l.size() > 15 ? new ArrayList<>(l.subList(0, 15)) : l;
    }

    List<String> lines(UUID id, PD d) {
        org.bukkit.configuration.file.YamlConfiguration bc = pl.boardCfg;
        String key = sbState();
        String footer = bc.getString("game.footer", "&ewww.yourserver.net");
        String date;
        try { date = new java.text.SimpleDateFormat(bc.getString("game.date-format", "dd/MM/yy")).format(new Date()); }
        catch (Exception e) { date = ""; }
        String result = winner == null ? Msg.m("sb-draw") : (d.team == winner ? Msg.m("sb-victory") : Msg.m("sb-gameover"));
        int[] ev = evTimes();
        String nx = "-";
        int rem = 0;
        for (int i = 0; i < ev.length; i++) if (ev[i] > elapsed) { nx = Msg.m("event-" + EK[i]); rem = ev[i] - elapsed; break; }
        String[][] ph = {
                {"%date%", date}, {"%arena%", name}, {"%map%", name}, {"%mode%", mode()},
                {"%version%", pl.getDescription().getVersion()}, {"%players%", "" + players.size()}, {"%max%", "" + maxPlayers()},
                {"%needed%", "" + Math.max(0, min - players.size())}, {"%countdown%", "" + countdown},
                {"%event%", nx}, {"%time%", U.mmss(rem)},
                {"%kills%", "" + d.kills}, {"%finals%", "" + d.finals}, {"%beds%", "" + d.beds},
                {"%coins%", "" + pl.stats.coins(id)}, {"%level%", "" + pl.stats.level(id)},
                {"%team%", d.team == null ? "-" : d.team.chat + d.team.display}, {"%footer%", footer}, {"%result%", result}
        };
        List<String> out = new ArrayList<>();
        for (String raw : Cfg.lines(bc, "game." + key + ".lines")) {
            if (raw.trim().equals("%teams%")) {
                if (key.equals("playing")) out.addAll(teamLines(bc, d));
                continue;
            }
            String l = raw;
            // old scoreboard.yml files still have the fixed "Game Over" line: winners must see Victory instead
            if (key.equals("ending") && ChatColor.stripColor(U.c(raw)).trim().equalsIgnoreCase("Game Over")) l = "%result%";
            for (String[] kv : ph) l = l.replace(kv[0], kv[1]);
            out.add(l);
        }
        return out;
    }

    private List<String> teamLines(org.bukkit.configuration.file.YamlConfiguration bc, PD d) {
        List<String> out = new ArrayList<>();
        String fmt = bc.getString("game.playing.team-line", "%color%%letter% &f%team%: %status%%you%");
        for (BT t : teams.values()) {
            int al = aliveCount(t.color);
            String st = t.bedAlive ? bc.getString("game.playing.status-bed", "&a✔")
                    : (al > 0 ? bc.getString("game.playing.status-alive", "&a%n%").replace("%n%", "" + al) : bc.getString("game.playing.status-dead", "&c✘"));
            out.add(fmt.replace("%color%", t.color.chat.toString()).replace("%letter%", t.color.display.substring(0, 1))
                    .replace("%team%", t.label()).replace("%status%", st)
                    .replace("%you%", d.team == t.color ? bc.getString("game.playing.you", " &7YOU") : ""));
        }
        return out;
    }
}
