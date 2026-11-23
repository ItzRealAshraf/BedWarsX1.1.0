/*
 * BedWarsX - Copyright (c) 2026 ASHRAF ELHENAWY (ItzRealAshraf). All rights reserved.
 * Copying, re-selling, re-branding or removing the credits is not allowed.
 */
package dev.bedwarsx;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ArenaManager {
    private final BedWarsX pl;
    public final Map<String, Arena> arenas = new LinkedHashMap<>();
    public final Map<UUID, Arena> byPlayer = new HashMap<>();
    public final Map<UUID, Arena> rejoin = new HashMap<>();
    public final Map<UUID, String> editing = new HashMap<>();

    public ArenaManager(BedWarsX pl) { this.pl = pl; }

    public File dir() {
        File d = new File(pl.getDataFolder(), "arenas");
        d.mkdirs();
        return d;
    }

    public void loadAll() {
        File[] fs = dir().listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (!f.getName().endsWith(".yml")) continue;
            try {
                Arena a = Arena.load(pl, f);
                arenas.put(a.name.toLowerCase(), a);
            } catch (Exception e) {
                pl.getLogger().warning("Could not load arena " + f.getName() + ": " + e.getMessage());
            }
        }
        pl.getLogger().info("Loaded " + arenas.size() + " arena(s).");
    }

    public Arena get(String n) { return n == null ? null : arenas.get(n.toLowerCase()); }

    public Arena of(Player p) { return byPlayer.get(p.getUniqueId()); }

    public Arena byWorld(World w) {
        if (w == null) return null;
        for (Arena a : arenas.values()) if (a.world != null && a.world.equals(w)) return a;
        return null;
    }

    public boolean isGameWorld(World w) { return w != null && w.getName().startsWith("bwx_"); }

    public Arena create(String name, String template, int teamSize) {
        Arena a = new Arena(pl, name);
        a.template = template;
        a.teamSize = teamSize;
        arenas.put(name.toLowerCase(), a);
        a.save();
        return a;
    }

    public void delete(Arena a) {
        a.forceStop();
        arenas.remove(a.name.toLowerCase());
        new File(dir(), a.name + ".yml").delete();
    }

    /** Best waiting arena (most players) for quick-join. */
    public Arena quick() { return quick(null); }

    public Arena quick(String mode) { return quick(mode, 1); }

    public Arena quick(String mode, int need) {
        Arena best = null;
        for (Arena a : arenas.values()) {
            if (!a.enabled) continue;
            if (a.state != Arena.State.WAITING && a.state != Arena.State.STARTING) continue;
            if (a.players.size() + need > a.maxPlayers() || need > a.teamSize) continue;
            if (mode != null && !a.mode().equalsIgnoreCase(mode)) continue;
            if (best == null || a.players.size() > best.players.size()) best = a;
        }
        return best;
    }

    public List<Arena> list() { return new ArrayList<>(arenas.values()); }

    public void shutdown() {
        for (Arena a : list()) a.forceStop();
    }

    /** Delete leftover game worlds from a crash. */
    public void cleanupLeftovers() {
        File[] fs = Bukkit.getWorldContainer().listFiles();
        if (fs == null) return;
        for (File f : fs) if (f.isDirectory() && f.getName().startsWith("bwx_")) Arena.deleteDir(f);
    }
}
