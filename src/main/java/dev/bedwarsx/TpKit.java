/*
 * BedWarsX - Copyright (c) 2026 ASHRAF ELHENAWY (ItzRealAshraf). All rights reserved.
 * Copying, re-selling, re-branding or removing the credits is not allowed.
 */
package dev.bedwarsx;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * /bw tp &lt;map&gt;: the admin's real items are stored, he only gets one bed that sends him back to the BedWars lobby.
 * In the lobby (bed click or /bw tp lobby) everything comes back. The items are also written to plugins/BedWarsX/tp-kits
 * so a crash or restart can never lose them (they are returned when the admin joins again).
 */
public final class TpKit {
    /** Item id of the return bed (last path segment of hotbar.tp-kit.tp-return in gui.yml). */
    public static final String TAG = "tp-return";

    private final BedWarsX pl;
    private final Set<UUID> active = new HashSet<>();

    TpKit(BedWarsX pl) { this.pl = pl; }

    private File file(UUID id) {
        File d = new File(pl.getDataFolder(), "tp-kits");
        d.mkdirs();
        return new File(d, id + ".yml");
    }

    public boolean has(Player p) { return active.contains(p.getUniqueId()); }

    /** Stores the real items (first time only) and gives the return bed. The admin flies, cannot build and is hidden. */
    public void enter(Player p) {
        UUID id = p.getUniqueId();
        if (!active.contains(id)) {
            save(p);
            active.add(id);
        }
        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[4]);
        HubMenus.setHot(pl, p, "hotbar.tp-kit." + TAG);
        p.getInventory().setHeldItemSlot(Math.max(0, Math.min(8, pl.guiCfg.getInt("hotbar.tp-kit." + TAG + ".slot", 4))));
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(true);
        p.setFlying(true);
        p.setCollidable(false);
        p.setInvulnerable(true);
        U.eff(p, "invisibility", Integer.MAX_VALUE, 0);
        p.updateInventory();
        p.sendMessage(Msg.m("tp-kit-given"));
    }

    private void save(Player p) {
        YamlConfiguration y = new YamlConfiguration();
        ItemStack[] c = p.getInventory().getContents();
        y.set("size", c.length);
        y.set("inv", Arrays.asList(c));
        y.set("armor", Arrays.asList(p.getInventory().getArmorContents()));
        y.set("gm", p.getGameMode().name());
        y.set("allow-flight", p.getAllowFlight());
        y.set("flying", p.isFlying());
        try {
            y.save(file(p.getUniqueId()));
        } catch (IOException ex) {
            pl.getLogger().warning("Could not store the items of " + p.getName() + ": " + ex.getMessage());
        }
    }

    private static ItemStack[] items(List<?> l, int size) {
        ItemStack[] a = new ItemStack[size];
        if (l != null) for (int i = 0; i < l.size() && i < size; i++) if (l.get(i) instanceof ItemStack) a[i] = (ItemStack) l.get(i);
        return a;
    }

    /** Gives the stored items back (also after a crash). Returns false when nothing was stored. */
    public boolean restore(Player p) {
        UUID id = p.getUniqueId();
        File f = file(id);
        active.remove(id);
        if (!f.isFile()) return false;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        int size = y.getInt("size", 41);
        p.getInventory().clear();
        try {
            p.getInventory().setContents(items(y.getList("inv"), size));
        } catch (IllegalArgumentException ex) { // inventory layout of another server version
            for (ItemStack it : items(y.getList("inv"), size)) if (it != null) p.getInventory().addItem(it);
        }
        p.getInventory().setArmorContents(items(y.getList("armor"), 4));
        try {
            p.setGameMode(GameMode.valueOf(y.getString("gm", "SURVIVAL")));
        } catch (IllegalArgumentException ignored) { p.setGameMode(GameMode.SURVIVAL); }
        boolean flight = y.getBoolean("allow-flight", false);
        p.setAllowFlight(flight);
        p.setFlying(flight && y.getBoolean("flying", false));
        p.setCollidable(true);
        p.setInvulnerable(false);
        PotionEffectType inv = U.pot("invisibility");
        if (inv != null) p.removePotionEffect(inv);
        p.updateInventory();
        f.delete();
        return true;
    }

    /** Bed click or /bw tp lobby: items back + teleport to the BedWars lobby. Returns true when items were returned. */
    public boolean returnToLobby(Player p) {
        boolean back = restore(p);
        Location hub = pl.hubLocation();
        if (hub != null) p.teleport(hub);
        if (back) p.sendMessage(Msg.m("tp-kit-returned"));
        return back;
    }

    /** Server stop / reload: nobody keeps the bed-only inventory. */
    public void restoreAll() {
        for (Player p : Bukkit.getOnlinePlayers()) if (active.contains(p.getUniqueId())) restore(p);
    }
}
