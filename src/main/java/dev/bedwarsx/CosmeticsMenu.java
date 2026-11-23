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

/** Cosmetics chest: the main menu (one icon per category) and the category menu (a 6-row grid of effects, like Hypixel). */
public final class CosmeticsMenu {
    private CosmeticsMenu() {}

    public static final String MAIN = "HUB_COS", CAT = "HUB_COSC";
    private static final int[] CAT_SLOTS = {20, 21, 22, 23, 24, 30, 31, 32};
    /** Content area of the category menu: 4 rows x 7 columns (the first cell of page 1 is the "No effect" button). */
    private static final int[] GRID = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private static final int BACK = 45, PREV = 48, COINS = 49, NEXT = 50, CLOSE = 53, MAIN_COINS = 4, MAIN_CLOSE = 40;

    private static String ph(String s, String... kv) {
        for (int i = 0; i + 1 < kv.length; i += 2) s = s.replace(kv[i], kv[i + 1]);
        return U.c(s);
    }

    private static ItemStack glow(ItemStack it) {
        U.addEnch(it, "unbreaking", 1);
        return it;
    }

    private static ItemStack hide(ItemStack it) {
        ItemMeta im = it.getItemMeta();
        if (im == null) return it;
        for (String f : new String[]{"HIDE_ENCHANTS", "HIDE_ATTRIBUTES", "HIDE_ADDITIONAL_TOOLTIP", "HIDE_POTION_EFFECTS", "HIDE_UNBREAKABLE"}) {
            try { im.addItemFlags(ItemFlag.valueOf(f)); } catch (Throwable ignored) { /* flag does not exist on this version */ }
        }
        it.setItemMeta(im);
        return it;
    }

    private static ItemStack named(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.setDisplayName(U.c(name));
            List<String> l = new ArrayList<>();
            for (String s : lore) l.add(U.c(s));
            im.setLore(l);
            it.setItemMeta(im);
        }
        return it;
    }

    private static ItemStack coinsItem(BedWarsX pl, UUID u) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Earn coins by playing:");
        lore.add("&7kills, beds, wins...");
        lore.add("");
        lore.add(pl.cos.freeAll() ? "&aEverything is FREE right now!" : "&7Spend them on cosmetics.");
        return named(Material.EMERALD, ph(pl.cos.text("coins-name", "&6&lCOINS: &e%coins%"), "%coins%", "" + pl.stats.coins(u)), lore);
    }

    private static void border(Inventory inv) {
        ItemStack pane = U.item(Material.BLACK_STAINED_GLASS_PANE, 1, " ");
        int size = inv.getSize();
        for (int i = 0; i < size; i++) if (i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8) inv.setItem(i, pane);
    }

    // ------------------------------------------------------------------ main menu
    public static void openMain(Player p, BedWarsX pl) {
        if (!pl.cos.enabled()) { p.sendMessage(Msg.m("cos-disabled")); return; }
        UUID u = p.getUniqueId();
        HubMenus.Holder h = new HubMenus.Holder(MAIN);
        Inventory inv = Bukkit.createInventory(h, 45, U.c(pl.cos.text("main-title", "&8Cosmetics")));
        h.inventory = inv;
        border(inv);
        inv.setItem(MAIN_COINS, coinsItem(pl, u));
        for (int i = 0; i < Cosmetics.CATS.length; i++) {
            String cat = Cosmetics.CATS[i];
            Material m = Material.matchMaterial(pl.cos.catIcon(cat));
            List<String> lore = new ArrayList<>();
            for (String d : pl.cos.catDesc(cat)) lore.add(d);
            Cosmetics.Item sel = pl.cos.selected(u, cat);
            lore.add("");
            lore.add(ph(pl.cos.text("main-selected", "&7Selected: %name%"), "%name%", sel == null ? "&cNone" : sel.name));
            lore.add(ph(pl.cos.text("main-unlocked", "&7Unlocked: &a%owned%&8/&a%total%"), "%owned%", "" + pl.cos.owned(u, cat), "%total%", "" + pl.cos.list(cat).size()));
            lore.add("");
            lore.add(U.c(pl.cos.text("main-click", "&eClick to open!")));
            ItemStack it = named(m == null ? Material.CHEST : m, pl.cos.catName(cat), lore);
            inv.setItem(CAT_SLOTS[i], hide(sel == null ? it : glow(it)));
        }
        inv.setItem(MAIN_CLOSE, U.item(Material.BARRIER, 1, "&cClose"));
        p.openInventory(inv);
        U.sound(p, "block.ender_chest.open", 0.7f, 1f);
    }

    // ------------------------------------------------------------------ category menu
    private static ItemStack icon(BedWarsX pl, Player p, Cosmetics.Item it, boolean selected, boolean pending) {
        UUID u = p.getUniqueId();
        List<String> lore = new ArrayList<>();
        for (String l : it.lore) lore.add(l);
        lore.add("");
        boolean unlocked = pl.cos.unlocked(u, it);
        if (selected) {
            lore.add(pl.cos.text("selected", "&a&l\u2714 SELECTED"));
            lore.add(pl.cos.text("click-deselect", "&7Click to unequip"));
        } else if (unlocked) {
            lore.add(pl.cos.text("unlocked", "&aUnlocked"));
            lore.add(pl.cos.text("click-select", "&eClick to select!"));
        } else {
            lore.add(ph(pl.cos.text("price", "&7Price: &6%price% Coins"), "%price%", "" + it.price));
            if (pending) lore.add(pl.cos.text("click-confirm", "&a&lClick again to confirm!"));
            else if (pl.stats.coins(u) >= it.price) lore.add(pl.cos.text("click-buy", "&eClick to buy!"));
            else lore.add(ph(pl.cos.text("no-coins", "&cNot enough Coins! &7(You have &6%coins%&7)"), "%coins%", "" + pl.stats.coins(u)));
        }
        ItemStack st = named(it.mat, it.name, lore);
        return hide(selected ? glow(st) : st);
    }

    private static List<Cosmetics.Item> entries(BedWarsX pl, String cat) {
        List<Cosmetics.Item> l = new ArrayList<>();
        l.add(null); // "No effect"
        l.addAll(pl.cos.list(cat));
        return l;
    }

    public static void openCat(Player p, BedWarsX pl, String cat, int page) {
        UUID u = p.getUniqueId();
        List<Cosmetics.Item> all = entries(pl, cat);
        int pages = Math.max(1, (all.size() + GRID.length - 1) / GRID.length);
        page = Math.max(0, Math.min(page, pages - 1));
        HubMenus.Holder h = new HubMenus.Holder(CAT);
        h.mode = cat;
        h.page = page;
        Inventory inv = Bukkit.createInventory(h, 54, U.c(pl.cos.catTitle(cat)));
        h.inventory = inv;
        border(inv);
        Cosmetics.Item sel = pl.cos.selected(u, cat);
        for (int i = 0; i < GRID.length; i++) {
            int idx = page * GRID.length + i;
            if (idx >= all.size()) break;
            Cosmetics.Item it = all.get(idx);
            if (it == null) {
                ItemStack none = named(Material.BARRIER, pl.cos.text("none-name", "&c&lNo Effect"), java.util.Arrays.asList(pl.cos.text("none-lore", "&7Click to remove your equipped effect")));
                inv.setItem(GRID[i], hide(sel == null ? glow(none) : none));
            } else inv.setItem(GRID[i], icon(pl, p, it, sel != null && sel.id.equals(it.id), false));
        }
        inv.setItem(BACK, U.item(Material.ARROW, 1, pl.cos.text("back", "&aBack")));
        inv.setItem(COINS, coinsItem(pl, u));
        if (page > 0) inv.setItem(PREV, U.item(Material.ARROW, 1, ph(pl.cos.text("prev", "&ePrevious page (%page%/%pages%)"), "%page%", "" + page, "%pages%", "" + pages)));
        if (page < pages - 1) inv.setItem(NEXT, U.item(Material.ARROW, 1, ph(pl.cos.text("next", "&eNext page (%page%/%pages%)"), "%page%", "" + (page + 2), "%pages%", "" + pages)));
        inv.setItem(CLOSE, U.item(Material.BARRIER, 1, "&cClose"));
        p.openInventory(inv);
    }

    // ------------------------------------------------------------------ clicks
    public static void click(Player p, BedWarsX pl, HubMenus.Holder h, int slot) {
        if (h.type.equals(MAIN)) {
            if (slot == MAIN_CLOSE) { p.closeInventory(); return; }
            for (int i = 0; i < CAT_SLOTS.length; i++)
                if (CAT_SLOTS[i] == slot) { U.sound(p, "ui.button.click", 0.6f, 1.2f); openCat(p, pl, Cosmetics.CATS[i], 0); return; }
            return;
        }
        String cat = h.mode;
        if (slot == BACK) { openMain(p, pl); return; }
        if (slot == CLOSE) { p.closeInventory(); return; }
        if (slot == PREV) { openCat(p, pl, cat, h.page - 1); return; }
        if (slot == NEXT) { openCat(p, pl, cat, h.page + 1); return; }
        int gi = -1;
        for (int i = 0; i < GRID.length; i++) if (GRID[i] == slot) gi = i;
        if (gi < 0) return;
        List<Cosmetics.Item> all = entries(pl, cat);
        int idx = h.page * GRID.length + gi;
        if (idx >= all.size()) return;
        UUID u = p.getUniqueId();
        Cosmetics.Item it = all.get(idx);
        if (it == null) {
            pl.stats.select(u, cat, null);
            p.sendMessage(Msg.m("cos-deselected"));
            U.sound(p, "ui.button.click", 0.7f, 0.8f);
            openCat(p, pl, cat, h.page);
            return;
        }
        Cosmetics.Item sel = pl.cos.selected(u, cat);
        if (pl.cos.unlocked(u, it)) {
            if (sel != null && sel.id.equals(it.id)) {
                pl.stats.select(u, cat, null);
                p.sendMessage(Msg.m("cos-deselected"));
            } else {
                pl.stats.select(u, cat, it.id);
                p.sendMessage(Msg.m("cos-selected", "%name%", it.name));
                U.sound(p, "entity.experience_orb.pickup", 0.8f, 1.3f);
            }
            pl.stats.save();
            openCat(p, pl, cat, h.page);
            return;
        }
        if (pl.stats.coins(u) < it.price) {
            p.sendMessage(Msg.m("cos-no-coins", "%price%", "" + it.price, "%coins%", "" + pl.stats.coins(u)));
            U.sound(p, "entity.villager.no", 0.8f, 1f);
            return;
        }
        if (!it.key().equals(h.pending)) { // first click: ask to confirm
            h.pending = it.key();
            h.inventory.setItem(slot, icon(pl, p, it, false, true));
            U.sound(p, "ui.button.click", 0.7f, 1f);
            return;
        }
        if (pl.cos.buy(p, it) == 0) {
            pl.stats.select(u, cat, it.id);
            pl.stats.save();
            p.sendMessage(Msg.m("cos-bought", "%name%", it.name, "%price%", "" + it.price));
            U.sound(p, "entity.player.levelup", 0.8f, 1.4f);
        }
        openCat(p, pl, cat, h.page);
    }
}
