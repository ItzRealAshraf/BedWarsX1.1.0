/*
 * BedWarsX - Copyright (c) 2026 ASHRAF ELHENAWY (ItzRealAshraf). All rights reserved.
 * Copying, re-selling, re-branding or removing the credits is not allowed.
 */
package dev.bedwarsx;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Loads the extra yml files (tab.yml, scoreboard.yml, generators.yml, gui.yml). Missing keys fall back to the jar's copy. */
public final class Cfg {
    private Cfg() {}

    public static YamlConfiguration load(BedWarsX pl, String name) {
        File f = new File(pl.getDataFolder(), name);
        if (!f.exists()) pl.saveResource(name, false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        try {
            InputStream in = pl.getResource(name);
            if (in != null) y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (Exception ignored) { /* no defaults */ }
        return y;
    }

    /** A list of lines; a single string with \n is split too. */
    public static List<String> lines(YamlConfiguration y, String path) {
        if (y.isList(path)) return new ArrayList<>(y.getStringList(path));
        String s = y.getString(path);
        if (s == null) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(s.split("\\\\n|\n", -1)));
    }
}
