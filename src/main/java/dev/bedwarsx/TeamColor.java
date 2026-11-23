/*
 * BedWarsX - Copyright (c) 2026 ASHRAF ELHENAWY (ItzRealAshraf). All rights reserved.
 * Copying, re-selling, re-branding or removing the credits is not allowed.
 */
package dev.bedwarsx;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;

public enum TeamColor {
    RED(ChatColor.RED, DyeColor.RED, "Red"),
    BLUE(ChatColor.BLUE, DyeColor.BLUE, "Blue"),
    GREEN(ChatColor.GREEN, DyeColor.LIME, "Green"),
    YELLOW(ChatColor.YELLOW, DyeColor.YELLOW, "Yellow"),
    AQUA(ChatColor.AQUA, DyeColor.CYAN, "Aqua"),
    WHITE(ChatColor.WHITE, DyeColor.WHITE, "White"),
    PINK(ChatColor.LIGHT_PURPLE, DyeColor.PINK, "Pink"),
    GRAY(ChatColor.DARK_GRAY, DyeColor.GRAY, "Gray");

    public final ChatColor chat;
    public final DyeColor dye;
    public final String display;

    TeamColor(ChatColor chat, DyeColor dye, String display) {
        this.chat = chat;
        this.dye = dye;
        this.display = display;
    }

    /** e.g. block("WOOL") -> RED_WOOL */
    public Material block(String suffix) { return U.mat(dye.name() + "_" + suffix); }

    public Color rgb() {
        try { return dye.getColor(); } catch (Throwable t) { return Color.WHITE; }
    }

    /** Bed material prefix ("RED", "LIME", "CYAN"...) to team. */
    public static TeamColor ofDye(String dyeName) {
        for (TeamColor c : values()) if (c.dye.name().equalsIgnoreCase(dyeName)) return c;
        return null;
    }

    public static TeamColor of(String s) {
        if (s == null) return null;
        for (TeamColor c : values()) if (c.name().equalsIgnoreCase(s)) return c;
        return null;
    }
}
