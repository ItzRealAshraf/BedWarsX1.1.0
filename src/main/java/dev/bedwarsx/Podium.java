/*
 * BedWarsX - Copyright (c) 2026 ASHRAF ELHENAWY (ItzRealAshraf). All rights reserved.
 * Copying, re-selling, re-branding or removing the credits is not allowed.
 */
package dev.bedwarsx;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * End-of-match podium (config.yml -> end-podium): the 3 best players of the match are shown as NPC-style figures with
 * THEIR OWN SKIN (head + gold / silver / bronze armor) and a name + kills hologram. Everybody is taken to the spectator
 * spot to watch it. No commands, no NMS, no other plugin needed. The figures are removed when the arena resets.
 */
public final class Podium {
    private Podium() {}

    private static final int[] COLORS = {0xFFD700, 0xC0C0C0, 0xCD7F32};   // gold, silver, bronze armor
    private static final String[] RANK_COLOR = {"&6", "&7", "&c"};

    private static ArmorStand stand(Arena a, Location at, boolean small) {
        ArmorStand st = at.getWorld().spawn(at, ArmorStand.class);
        st.setGravity(false);
        st.setMarker(true);
        st.setBasePlate(false);
        st.setSmall(small);
        st.setInvulnerable(true);
        st.setPersistent(false);
        a.spawned.add(st);
        return st;
    }

    private static void label(Arena a, Location at, String text) {
        ArmorStand st = stand(a, at, true);
        st.setVisible(false);
        st.setArms(false);
        st.setCustomName(U.c(text));
        st.setCustomNameVisible(true);
    }

    private static ItemStack armor(Material m, int rgb) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        if (im instanceof LeatherArmorMeta) { ((LeatherArmorMeta) im).setColor(Color.fromRGB(rgb)); it.setItemMeta(im); }
        return it;
    }

    private static ItemStack head(UUID u) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta im = it.getItemMeta();
        if (im instanceof SkullMeta) {
            ((SkullMeta) im).setOwningPlayer(Bukkit.getOfflinePlayer(u));
            it.setItemMeta(im);
        }
        return it;
    }

    /** top = the best players of the match, best first (at most 3 are shown). */
    static void show(BedWarsX pl, Arena a, List<Map.Entry<UUID, Arena.PD>> top) {
        if (top.isEmpty() || a.world == null) return;
        Location stage = a.specLoc();
        double rad = Math.toRadians(stage.getYaw());
        double fx = -Math.sin(rad), fz = Math.cos(rad);        // where the spectators look
        double rx = -Math.cos(rad), rz = -Math.sin(rad);       // their right hand
        Location mid = stage.clone().add(fx * 5, -1.5, fz * 5);
        float face = stage.getYaw() + 180f;                    // the figures look back at the spectators
        double[] side = {0.0, -1.4, 1.4};                      // rank 1 in the middle, 2 on the left, 3 on the right
        label(a, mid.clone().add(0, 3.3, 0), Msg.m("podium-title"));
        for (int r = 0; r < 3 && r < top.size(); r++) {
            UUID u = top.get(r).getKey();
            Arena.PD d = top.get(r).getValue();
            Location at = mid.clone().add(rx * side[r], 0, rz * side[r]);
            at.setYaw(face);
            at.setPitch(0f);
            ArmorStand body = stand(a, at, false);
            body.setVisible(true);
            body.setArms(true);
            EntityEquipment eq = body.getEquipment();
            if (eq != null) {
                eq.setHelmet(head(u));
                eq.setChestplate(armor(Material.LEATHER_CHESTPLATE, COLORS[r]));
                eq.setLeggings(armor(Material.LEATHER_LEGGINGS, COLORS[r]));
                eq.setBoots(armor(Material.LEATHER_BOOTS, COLORS[r]));
                eq.setItemInMainHand(new ItemStack(r == 0 ? Material.GOLDEN_SWORD : r == 1 ? Material.IRON_SWORD : Material.STONE_SWORD));
            }
            Player op = Bukkit.getPlayer(u);
            String name = op != null ? op.getName() : pl.stats.nameOf(u);
            label(a, at.clone().add(0, 2.25, 0), Msg.m("podium-name", "%color%", RANK_COLOR[r], "%rank%", "" + (r + 1), "%player%", name));
            label(a, at.clone().add(0, 2.0, 0), Msg.m("podium-value", "%kills%", "" + d.kills, "%finals%", "" + d.finals, "%beds%", "" + d.beds));
        }
        if (pl.getConfig().getBoolean("end-podium.teleport-players", true)) {
            for (UUID id : a.players.keySet()) {
                Player p = Bukkit.getPlayer(id);
                if (p == null) continue;
                p.teleport(stage);
                p.setAllowFlight(true);
                p.setFlying(true);
            }
        }
    }
}
