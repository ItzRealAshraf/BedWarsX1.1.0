/*
 * BedWarsX - Copyright (c) 2026 ASHRAF ELHENAWY (ItzRealAshraf). All rights reserved.
 * Copying, re-selling, re-branding or removing the credits is not allowed.
 */
package dev.bedwarsx;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Ownership protection. BedWarsX is (c) ASHRAF ELHENAWY (ItzRealAshraf) - all rights reserved.
 *
 * At start-up, and again every few minutes, the plugin checks that plugin.yml still names BOTH authors and the original
 * plugin name. The names are compared as SHA-256 hashes, so editing the text in the jar does not help. If the credits were
 * changed or removed, the plugin writes a message to the console and switches itself off.
 */
public final class Protect {
    private Protect() {}

    public static final String AUTHOR = "ASHRAF ELHENAWY";
    public static final String NICK = "ItzRealAshraf";
    public static final String COPYRIGHT = "Copyright (c) 2026 " + AUTHOR + " (" + NICK + "). All rights reserved.";

    // sha256("ashraf elhenawy"), sha256("itzrealashraf"), sha256("bedwarsx")  (lower case)
    private static final String H_AUTHOR = "3560760e171d52f0fefffe7ca74f265c54da2583b6c18cdf606f231eb3733a6f";
    private static final String H_NICK = "4b05605473a73d0530f30e3ed7251d12f7b2c3c4894d1bc0798d7027934e0d20";
    private static final String H_NAME = "2c2626c71b795591f00c9c7e25c471a256433d8ad1371a378d30a8b9b298be9f";

    private static String sha(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** Are the original credits still in plugin.yml? */
    public static boolean valid(JavaPlugin pl) {
        try {
            boolean author = false, nick = false;
            for (String a : pl.getDescription().getAuthors()) {
                String h = sha(a.trim().toLowerCase());
                if (h.equals(H_AUTHOR)) author = true;
                if (h.equals(H_NICK)) nick = true;
            }
            return author && nick && sha(pl.getName().trim().toLowerCase()).equals(H_NAME);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Call first thing in onEnable. When the credits were tampered with, the plugin disables itself and false is returned. */
    public static boolean check(JavaPlugin pl) {
        if (valid(pl)) return true;
        String bar = "============================================================";
        pl.getLogger().severe(bar);
        pl.getLogger().severe("This copy of BedWarsX was modified: the author credits were changed or removed.");
        pl.getLogger().severe("BedWarsX belongs to " + AUTHOR + " (" + NICK + "). " + COPYRIGHT);
        pl.getLogger().severe("Please use the original plugin. BedWarsX is now disabled.");
        pl.getLogger().severe(bar);
        Bukkit.getPluginManager().disablePlugin(pl);
        return false;
    }

    /** Re-checks the credits every 5 minutes while the server runs. */
    public static void watch(final JavaPlugin pl) {
        Bukkit.getScheduler().runTaskTimer(pl, new Runnable() {
            @Override public void run() { check(pl); }
        }, 20L * 60, 20L * 60 * 5);
    }
}
