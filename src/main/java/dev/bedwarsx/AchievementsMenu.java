/*
 * BedWarsX - Copyright (c) 2026 ASHRAF ELHENAWY (ItzRealAshraf). All rights reserved.
 * Copying, re-selling, re-branding or removing the credits is not allowed.
 */
package dev.bedwarsx;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Achievements chest: a 6-row grid (28 per page). Completed ones glow, the others show the progress bar. */
public final class AchievementsMenu {
    private AchievementsMenu() {}

    public static final String TYPE = "HUB_ACH";
    private static final int[] GRID = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private static final int HEAD = 4, PREV = 48, CLOSE = 49, NEXT = 50;

    private static ItemStack hide(ItemStack it) {
        ItemMeta im = it.getItemMeta();
        if (im == null) return it;
        for (String f : new String[]{"HIDE_ENCHANTS", "HIDE_ATTRIBUTES", "HIDE_ADDITIONAL_TOOLTIP", "HIDE_POTION_EFFECTS", "HIDE_UNBREAKABLE"}) {
            try { im.addItemFlags(ItemFlag.valueOf(f)); } catch (Throwable ignored) { /* flag does not exist on this version */ }
        }
        it.setItemMeta(im);
        return it;
    }

    private static String bar(int value, int target, int len) {
        int on = (int) Math.round(Math.min(1.0, (double) value / target) * len);
        StringBuilder sb = new StringBuilder("&a");
        for (int i = 0; i < len; i++) {
            if (i == on) sb.append("&7");
            sb.append('\u25A0');
        }
        return sb.toString();
    }

    private static ItemStack icon(BedWarsX pl, UUID u, Achievements.Ach a) {
        boolean done = pl.ach.done(u, a);
        List<String> lore = new ArrayList<>();
        if (a.weekly) lore.add(Msg.m("ach-menu-weekly", "%days%", "" + Stats.weekDaysLeft()));
        lore.add("&7" + a.desc);
        lore.add("");
        if (done) lore.add(Msg.m("ach-menu-done"));
        else if (a.perGame()) lore.add(Msg.m("ach-menu-single"));
        else {
            int v = Math.min(pl.ach.value(u, a, null), a.target);
            lore.add(Msg.m("ach-menu-progress", "%value%", "" + v, "%target%", "" + a.target, "%percent%", "" + (v * 100 / a.target)));
            lore.add(bar(v, a.target, 20));
        }
        lore.add("");
        lore.add(Msg.m("ach-menu-rewards"));
        if (a.xp > 0) lore.add(Msg.m("ach-menu-xp", "%xp%", "" + a.xp));
        if (a.coins > 0) lore.add(Msg.m("ach-menu-coins", "%coins%", "" + a.coins));
        ItemStack it = U.item(a.icon, 1, (done ? "&a&l" : a.weekly ? "&d&l" : "&e&l") + a.name, lore.toArray(new String[0]));
        if (done) U.addEnch(it, "unbreaking", 1);
        return hide(it);
    }

    public static void open(Player p, BedWarsX pl, int page) {
        if (pl.ach == null || !pl.ach.enabled()) { p.sendMessage(Msg.m("ach-disabled")); return; }
        UUID u = p.getUniqueId();
        List<Achievements.Ach> all = pl.ach.all();
        int pages = Math.max(1, (all.size() + GRID.length - 1) / GRID.length);
        page = Math.max(0, Math.min(page, pages - 1));
        HubMenus.Holder h = new HubMenus.Holder(TYPE);
        h.page = page;
        Inventory inv = Bukkit.createInventory(h, 54, Msg.m("ach-menu-title"));
        h.inventory = inv;
        ItemStack pane = U.item(Material.BLACK_STAINED_GLASS_PANE, 1, " ");
        for (int i = 0; i < 54; i++) if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) inv.setItem(i, pane);
        int done = pl.ach.count(u);
        List<String> head = new ArrayList<>();
        head.add(Msg.m("ach-menu-total", "%done%", "" + done, "%total%", "" + pl.ach.total()));
        if (pl.ach.weeklyTotal() > 0)
            head.add(Msg.m("ach-menu-weekly-total", "%done%", "" + pl.ach.weeklyCount(u), "%total%", "" + pl.ach.weeklyTotal(), "%days%", "" + Stats.weekDaysLeft()));
        inv.setItem(HEAD, U.item(Material.BOOK, 1, Msg.m("ach-menu-head"), head.toArray(new String[0])));
        for (int i = 0; i < GRID.length; i++) {
            int idx = page * GRID.length + i;
            if (idx >= all.size()) break;
            inv.setItem(GRID[i], icon(pl, u, all.get(idx)));
        }
        if (page > 0) inv.setItem(PREV, U.item(Material.ARROW, 1, Msg.m("ach-menu-prev", "%page%", "" + page, "%pages%", "" + pages)));
        inv.setItem(CLOSE, U.item(Material.BARRIER, 1, Msg.m("ach-menu-close")));
        if (page < pages - 1) inv.setItem(NEXT, U.item(Material.ARROW, 1, Msg.m("ach-menu-next", "%page%", "" + (page + 2), "%pages%", "" + pages)));
        p.openInventory(inv);
        U.sound(p, "block.ender_chest.open", 0.6f, 1.3f);
    }

    public static void click(Player p, BedWarsX pl, HubMenus.Holder h, int slot) {
        if (slot == CLOSE) p.closeInventory();
        else if (slot == PREV && h.page > 0) open(p, pl, h.page - 1);
        else if (slot == NEXT) open(p, pl, h.page + 1);
    }
}
