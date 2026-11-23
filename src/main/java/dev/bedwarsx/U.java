/*
 * BedWarsX - Copyright (c) 2026 ASHRAF ELHENAWY (ItzRealAshraf). All rights reserved.
 * Copying, re-selling, re-branding or removing the credits is not allowed.
 */
package dev.bedwarsx;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Helpers. Everything that changed between Minecraft versions (Enchantment / PotionEffectType / Sound
 * became interfaces and got renamed) is accessed through reflection or string keys so ONE jar works everywhere.
 */
public final class U {
    private U() {}

    public static String c(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    public static Material mat(String n) {
        Material m = Material.matchMaterial(n);
        return m == null ? Material.STONE : m;
    }

    private static Object byKey(Class<?> c, String key) {
        try {
            Method m = c.getMethod("getByKey", NamespacedKey.class);
            return m.invoke(null, NamespacedKey.minecraft(key));
        } catch (Throwable t) { return null; }
    }

    public static Enchantment ench(String key) { return (Enchantment) byKey(Enchantment.class, key); }

    public static PotionEffectType pot(String key) { return (PotionEffectType) byKey(PotionEffectType.class, key); }

    public static void eff(LivingEntity e, String key, int ticks, int amp) {
        PotionEffectType t = pot(key);
        if (t != null) e.addPotionEffect(new PotionEffect(t, ticks, amp, true, false));
    }

    public static void addEnch(ItemStack i, String key, int lvl) {
        Enchantment e = ench(key);
        if (e != null) i.addUnsafeEnchantment(e, lvl);
    }

    public static ItemStack item(Material m, int amt, String name, String... lore) {
        ItemStack i = new ItemStack(m, amt);
        ItemMeta im = i.getItemMeta();
        if (im != null) {
            if (name != null) im.setDisplayName(c(name));
            if (lore.length > 0) {
                List<String> l = new ArrayList<>();
                for (String s : lore) l.add(c(s));
                im.setLore(l);
            }
            i.setItemMeta(im);
        }
        return i;
    }

    public static ItemStack unbreakable(ItemStack i) {
        ItemMeta im = i.getItemMeta();
        if (im != null) { im.setUnbreakable(true); i.setItemMeta(im); }
        return i;
    }

    private static NamespacedKey tagKey() { return new NamespacedKey(JavaPlugin.getPlugin(BedWarsX.class), "item"); }

    /** Marks an item with an id (survives renaming in gui.yml). */
    public static ItemStack tag(ItemStack i, String id) {
        ItemMeta im = i.getItemMeta();
        if (im != null) { im.getPersistentDataContainer().set(tagKey(), PersistentDataType.STRING, id); i.setItemMeta(im); }
        return i;
    }

    /** The id set by tag(), or "" when the item has none. */
    public static String tagOf(ItemStack i) {
        if (i == null || !i.hasItemMeta()) return "";
        String v = i.getItemMeta().getPersistentDataContainer().get(tagKey(), PersistentDataType.STRING);
        return v == null ? "" : v;
    }

    public static String name(ItemStack i) {
        if (i == null || !i.hasItemMeta() || !i.getItemMeta().hasDisplayName()) return "";
        return ChatColor.stripColor(i.getItemMeta().getDisplayName());
    }

    /** String-key sound (works on every version). */
    public static void sound(Player p, String key, float v, float pitch) {
        try { p.playSound(p.getLocation(), "minecraft:" + key, v, pitch); } catch (Throwable ignored) {}
    }

    public static void title(Player p, String t, String s) { p.sendTitle(c(t), c(s), 5, 40, 10); }

    public static String ls(Location l) {
        return l.getX() + ";" + l.getY() + ";" + l.getZ() + ";" + l.getYaw() + ";" + l.getPitch();
    }

    public static Location sl(String s, World w) {
        String[] a = s.split(";");
        return new Location(w, Double.parseDouble(a[0]), Double.parseDouble(a[1]), Double.parseDouble(a[2]),
                Float.parseFloat(a[3]), Float.parseFloat(a[4]));
    }

    public static String bk(Location l) { return l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ(); }

    public static String mmss(int s) { return String.format("%d:%02d", s / 60, s % 60); }
}
