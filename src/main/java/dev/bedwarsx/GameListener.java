/*
 * BedWarsX - Copyright (c) 2026 ASHRAF ELHENAWY (ItzRealAshraf). All rights reserved.
 * Copying, re-selling, re-branding or removing the credits is not allowed.
 */
package dev.bedwarsx;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

public class GameListener implements Listener {
    final BedWarsX pl;
    final ArenaManager mgr;
    private final Map<UUID, Long> fireCd = new HashMap<>();

    public GameListener(BedWarsX pl) { this.pl = pl; this.mgr = pl.mgr; }

    private Arena.PD pd(Arena a, Player p) { return a.players.get(p.getUniqueId()); }

    private boolean inPlay(Arena a, Arena.PD d) { return a.state == Arena.State.PLAYING && d != null && d.alive && !d.respawning; }

    private void consumeHand(Player p) {
        ItemStack h = p.getInventory().getItemInMainHand();
        if (h == null) return;
        h.setAmount(h.getAmount() - 1);
        p.getInventory().setItemInMainHand(h.getAmount() <= 0 ? null : h);
    }

    // ------------------------------------------------------------------ join / quit
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        pl.stats.setName(p.getUniqueId(), p.getName());
        pl.tpKit.restore(p); // items of an admin who left (or the server crashed) while using /bw tp
        final Arena a = mgr.rejoin.get(p.getUniqueId());
        Bukkit.getScheduler().runTaskLater(pl, new Runnable() {
            @Override public void run() {
                if (!p.isOnline() || mgr.of(p) != null || mgr.rejoin.containsKey(p.getUniqueId())) return;
                String spawn = pl.getConfig().getString("hub.spawn", "");
                if (pl.getConfig().getBoolean("hub.teleport-on-join", false) && spawn != null && !spawn.isEmpty()) {
                    try {
                        String[] parts = spawn.split(";", 2);
                        org.bukkit.World world = Bukkit.getWorld(parts[0]);
                        if (world != null && parts.length == 2) p.teleport(U.sl(parts[1], world));
                    } catch (Exception ex) { pl.getLogger().warning("Invalid hub.spawn config: " + ex.getMessage()); }
                }
                HubMenus.giveItems(p);
                pl.ui.refresh(p);
                pl.ui.syncVisibilitySoon();
            }
        }, 2L);
        // show the TAB / scoreboard instantly, before the delayed task above runs
        pl.ui.refresh(p);
        if (a != null) {
            Bukkit.getScheduler().runTaskLater(pl, new Runnable() {
                @Override public void run() {
                    if (p.isOnline() && a.rejoin(p)) p.sendMessage(Msg.m("rejoined"));
                }
            }, 10L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pl.tpKit.restore(e.getPlayer()); // never save the bed-only /bw tp inventory
        Arena a = mgr.of(e.getPlayer());
        if (a != null) a.quit(e.getPlayer());
        pl.parties.leave(e.getPlayer().getUniqueId());
        pl.ui.forget(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        if (pl.ui != null) pl.ui.onChunkLoad(e.getChunk());
    }

    // ------------------------------------------------------------------ game mode
    /** Nobody (not even an op / the owner) can switch to creative while he is inside a game. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        if (e.getNewGameMode() != org.bukkit.GameMode.CREATIVE) return;
        Player p = e.getPlayer();
        if (mgr.of(p) == null) return;
        e.setCancelled(true);
        p.sendMessage(Msg.m("no-creative-in-game"));
    }

    // ------------------------------------------------------------------ blocks
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Arena a = mgr.of(p);
        if (a == null) return;
        Arena.PD d = pd(a, p);
        if (!inPlay(a, d)) { e.setCancelled(true); return; }
        Block b = e.getBlock();
        if (b.getY() >= a.world.getMaxHeight() - 1) {
            e.setCancelled(true);
            p.sendMessage(Msg.m("build-limit"));
            return;
        }
        if (a.inBase(b.getLocation(), d.team)) {
            e.setCancelled(true);
            p.sendMessage(Msg.m("base-protected"));
            return;
        }
        if (b.getType() == Material.CHEST && U.name(e.getItemInHand()).equals("Pop-up Tower")) {
            a.popTower(p, b, d);
            return;
        }
        if (b.getType() == Material.TNT) {
            e.setCancelled(true);
            consumeHand(p);
            TNTPrimed t = a.world.spawn(b.getLocation().add(0.5, 0, 0.5), TNTPrimed.class);
            t.setFuseTicks(40);
            a.tntOwner.put(t.getUniqueId(), p.getUniqueId());
            return;
        }
        a.placed.add(U.bk(b.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Arena a = mgr.of(p);
        if (a == null) return;
        Arena.PD d = pd(a, p);
        if (!inPlay(a, d)) { e.setCancelled(true); return; }
        Block b = e.getBlock();
        Arena.BT t = a.bedAt(b);
        if (t != null) {
            e.setCancelled(true);
            if (t.color == d.team) { p.sendMessage(Msg.m("own-bed")); return; }
            a.bedBroken(t, p);
            return;
        }
        if (a.placed.remove(U.bk(b.getLocation()))) return;
        e.setCancelled(true);
        p.sendMessage(Msg.m("break-denied"));
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        Arena a = mgr.byWorld(e.getLocation().getWorld());
        if (a == null) return;
        Iterator<Block> it = e.blockList().iterator();
        while (it.hasNext()) {
            Block b = it.next();
            String n = b.getType().name();
            if (n.contains("GLASS") || n.endsWith("_BED")) { it.remove(); continue; }
            if (!a.placed.remove(U.bk(b.getLocation()))) it.remove();
        }
    }

    @EventHandler
    public void onIgnite(BlockIgniteEvent e) {
        if (mgr.isGameWorld(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler
    public void onWeather(WeatherChangeEvent e) {
        if (mgr.isGameWorld(e.getWorld()) && e.toWeatherState()) e.setCancelled(true);
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent e) {
        if (!mgr.isGameWorld(e.getLocation().getWorld())) return;
        CreatureSpawnEvent.SpawnReason r = e.getSpawnReason();
        if (r == CreatureSpawnEvent.SpawnReason.NATURAL || r == CreatureSpawnEvent.SpawnReason.CHUNK_GEN) e.setCancelled(true);
    }

    @EventHandler
    public void onStand(PlayerArmorStandManipulateEvent e) {
        if (mgr.isGameWorld(e.getRightClicked().getWorld())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ damage / death
    private Player attacker(Arena a, Entity dm) {
        if (dm instanceof Player) return (Player) dm;
        if (dm instanceof Projectile) {
            ProjectileSource s = ((Projectile) dm).getShooter();
            if (s instanceof Player) return (Player) s;
        }
        if (dm instanceof TNTPrimed) {
            UUID o = a.tntOwner.get(dm.getUniqueId());
            if (o != null) return Bukkit.getPlayer(o);
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        Entity en = e.getEntity();
        Arena wa = mgr.byWorld(en.getWorld());
        if (wa != null && (en instanceof Villager || en instanceof ArmorStand)) { e.setCancelled(true); return; }
        if (!(en instanceof Player)) return;
        Player p = (Player) en;
        Arena a = mgr.of(p);
        if (a == null) return;
        Arena.PD d = pd(a, p);
        if (!inPlay(a, d)) {
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
                if (a.state == Arena.State.PLAYING || a.state == Arena.State.ENDING) p.teleport(a.specLoc());
                else p.teleport(a.L(a.lobby));
            }
            return;
        }
        Player at = null;
        if (e instanceof EntityDamageByEntityEvent) {
            Entity dm = ((EntityDamageByEntityEvent) e).getDamager();
            at = attacker(a, dm);
            if (at != null) {
                Arena.PD ad = pd(a, at);
                if (ad == null || !ad.alive || ad.respawning) { e.setCancelled(true); return; }
                if (ad.team == d.team && at != p && !(dm instanceof TNTPrimed) && !(dm instanceof Fireball)) {
                    e.setCancelled(true);
                    return;
                }
                if (at != p) { d.lastHit = at.getUniqueId(); d.lastHitAt = System.currentTimeMillis(); }
            } else if (a.mobTeam.get(dm.getUniqueId()) == d.team) {
                e.setCancelled(true);
                return;
            }
        }
        if (p.getHealth() - e.getFinalDamage() <= 0) {
            e.setCancelled(true);
            Player k = null;
            if (at != null && at != p) k = at;
            else if (d.lastHit != null && System.currentTimeMillis() - d.lastHitAt < 15000) k = Bukkit.getPlayer(d.lastHit);
            a.onDeath(p, k, e.getCause());
        }
    }

    /** Water / lava buckets can not be used inside a protected team base either. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(org.bukkit.event.player.PlayerBucketEmptyEvent e) {
        Player p = e.getPlayer();
        Arena a = mgr.of(p);
        if (a == null) return;
        Arena.PD d = pd(a, p);
        if (!inPlay(a, d)) return;
        if (a.inBase(e.getBlockClicked().getRelative(e.getBlockFace()).getLocation(), d.team)) {
            e.setCancelled(true);
            p.sendMessage(Msg.m("base-protected"));
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player && mgr.of((Player) e.getEntity()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onTarget(EntityTargetEvent e) {
        Arena a = mgr.byWorld(e.getEntity().getWorld());
        if (a == null) return;
        TeamColor tc = a.mobTeam.get(e.getEntity().getUniqueId());
        if (tc != null && e.getTarget() instanceof Player) {
            Arena.PD d = a.players.get(e.getTarget().getUniqueId());
            if (d != null && d.team == tc) e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ items / interaction
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        Arena a = mgr.of(p);
        Action ac = e.getAction();
        boolean right = ac == Action.RIGHT_CLICK_AIR || ac == Action.RIGHT_CLICK_BLOCK;
        ItemStack it = e.getItem();
        String n = U.name(it);
        String tag = U.tagOf(it);
        if (a == null) {
            if (!right) return;
            if (tag.equals(HubMenus.T_MAP)) { e.setCancelled(true); HubMenus.openMaps(p, pl); }
            else if (tag.equals(HubMenus.T_PARTY)) { e.setCancelled(true); HubMenus.openParty(p, pl); }
            else if (tag.equals(HubMenus.T_STATS)) { e.setCancelled(true); StatsMenu.open(p, pl, p); }
            else if (tag.equals(HubMenus.T_COS)) { e.setCancelled(true); CosmeticsMenu.openMain(p, pl); }
            else if (tag.equals(HubMenus.T_MAIN)) {
                e.setCancelled(true);
                sendToMainHub(p);
            }
            else if (tag.equals(TpKit.TAG)) { e.setCancelled(true); pl.tpKit.returnToLobby(p); }
            return;
        }
        Arena.PD d = pd(a, p);
        if (!right) {
            if (a.state != Arena.State.PLAYING || !inPlay(a, d)) e.setCancelled(true);
            return;
        }
        if (tag.equals("leave") || tag.equals("lobby")) {
            e.setCancelled(true);
            a.leave(p);
            teleportToBedWarsHub(p);
            return;
        }
        if (a.state == Arena.State.WAITING || a.state == Arena.State.STARTING) {
            e.setCancelled(true);
            if (tag.equals("team-selector")) Menus.openTeamSelect(p, a);
            return;
        }
        if (d == null) return;
        if (!inPlay(a, d)) {
            e.setCancelled(true);
            if (tag.equals("teleporter")) Menus.openSpectator(p, a);
            else if (tag.equals("play-again")) a.rematch(p);
            else if (tag.equals("lobby")) { a.leave(p); teleportToBedWarsHub(p); }
            return;
        }
        if (it == null) return;
        if (n.equals("Fireball") && it.getType().name().equals("FIRE_CHARGE")) {
            e.setCancelled(true);
            long now = System.currentTimeMillis();
            Long last = fireCd.get(p.getUniqueId());
            if (last != null && now - last < 500) return;
            fireCd.put(p.getUniqueId(), now);
            consumeHand(p);
            Fireball fb = p.launchProjectile(Fireball.class);
            fb.setYield(2.5f);
            fb.setIsIncendiary(false);
            fb.setDirection(p.getLocation().getDirection());
        } else if (n.equals("Dream Defender")) {
            e.setCancelled(true);
            consumeHand(p);
            a.summon(p, d, EntityType.IRON_GOLEM, p.getLocation(), 240);
        }
    }

    /**
     * Main network hub (a different server). Uses the BungeeCord plugin channel, which Bungee and Velocity both
     * understand. p.performCommand() only runs commands on THIS server, so "server lobby" never reached the proxy.
     */
    private void sendToMainHub(Player p) {
        String server = pl.getConfig().getString("hub.main-server", "lobby");
        if (server != null && !server.trim().isEmpty()) {
            try {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
                out.writeUTF("Connect");
                out.writeUTF(server.trim());
                p.sendPluginMessage(pl, "BungeeCord", bytes.toByteArray());
                return;
            } catch (java.io.IOException ex) {
                pl.getLogger().warning("Could not send player to main hub: " + ex.getMessage());
            }
        }
        String command = pl.getConfig().getString("hub.return-command", "");
        if (command != null && !command.trim().isEmpty()) p.performCommand(command.trim().replaceFirst("^/", ""));
        else p.sendMessage(U.c("&cThe main hub is not configured (hub.main-server in config.yml)."));
    }

    private static boolean isHubItem(ItemStack it) {
        String t = U.tagOf(it);
        return t.equals(HubMenus.T_MAP) || t.equals(HubMenus.T_PARTY) || t.equals(HubMenus.T_STATS) || t.equals(HubMenus.T_COS) || t.equals(HubMenus.T_MAIN) || t.equals(TpKit.TAG);
    }

    private void teleportToBedWarsHub(Player p) {
        String spawn = pl.getConfig().getString("hub.spawn", "");
        if (spawn == null || spawn.trim().isEmpty()) {
            p.sendMessage(U.c("&cBedWars hub is not set yet. Ask an admin to use /bw sethub."));
            return;
        }
        try {
            String[] parts = spawn.split(";", 2);
            org.bukkit.World world = parts.length == 2 ? Bukkit.getWorld(parts[0]) : null;
            if (world == null) {
                p.sendMessage(U.c("&cThe BedWars hub world is unavailable."));
                return;
            }
            p.teleport(U.sl(parts[1], world));
            p.setGameMode(org.bukkit.GameMode.ADVENTURE);
            p.setAllowFlight(false);
            p.setFlying(false);
            p.setCollidable(true);
            p.getInventory().clear();
            p.getInventory().setArmorContents(new ItemStack[4]);
            HubMenus.giveItems(p);
        } catch (Exception ex) {
            pl.getLogger().warning("Could not teleport player to BedWars hub: " + ex.getMessage());
            p.sendMessage(U.c("&cCould not teleport to the BedWars hub. Check the hub configuration."));
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        Arena a = mgr.of(p);
        if (a == null) return;
        String s = a.shopMap.get(e.getRightClicked().getUniqueId());
        if (s == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!inPlay(a, pd(a, p))) return;
        if (s.equals("ITEM")) Menus.openShop(p, a, "Quick Buy");
        else Menus.openUpgrades(p, a);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        InventoryHolder h = e.getInventory().getHolder();
        if (h instanceof HubMenus.Holder) {
            e.setCancelled(true);
            if (e.getClickedInventory() != null && e.getClickedInventory().getHolder() instanceof HubMenus.Holder)
                HubMenus.click(p, pl, (HubMenus.Holder) h, e.getSlot());
            return;
        }
        Arena a = mgr.of(p);
        if (h instanceof Menus.H) {
            e.setCancelled(true);
            if (a == null) return;
            if (e.getClickedInventory() != null && e.getClickedInventory().getHolder() instanceof Menus.H)
                Menus.click(p, a, (Menus.H) h, e.getSlot(), e.isShiftClick());
            return;
        }
        if (a == null) {
            if (isHubItem(e.getCurrentItem()) || isHubItem(e.getCursor())) e.setCancelled(true);
            return;
        }
        if (!inPlay(a, pd(a, p))) { e.setCancelled(true); return; }
        if (e.getSlotType() == InventoryType.SlotType.ARMOR) e.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menus.H || e.getInventory().getHolder() instanceof HubMenus.Holder) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Arena a = mgr.of(e.getPlayer());
        if (a == null && isHubItem(e.getItemDrop().getItemStack())) { e.setCancelled(true); return; }
        if (a != null && !inPlay(a, pd(a, e.getPlayer()))) e.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        Arena a = mgr.of(p);
        if (a != null && !inPlay(a, pd(a, p))) e.setCancelled(true);
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        Arena a = mgr.of(p);
        if (a == null) return;
        ItemStack it = e.getItem();
        if (it.getType() == Material.MILK_BUCKET && U.name(it).equals("Magic Milk")) {
            e.setCancelled(true);
            consumeHand(p);
            a.immune.put(p.getUniqueId(), System.currentTimeMillis() + 30000L);
            p.sendMessage(Msg.m("milk-msg"));
        }
    }

    // ------------------------------------------------------------------ projectiles
    @EventHandler
    public void onLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity() instanceof Egg)) return;
        ProjectileSource s = e.getEntity().getShooter();
        if (!(s instanceof Player)) return;
        final Player p = (Player) s;
        final Arena a = mgr.of(p);
        if (a == null) return;
        Arena.PD d = pd(a, p);
        if (!inPlay(a, d)) return;
        final Egg egg = (Egg) e.getEntity();
        final Material wool = d.team.block("WOOL");
        final int baseY = p.getLocation().getBlockY() - 1;
        final Location start = p.getLocation();
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t++;
                if (!egg.isValid() || t > 40) { cancel(); return; }
                Location l = egg.getLocation();
                if (l.distanceSquared(start) < 4) return;
                Block b = l.getWorld().getBlockAt(l.getBlockX(), baseY, l.getBlockZ());
                if (b.getType() == Material.AIR) {
                    b.setType(wool);
                    a.placed.add(U.bk(b.getLocation()));
                    U.sound(p, "block.wool.place", 1f, 1f);
                }
            }
        }.runTaskTimer(pl, 1L, 1L);
    }

    @EventHandler
    public void onEggThrow(PlayerEggThrowEvent e) {
        if (mgr.of(e.getPlayer()) != null) e.setHatching(false);
    }

    @EventHandler
    public void onHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Snowball)) return;
        ProjectileSource s = e.getEntity().getShooter();
        if (!(s instanceof Player)) return;
        Player p = (Player) s;
        Arena a = mgr.of(p);
        if (a == null) return;
        Arena.PD d = pd(a, p);
        if (!inPlay(a, d)) return;
        a.summon(p, d, EntityType.SILVERFISH, e.getEntity().getLocation().add(0, 0.5, 0), 15);
    }

    // ------------------------------------------------------------------ chat / commands
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        Arena a = mgr.of(p);
        if (a == null) return;
        Arena.PD d = pd(a, p);
        if (d == null) return;
        e.getRecipients().clear();
        String white = ChatColor.WHITE.toString();
        String lp = pl.getConfig().getBoolean("levels.chat-prefix", true)
                ? Msg.m("level-prefix", "%level%", "" + pl.stats.level(p.getUniqueId())) : "";
        if (a.state != Arena.State.PLAYING) {
            for (UUID u : a.players.keySet()) { Player q = Bukkit.getPlayer(u); if (q != null) e.getRecipients().add(q); }
            String c = d.team == null ? ChatColor.GRAY.toString() : d.team.chat.toString();
            e.setFormat(lp + c + "%1$s" + white + ": %2$s");
            return;
        }
        if (!d.alive || d.respawning) {
            for (Map.Entry<UUID, Arena.PD> en : a.players.entrySet()) {
                if (en.getValue().alive && !en.getValue().respawning) continue;
                Player q = Bukkit.getPlayer(en.getKey());
                if (q != null) e.getRecipients().add(q);
            }
            e.setFormat(lp + Msg.m("chat-spectator") + "%1$s" + white + ": %2$s");
            return;
        }
        String c = d.team.chat.toString();
        if (e.getMessage().startsWith("!") && e.getMessage().length() > 1) {
            e.setMessage(e.getMessage().substring(1).trim());
            for (UUID u : a.players.keySet()) { Player q = Bukkit.getPlayer(u); if (q != null) e.getRecipients().add(q); }
            e.setFormat(lp + Msg.m("chat-shout") + c + "[" + d.team.display + "] %1$s" + white + ": %2$s");
        } else {
            for (Player q : a.online(d.team)) e.getRecipients().add(q);
            e.setFormat(lp + c + Msg.m("chat-team") + "%1$s" + white + ": %2$s");
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (mgr.of(p) == null || p.hasPermission("bwx.admin")) return;
        String m = e.getMessage().toLowerCase();
        for (String c : pl.getConfig().getStringList("allowed-commands"))
            if (m.equals(c) || m.startsWith(c + " ")) return;
        e.setCancelled(true);
        p.sendMessage(Msg.m("cmd-blocked"));
    }
}
