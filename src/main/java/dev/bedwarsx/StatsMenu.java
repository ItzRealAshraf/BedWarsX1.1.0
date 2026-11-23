/*
 * BedWarsX - Copyright (c) 2026 ASHRAF ELHENAWY (ItzRealAshraf). All rights reserved.
 * Copying, re-selling, re-branding or removing the credits is not allowed.
 */
package dev.bedwarsx;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The player Stats menu (5 rows): the player's head, level + progress bar and every stat. Everything is editable in gui.yml -> stats-menu. */
public final class StatsMenu {
    private StatsMenu() {}

    public static final String TYPE = "HUB_STATS";
    private static final int SIZE = 45;
    private static final int CLOSE = 40;

    /** key in gui.yml, default slot. */
    private static final String[][] ITEMS = {
            {"level", "22"}, {"wins", "20"}, {"kills", "21"}, {"finals", "23"}, {"beds", "24"},
            {"games", "28"}, {"streak", "30"}, {"performance", "32"}, {"kd", "34"}, {"achievements", "31"}
    };

    private static String[] values(BedWarsX pl, UUID u, String name) {
        Stats s = pl.stats;
        int kills = s.get(u, "kills"), deaths = s.get(u, "deaths"), wins = s.get(u, "wins"), losses = s.get(u, "losses");
        return new String[]{
                "%player%", name,
                "%level%", "" + s.level(u),
                "%next%", "" + (s.level(u) + 1),
                "%xp%", "" + s.get(u, "xp"),
                "%xp_into%", "" + s.xpInto(u),
                "%xp_needed%", "" + s.xpNeeded(u),
                "%percent%", "" + s.percent(u),
                "%bar%", s.bar(u, 20),
                "%wins%", "" + wins,
                "%losses%", "" + losses,
                "%games%", "" + s.get(u, "games"),
                "%wl%", s.ratio(wins, losses),
                "%kills%", "" + kills,
                "%finals%", "" + s.get(u, "finals"),
                "%beds%", "" + s.get(u, "beds"),
                "%deaths%", "" + deaths,
                "%kd%", s.ratio(kills, deaths),
                "%streak%", "" + s.get(u, "streak"),
                "%beststreak%", "" + s.get(u, "beststreak"),
                "%bestscore%", "" + s.get(u, "bestscore"),
                "%bestkills%", "" + s.get(u, "bestkills"),
                "%bestfinals%", "" + s.get(u, "bestfinals"),
                "%bestbeds%", "" + s.get(u, "bestbeds"),
                "%coins%", "" + s.coins(u),
                "%ach%", "" + (pl.ach == null ? 0 : pl.ach.count(u)),
                "%ach_total%", "" + (pl.ach == null ? 0 : pl.ach.total())
        };
    }

    private static String ph(String s, String[] kv) {
        if (s == null) return "";
        for (int i = 0; i + 1 < kv.length; i += 2) s = s.replace(kv[i], kv[i + 1]);
        return U.c(s);
    }

    private static List<String> lore(BedWarsX pl, String path, String[] kv) {
        List<String> out = new ArrayList<>();
        for (String l : pl.guiCfg.getStringList(path + ".lore")) out.add(ph(l, kv));
        return out;
    }

    private static ItemStack icon(BedWarsX pl, String key, String[] kv) {
        String path = "stats-menu.items." + key;
        Material m = Material.matchMaterial(pl.guiCfg.getString(path + ".material", "PAPER"));
        ItemStack it = new ItemStack(m == null ? Material.PAPER : m);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.setDisplayName(ph(pl.guiCfg.getString(path + ".name", key), kv));
            im.setLore(lore(pl, path, kv));
            it.setItemMeta(im);
        }
        return it;
    }

    private static ItemStack head(BedWarsX pl, OfflinePlayer target, String[] kv) {
        String path = "stats-menu.head";
        ItemStack it = new ItemStack(U.mat("PLAYER_HEAD"));
        ItemMeta im = it.getItemMeta();
        if (im instanceof SkullMeta) {
            SkullMeta sm = (SkullMeta) im;
            sm.setOwningPlayer(target);
            sm.setDisplayName(ph(pl.guiCfg.getString(path + ".name", "&e&l%player%"), kv));
            sm.setLore(lore(pl, path, kv));
            it.setItemMeta(sm);
        }
        return it;
    }

    private static int slot(BedWarsX pl, String key, int def) {
        int s = pl.guiCfg.getInt("stats-menu.items." + key + ".slot", def);
        return s >= 0 && s < SIZE ? s : def;
    }

    public static void open(Player viewer, BedWarsX pl, OfflinePlayer target) {
        UUID u = target.getUniqueId();
        String name = target.getName() == null ? "Unknown" : target.getName();
        String[] kv = values(pl, u, name);
        HubMenus.Holder h = new HubMenus.Holder(TYPE);
        Inventory inv = Bukkit.createInventory(h, SIZE, ph(pl.guiCfg.getString("stats-menu.title", "&8%player% &7» &6Stats"), kv));
        h.inventory = inv;

        // decoration: glass border + coloured corners
        Material pane = Material.matchMaterial(pl.guiCfg.getString("stats-menu.filler.material", "BLACK_STAINED_GLASS_PANE"));
        Material accent = Material.matchMaterial(pl.guiCfg.getString("stats-menu.filler.accent", "ORANGE_STAINED_GLASS_PANE"));
        ItemStack fill = U.item(pane == null ? Material.BLACK_STAINED_GLASS_PANE : pane, 1, " ");
        ItemStack acc = U.item(accent == null ? Material.ORANGE_STAINED_GLASS_PANE : accent, 1, " ");
        for (int i = 0; i < SIZE; i++) {
            boolean edge = i < 9 || i >= SIZE - 9 || i % 9 == 0 || i % 9 == 8;
            if (edge) inv.setItem(i, fill);
        }
        for (int i : new int[]{0, 8, 36, 44, 3, 5, 39, 41}) inv.setItem(i, acc);

        inv.setItem(4, head(pl, target, kv));
        for (String[] e : ITEMS) inv.setItem(slot(pl, e[0], Integer.parseInt(e[1])), icon(pl, e[0], kv));
        inv.setItem(slot(pl, "close", CLOSE), icon(pl, "close", kv));

        viewer.openInventory(inv);
        U.sound(viewer, "block.ender_chest.open", 0.6f, 1.3f);
    }

    public static void click(Player p, BedWarsX pl, int slot) {
        if (slot == slot(pl, "close", CLOSE)) p.closeInventory();
        else if (slot == slot(pl, "achievements", 31)) AchievementsMenu.open(p, pl, 0);
    }
}
