/*
 * BedWarsX - Copyright (c) 2026 ASHRAF ELHENAWY (ItzRealAshraf). All rights reserved.
 * Copying, re-selling, re-branding or removing the credits is not allowed.
 */
package dev.bedwarsx;

import org.bukkit.entity.Firework;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class CosmeticsListener implements Listener {
    private final BedWarsX pl;

    public CosmeticsListener(BedWarsX pl) { this.pl = pl; }

    @EventHandler
    public void onLaunch(ProjectileLaunchEvent e) { pl.cos.onLaunch(e.getEntity()); }

    /** Cosmetic fireworks must never hurt anybody inside a BedWars world. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFirework(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Firework && pl.mgr.byWorld(e.getEntity().getWorld()) != null) e.setCancelled(true);
    }

    /** Old players unlock the achievements their stats already reached. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (pl.ach == null || !pl.ach.joinCheck()) return;
        final java.util.UUID id = e.getPlayer().getUniqueId();
        pl.getServer().getScheduler().runTaskLater(pl, new Runnable() {
            @Override public void run() {
                org.bukkit.entity.Player p = pl.getServer().getPlayer(id);
                if (p != null) pl.ach.check(p);
            }
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { pl.cos.forget(e.getPlayer().getUniqueId()); }
}
