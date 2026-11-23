/*
 * BedWarsX - Copyright (c) 2026 ASHRAF ELHENAWY (ItzRealAshraf). All rights reserved.
 * Copying, re-selling, re-branding or removing the credits is not allowed.
 */
package dev.bedwarsx;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Protects the BedWars lobby (the spawn set with /bw sethub): nobody can break, place, burn or blow up anything there.
 * An admin (bwx.admin) switches build mode on with /bw build, and off again with /bw build (or when he leaves the server).
 * config.yml -> lobby-protection: enabled / radius (0 = the whole lobby world).
 */
public final class LobbyProtection implements Listener {
    private final BedWarsX pl;
    private final Set<UUID> builders = new HashSet<>();
    private final Map<UUID, Long> warned = new HashMap<>();

    LobbyProtection(BedWarsX pl) { this.pl = pl; }

    // ------------------------------------------------------------------ build mode
    public boolean isBuilder(Player p) { return builders.contains(p.getUniqueId()); }

    /** /bw build: toggles the mode, returns the new state. */
    public boolean toggle(Player p) {
        UUID id = p.getUniqueId();
        if (builders.remove(id)) return false;
        builders.add(id);
        return true;
    }

    public void set(Player p, boolean on) {
        if (on) builders.add(p.getUniqueId());
        else builders.remove(p.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        builders.remove(e.getPlayer().getUniqueId());
        warned.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ region
    /** Is this place inside the protected BedWars lobby? */
    public boolean protectedAt(Location l) {
        if (l == null || l.getWorld() == null) return false;
        if (!pl.getConfig().getBoolean("lobby-protection.enabled", true)) return false;
        Location hub = pl.hubLocation();
        if (hub == null || !l.getWorld().equals(hub.getWorld())) return false;
        double r = pl.getConfig().getDouble("lobby-protection.radius", 0);
        return r <= 0 || l.distanceSquared(hub) <= r * r;
    }

    /** True when this player must NOT change anything at this place. */
    private boolean deny(Player p, Location l) {
        if (!protectedAt(l)) return false;
        return p == null || !(builders.contains(p.getUniqueId()) && p.hasPermission("bwx.admin"));
    }

    private void warn(Player p) {
        if (p == null) return;
        long now = System.currentTimeMillis();
        Long last = warned.get(p.getUniqueId());
        if (last != null && now - last < 1500) return;
        warned.put(p.getUniqueId(), now);
        p.sendMessage(Msg.m("lobby-protected"));
    }

    /** The player behind an entity (himself, or the shooter of an arrow / snowball ...). */
    private static Player source(Entity en) {
        if (en instanceof Player) return (Player) en;
        if (en instanceof Projectile) {
            ProjectileSource s = ((Projectile) en).getShooter();
            if (s instanceof Player) return (Player) s;
        }
        return null;
    }

    // ------------------------------------------------------------------ blocks
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (deny(e.getPlayer(), e.getBlock().getLocation())) { e.setCancelled(true); warn(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (deny(e.getPlayer(), e.getBlock().getLocation())) { e.setCancelled(true); warn(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (deny(e.getPlayer(), e.getBlockClicked().getLocation())) { e.setCancelled(true); warn(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (deny(e.getPlayer(), e.getBlockClicked().getLocation())) { e.setCancelled(true); warn(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (deny(e.getPlayer(), e.getBlock().getLocation())) { e.setCancelled(true); warn(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (protectedAt(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (protectedAt(e.getLocation())) e.blockList().clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (protectedAt(e.getBlock().getLocation())) e.blockList().clear();
    }

    /** Endermen, falling blocks, mobs trampling farmland ... */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent e) {
        if (e.getEntity() instanceof Player) {
            if (deny((Player) e.getEntity(), e.getBlock().getLocation())) e.setCancelled(true);
        } else if (protectedAt(e.getBlock().getLocation())) e.setCancelled(true);
    }

    /** Stepping on farmland. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPhysical(PlayerInteractEvent e) {
        if (e.getAction() != Action.PHYSICAL) return;
        Block b = e.getClickedBlock();
        if (b != null && b.getType() == Material.FARMLAND && deny(e.getPlayer(), b.getLocation())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ item frames / paintings / armor stands
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent e) {
        if (deny(e.getPlayer(), e.getEntity().getLocation())) { e.setCancelled(true); warn(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        Player src = source(e.getRemover());
        if (deny(src, e.getEntity().getLocation())) { e.setCancelled(true); warn(src); }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFrameUse(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof ItemFrame)) return;
        if (deny(e.getPlayer(), e.getRightClicked().getLocation())) { e.setCancelled(true); warn(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStand(PlayerArmorStandManipulateEvent e) {
        if (deny(e.getPlayer(), e.getRightClicked().getLocation())) { e.setCancelled(true); warn(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHitDecoration(EntityDamageByEntityEvent e) {
        Entity v = e.getEntity();
        if (!(v instanceof Hanging) && !(v instanceof ArmorStand)) return;
        Player src = source(e.getDamager());
        if (deny(src, v.getLocation())) { e.setCancelled(true); warn(src); }
    }
}
