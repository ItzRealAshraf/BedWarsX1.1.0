/*
 * BedWarsX - Copyright (c) 2026 ASHRAF ELHENAWY (ItzRealAshraf). All rights reserved.
 * Copying, re-selling, re-branding or removing the credits is not allowed.
 */
package dev.bedwarsx;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Setup holograms ("this is the RED team spawn"...) shown inside the map while it is being edited with
 * /bw edit. They are armor stands that are never saved with the world, so they cannot end up in the map
 * copy that games run on; they are removed when the map is saved/enabled.
 */
public final class EditMarkers {
    private static final String TAG = "bwx_editmarker";

    private final BedWarsX pl;
    private final Map<String, List<UUID>> stands = new HashMap<>();

    EditMarkers(BedWarsX pl) { this.pl = pl; }

    /** Rebuilds every marker of this arena from its saved setup (call after each change). */
    public void refresh(Arena ar) {
        remove(ar);
        World w = ar.template == null ? null : Bukkit.getWorld(ar.template);
        if (w == null) return;
        List<UUID> ids = new ArrayList<>();
        spot(ids, w, ar.lobby, 1.6, "&a&lWAITING SPAWN", "&7Players wait here");
        spot(ids, w, ar.waitPos1, 1.6, "&e&lWAITING POS 1", "&7Waiting lobby corner");
        spot(ids, w, ar.waitPos2, 1.6, "&e&lWAITING POS 2", "&7Waiting lobby corner");
        spot(ids, w, ar.spec, 1.6, "&7&lSPECTATOR SPAWN", "&7Spectators start here");
        for (Arena.BT t : ar.teams.values()) {
            String c = t.color.chat + "&l" + t.label().toUpperCase();
            spot(ids, w, t.spawn, 2.4, c, "&fTeam Spawn");
            spot(ids, w, t.bed, 1.4, c, "&fTeam Bed");
            spot(ids, w, t.shop, 2.4, c, "&eItem Shop");
            spot(ids, w, t.upgrade, 2.4, c, "&dUpgrade Shop");
            spot(ids, w, t.gen, 1.6, c, "&6Team Generator &7(iron + gold)");
        }
        for (String g : ar.genCfg) {
            String[] p = g.split("@", 2);
            if (p.length < 2) continue;
            boolean dia = p[0].equalsIgnoreCase("DIAMOND");
            spot(ids, w, p[1], 1.8, dia ? "&b&lDIAMOND" : "&a&lEMERALD", "&fMap Generator");
        }
        for (String sh : ar.shopCfg) {
            String[] p = sh.split("@", 2);
            if (p.length < 2) continue;
            spot(ids, w, p[1], 2.4, "&e&lSHOP", p[0].equalsIgnoreCase("UPGRADE") ? "&dUpgrade Shop" : "&eItem Shop");
        }
        stands.put(ar.name.toLowerCase(), ids);
    }

    private void spot(List<UUID> ids, World w, String saved, double height, String... lines) {
        if (saved == null || saved.isEmpty()) return;
        Location base;
        try { base = U.sl(saved, w); } catch (Exception e) { return; }
        base.add(0, height, 0);
        for (int i = 0; i < lines.length; i++) {
            Location at = base.clone().add(0, (lines.length - 1 - i) * 0.27, 0);
            ArmorStand st = w.spawn(at, ArmorStand.class);
            st.setVisible(false);
            st.setGravity(false);
            st.setSmall(true);
            st.setMarker(true);
            st.setBasePlate(false);
            st.setArms(false);
            st.setPersistent(false);
            st.addScoreboardTag(TAG);
            st.setCustomName(U.c(lines[i]));
            st.setCustomNameVisible(true);
            ids.add(st.getUniqueId());
        }
    }

    public void remove(Arena ar) {
        List<UUID> old = stands.remove(ar.name.toLowerCase());
        if (old != null) for (UUID id : old) { Entity e = Bukkit.getEntity(id); if (e != null) e.remove(); }
        // also anything left over inside the template world (e.g. after a crash / reload)
        World w = ar.template == null ? null : Bukkit.getWorld(ar.template);
        if (w != null) for (ArmorStand st : w.getEntitiesByClass(ArmorStand.class)) if (st.getScoreboardTags().contains(TAG) && !isTracked(st.getUniqueId())) st.remove();
    }

    private boolean isTracked(UUID id) {
        for (List<UUID> l : stands.values()) if (l.contains(id)) return true;
        return false;
    }

    /** Removes every marker in every world (plugin start / stop). */
    public void purge() {
        stands.clear();
        for (World w : Bukkit.getWorlds())
            for (ArmorStand st : w.getEntitiesByClass(ArmorStand.class))
                if (st.getScoreboardTags().contains(TAG)) st.remove();
    }
}
