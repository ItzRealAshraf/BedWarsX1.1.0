/*
 * BedWarsX - Copyright (c) 2026 ASHRAF ELHENAWY (ItzRealAshraf). All rights reserved.
 * Copying, re-selling, re-branding or removing the credits is not allowed.
 */
package dev.bedwarsx;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class BedWarsX extends JavaPlugin {
    public ArenaManager mgr;
    public Stats stats;
    public PartyManager parties;
    public UiManager ui;
    public EditMarkers markers;
    public TpKit tpKit;
    public LobbyProtection lobby;
    public Cosmetics cos;
    public Achievements ach;
    /** tab.yml, scoreboard.yml, generators.yml, gui.yml, shop.yml */
    public org.bukkit.configuration.file.YamlConfiguration tabCfg, boardCfg, genCfg, guiCfg, shopCfg, cosCfg, achCfg, weeklyCfg;

    public void loadFiles() {
        tabCfg = Cfg.load(this, "tab.yml");
        boardCfg = Cfg.load(this, "scoreboard.yml");
        genCfg = Cfg.load(this, "generators.yml");
        guiCfg = Cfg.load(this, "gui.yml");
        shopCfg = Cfg.load(this, "shop.yml");
        Menus.load(shopCfg);
        cosCfg = Cfg.load(this, "cosmetics.yml");
        if (cos != null) cos.load(cosCfg);
        achCfg = Cfg.load(this, "achievements.yml");
        weeklyCfg = Cfg.load(this, "weekly.yml"); // new in 1.1.0: created automatically when it does not exist yet
        if (ach != null) ach.load(achCfg);
    }

    /** BedWars lobby spawn (hub.spawn set with /bw sethub), or null when it is not set or its world is not loaded. */
    public org.bukkit.Location hubLocation() {
        try {
            String spawn = getConfig().getString("hub.spawn", "");
            if (spawn == null || spawn.isEmpty()) return null;
            String[] parts = spawn.split(";", 2);
            org.bukkit.World w = parts.length == 2 ? getServer().getWorld(parts[0]) : null;
            return w == null ? null : U.sl(parts[1], w);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void onEnable() {
        if (!Protect.check(this)) return; // credits (ASHRAF ELHENAWY / ItzRealAshraf) were removed -> the plugin switches itself off
        saveDefaultConfig();
        getConfig().addDefault("hub.spawn", "");
        getConfig().addDefault("hub.teleport-on-join", false);
        getConfig().addDefault("hub.main-server", "lobby");
        getConfig().addDefault("hub.return-command", "");
        getConfig().addDefault("lobby-protection.enabled", true);
        getConfig().addDefault("lobby-protection.radius", 0);
        getConfig().addDefault("clear-enderchest", true); // the ender chest is emptied for every match (items come back after it)
        getConfig().addDefault("stats-autosave-minutes", 5);
        getConfig().options().copyDefaults(true);
        saveConfig();
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        Msg.load(this);
        cos = new Cosmetics(this);
        ach = new Achievements(this);
        loadFiles();
        stats = new Stats(this);
        parties = new PartyManager(this);
        mgr = new ArenaManager(this);
        mgr.cleanupLeftovers();
        mgr.loadAll();
        markers = new EditMarkers(this);
        markers.purge();
        ui = new UiManager(this);
        ui.start();
        tpKit = new TpKit(this);
        lobby = new LobbyProtection(this);
        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getPluginManager().registerEvents(lobby, this);
        getServer().getPluginManager().registerEvents(new CosmeticsListener(this), this);
        cos.start();
        Protect.watch(this);
        long autosave = Math.max(1, getConfig().getInt("stats-autosave-minutes", 5)) * 60L * 20L;
        getServer().getScheduler().runTaskTimer(this, new Runnable() {
            @Override public void run() { if (stats != null) stats.save(); }
        }, autosave, autosave);
        PluginCommand pc = getCommand("bw");
        BwCommand cmd = new BwCommand(this);
        pc.setExecutor(cmd);
        pc.setTabCompleter(cmd);
        PluginCommand shout = getCommand("shout");
        if (shout != null) shout.setExecutor(cmd);
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try { new BwxExpansion(this).register(); getLogger().info("PlaceholderAPI hooked."); }
            catch (Throwable t) { getLogger().warning("PlaceholderAPI hook failed: " + t); }
        }
        banner();
        getLogger().info("BedWarsX enabled.");
    }

    // 5x5 block font used for the console banner (ASHRAF / ELHENAWY). Each '#' is drawn as two full blocks.
    private static final java.util.Map<Character, String[]> FONT = new java.util.HashMap<>();
    static {
        FONT.put('A', new String[]{" ### ", "#   #", "#####", "#   #", "#   #"});
        FONT.put('S', new String[]{" ####", "#    ", " ### ", "    #", "#### "});
        FONT.put('H', new String[]{"#   #", "#   #", "#####", "#   #", "#   #"});
        FONT.put('R', new String[]{"#### ", "#   #", "#### ", "#  # ", "#   #"});
        FONT.put('F', new String[]{"#####", "#    ", "#### ", "#    ", "#    "});
        FONT.put('E', new String[]{"#####", "#    ", "#### ", "#    ", "#####"});
        FONT.put('L', new String[]{"#    ", "#    ", "#    ", "#    ", "#####"});
        FONT.put('N', new String[]{"#   #", "##  #", "# # #", "#  ##", "#   #"});
        FONT.put('W', new String[]{"#   #", "#   #", "# # #", "## ##", "#   #"});
        FONT.put('Y', new String[]{"#   #", " # # ", "  #  ", "  #  ", "  #  "});
    }

    private static String[] renderWord(String word) {
        String[] rows = new String[5];
        for (int r = 0; r < 5; r++) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < word.length(); i++) {
                String line = FONT.get(word.charAt(i))[r];
                for (char ch : line.toCharArray()) sb.append(ch == '#' ? "\u2588\u2588" : "  ");
                sb.append(' ');
            }
            rows[r] = sb.toString();
        }
        return rows;
    }

    /** Big console banner (LuckPerms style) so everybody who runs the plugin knows who made it. */
    private void banner() {
        org.bukkit.command.ConsoleCommandSender c = getServer().getConsoleSender();
        String bar = "&8" + new String(new char[60]).replace('\0', '=');
        c.sendMessage("");
        c.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', bar));
        for (String row : renderWord("ASHRAF")) c.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&6&l" + row));
        c.sendMessage("");
        for (String row : renderWord("ELHENAWY")) c.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&e&l" + row));
        c.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', bar));
        String[] info = {
                "&b&lBedWarsX &fv" + getDescription().getVersion(),
                "&7Made by &6&lASHRAF ELHENAWY &7(&e" + Protect.NICK + "&7)",
                "&8" + Protect.COPYRIGHT,
                "&7Running on: &f" + getServer().getName() + " " + getServer().getBukkitVersion(),
                "&7Thanks for using my plugin!"
        };
        for (String line : info) c.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "  " + line));
        c.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', bar));
        c.sendMessage("");
    }

    @Override
    public void onDisable() {
        if (tpKit != null) tpKit.restoreAll();
        if (markers != null) markers.purge();
        if (ui != null) ui.stop();
        if (mgr != null) mgr.shutdown();
        if (stats != null) stats.save();
    }
}
