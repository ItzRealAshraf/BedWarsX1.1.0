/*
 * BedWarsX - Copyright (c) 2026 ASHRAF ELHENAWY (ItzRealAshraf). All rights reserved.
 * Copying, re-selling, re-branding or removing the credits is not allowed.
 */
package dev.bedwarsx;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Loads messages_<lang>.yml (language chosen in config.yml). Missing keys fall back to English, then to the key itself. */
public final class Msg {
    private static YamlConfiguration cur, en, curDef;

    private Msg() {}

    public static void load(BedWarsX pl) {
        for (String l : new String[]{"en", "ar"}) {
            File f = new File(pl.getDataFolder(), "messages_" + l + ".yml");
            if (!f.exists()) pl.saveResource("messages_" + l + ".yml", false);
        }
        String lang = pl.getConfig().getString("language", "en");
        File f = new File(pl.getDataFolder(), "messages_" + lang + ".yml");
        if (!f.exists()) f = new File(pl.getDataFolder(), "messages_en.yml");
        cur = YamlConfiguration.loadConfiguration(f);
        try {
            java.io.InputStream in = pl.getResource("messages_" + lang + ".yml");
            curDef = in == null ? null : YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) { curDef = null; }
        try {
            en = YamlConfiguration.loadConfiguration(new InputStreamReader(pl.getResource("messages_en.yml"), StandardCharsets.UTF_8));
        } catch (Exception e) { en = new YamlConfiguration(); }
    }

    /** m("key", "%player%", name, "%n%", "3") */
    public static String m(String key, String... kv) {
        String s = cur == null ? null : cur.getString(key);
        if (s == null && curDef != null) s = curDef.getString(key); // key added by a newer jar, the old file on the server lacks it
        if (s == null && en != null) s = en.getString(key);
        if (s == null) s = key;
        for (int i = 0; i + 1 < kv.length; i += 2) s = s.replace(kv[i], kv[i + 1]);
        return U.c(s);
    }
}
