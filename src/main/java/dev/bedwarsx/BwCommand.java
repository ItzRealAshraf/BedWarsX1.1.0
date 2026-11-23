/*
 * BedWarsX - Copyright (c) 2026 ASHRAF ELHENAWY (ItzRealAshraf). All rights reserved.
 * Copying, re-selling, re-branding or removing the credits is not allowed.
 */
package dev.bedwarsx;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BwCommand implements CommandExecutor, TabCompleter {
    private final BedWarsX pl;
    private final ArenaManager mgr;

    private static final List<String> PLAYER_CMDS = Arrays.asList("join", "quickjoin", "party", "leave", "list", "stats", "top", "rejoin", "help", "shout", "gui", "about", "cosmetics", "cos", "coins", "achievements", "ach");
    /** Top-level admin commands. Everything that configures a map lives inside /bw edit <arena> <command>. */
    private static final List<String> ADMIN_CMDS = Arrays.asList("create", "delete", "edit", "setup", "sethub", "forcestart", "forcestop",
            "leaderboard", "reload", "tp", "build");

    /** Commands that used to be top-level; they now only work as /bw edit <arena> <command>. */
    private static final List<String> MOVED_CMDS = Arrays.asList("setwaitingplayer", "setwai", "setlobby", "setspec", "addteam", "delteam",
            "setspawn", "setbed", "addgen", "addshop", "clearshops", "cleargens", "setmin", "setvoid", "enable", "disable", "info",
            "setwaitingspawn", "waitingpos", "autocreateteams", "createteam", "removeteam", "setshop", "setupgrade", "addgenerator",
            "removegenerator", "settype", "save");

    /** Sub-commands of /bw edit <arena> (the first group is shown in the setup list, the rest are optional / legacy aliases). */
    private static final List<String> EDIT_CMDS = Arrays.asList("setwaitingspawn", "waitingpos", "autocreateteams", "createteam", "removeteam",
            "setspawn", "setbed", "setshop", "setupgrade", "addgenerator", "removegenerator", "settype", "save",
            "setspec", "setmin", "setvoid", "clearshops", "cleargens", "info", "markers", "enable", "disable",
            "setwaitingplayer", "setwai", "setlobby", "addteam", "delteam", "addgen", "addshop");

    private static final List<String> SETUP_STEPS = Arrays.asList("setlobby", "setwaitingplayer", "setwai", "setspec", "addteam", "delteam",
            "setspawn", "setbed", "addgen", "addshop", "clearshops", "cleargens",
            "setwaitingspawn", "waitingpos", "autocreateteams", "createteam", "removeteam", "setshop", "setupgrade", "addgenerator", "removegenerator", "settype", "setmin", "setvoid");

    public BwCommand(BedWarsX pl) { this.pl = pl; this.mgr = pl.mgr; }

    private void msg(CommandSender s, String m) { s.sendMessage(U.c(m)); }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (l.equalsIgnoreCase("shout")) { // /shout <message> works like /bw shout <message>
            String[] n = new String[a.length + 1];
            n[0] = "shout";
            System.arraycopy(a, 0, n, 1, a.length);
            a = n;
        }
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) { help(s, a.length > 1 ? a[1].toLowerCase() : ""); return true; }
        String sub = a[0].toLowerCase();
        if (PLAYER_CMDS.contains(sub)) return player(s, sub, a);
        if (MOVED_CMDS.contains(sub)) {
            if (!s.hasPermission("bwx.admin")) { msg(s, "&cYou do not have permission to use this command."); return true; }
            msg(s, "&cMap setup commands now live inside &f/bw edit&c.");
            msg(s, "&7Use: &f/bw edit <arena> " + sub + " ...");
            return true;
        }
        if (ADMIN_CMDS.contains(sub)) {
            if (!s.hasPermission("bwx.admin")) { msg(s, "&cYou do not have permission to use this command."); return true; }
            if (sub.equals("reload")) {
                try {
                    pl.reloadConfig();
                    Msg.load(pl);
                    pl.loadFiles();
                    pl.ui.reload();
                    msg(s, "&8&m----------------------------------------");
                    msg(s, "&b&lBedWarsX &8» &aConfiguration reloaded successfully.");
                    msg(s, "&7Config, messages, TAB, hub scoreboard and leaderboards refreshed.");
                    msg(s, "&eRunning matches were not restarted.");
                    msg(s, "&8&m----------------------------------------");
                } catch (Exception ex) {
                    pl.getLogger().severe("BedWarsX reload failed: " + ex.getMessage());
                    msg(s, "&cReload failed. Check the server console.");
                }
                return true;
            }
            return admin(s, sub, a);
        }
        help(s, "");
        return true;
    }

    private void helpLine(CommandSender s, String cmd, String desc) {
        msg(s, " &b" + cmd + " &8» &7" + desc);
    }

    private void helpHeader(CommandSender s, String title) {
        msg(s, "&8&m----------------------------------------------");
        msg(s, "&b&lBEDWARSX &8» &f" + title + " &8| &7v" + pl.getDescription().getVersion());
        msg(s, "&8&m----------------------------------------------");
    }

    private void helpPlayer(CommandSender s) {
        msg(s, "&a&lPLAYER COMMANDS");
        helpLine(s, "/bw join <map>", "Join a specific map");
        helpLine(s, "/bw quickjoin [mode]", "Join the best available map");
        helpLine(s, "/bw leave", "Leave your current match");
        helpLine(s, "/shout <message>", "Talk to everyone in the match (also: ! before a chat message)");
        helpLine(s, "/bw rejoin", "Reconnect to your previous match");
        helpLine(s, "/bw gui [solo|doubles|3v3v3v3|4v4v4v4|1v1|4v4]", "Open the map menu of a mode (no mode = mode selector)");
        helpLine(s, "/bw list", "Show available maps");
        helpLine(s, "/bw stats [player]", "View player statistics");
        helpLine(s, "/bw top [wins|kills|finals|beds]", "View the leaderboard");
        helpLine(s, "/bw achievements", "Open the achievements menu (XP + Coins rewards)");
        helpLine(s, "/bw cosmetics", "Open the cosmetics chest");
    }

    private void helpParty(CommandSender s) {
        msg(s, "&d&lPARTY COMMANDS");
        helpLine(s, "/bw party create", "Create a party (you are the leader)");
        helpLine(s, "/bw party invite <player>", "Invite a player (creates a party)");
        helpLine(s, "/bw party accept", "Accept a party invite");
        helpLine(s, "/bw party list", "Show party members");
        helpLine(s, "/bw party kick <player>", "Kick a member (leader)");
        helpLine(s, "/bw party leave", "Leave your party");
        helpLine(s, "/bw party disband", "Disband the party (leader)");
        msg(s, " &7Tip: use the &eParty Menu &7item in the BedWars lobby.");
    }

    private void helpSetup(CommandSender s) {
        msg(s, "&e&lMAP SETUP &7(bwx.admin) &8- &7everything is inside &f/bw edit <arena> ...");
        helpLine(s, "/bw create <name> [type] [world]", "Create a map and open the setup assistant");
        helpLine(s, "/bw edit <arena>", "Teleport to the map, show the setup holograms + assistant");
        helpLine(s, "/bw edit <arena> setWaitingSpawn", "Waiting spawn inside the map");
        helpLine(s, "/bw edit <arena> waitingPos <1|2>", "Waiting-lobby corners (removed when the game starts)");
        helpLine(s, "/bw edit <arena> autoCreateTeams", "Detect the beds in the map and create the teams");
        helpLine(s, "/bw edit <arena> createTeam <name> <color>", "Create a team");
        helpLine(s, "/bw edit <arena> removeTeam <name>", "Remove a team");
        helpLine(s, "/bw edit <arena> setSpawn <team>", "Team spawn (the team bed is detected automatically)");
        helpLine(s, "/bw edit <arena> setBed <team>", "Team bed (look at it) - only needed if it was not detected");
        helpLine(s, "/bw edit <arena> setShop <team>", "Item shop NPC of the team");
        helpLine(s, "/bw edit <arena> setUpgrade <team>", "Upgrade shop NPC of the team");
        helpLine(s, "/bw edit <arena> addGenerator <iron|gold|diamond|emerald> [team]", "iron/gold = team generator, diamond/emerald = map generator");
        helpLine(s, "/bw edit <arena> removeGenerator", "Remove the generator you are standing next to");
        helpLine(s, "/bw edit <arena> setType <type>", "solo, doubles, 3v3v3v3, 4v4v4v4, 1v1, 4v4");
        helpLine(s, "/bw edit <arena> save", "Validate, save and enable the map");
        helpLine(s, "/bw edit <arena> setSpec | setMin <n> | setVoid [y]", "Optional: spectator spawn, min players, void level");
        helpLine(s, "/bw edit <arena> clearShops | clearGens", "Remove old global shops / generators");
        helpLine(s, "/bw edit <arena> markers", "Show the setup holograms again");
        helpLine(s, "/bw edit <arena> info | disable", "Check the map / disable it");
    }

    private void helpAdmin(CommandSender s) {
        msg(s, "&c&lADMIN COMMANDS &7(bwx.admin)");
        helpLine(s, "/bw sethub", "Set the BedWars lobby spawn (hotbar items + spectator lobby button)");
        helpLine(s, "/bw delete <map>", "Delete a map config (world folder is kept)");
        helpLine(s, "/bw tp <map|lobby>", "Watch a map (your items are stored, you only get a bed that returns you to the lobby)");
        helpLine(s, "/bw build [on|off]", "Build mode: lets you build / break in the protected BedWars lobby");
        helpLine(s, "/bw forcestart", "Force-start the match you are in");
        helpLine(s, "/bw forcestop [map]", "Stop a running match");
        helpLine(s, "/bw leaderboard create <wins|kills|finals|beds>", "Place a hologram leaderboard");
        helpLine(s, "/bw leaderboard remove", "Remove the nearest leaderboard");
        helpLine(s, "/bw reload", "Reload config, messages and UI (alias: /bwx reload)");
    }

    private void help(CommandSender s, String topic) {
        boolean admin = s.hasPermission("bwx.admin");
        switch (topic) {
            case "player": helpHeader(s, "Player"); helpPlayer(s); break;
            case "party": helpHeader(s, "Party"); helpParty(s); break;
            case "setup":
                if (!admin) { msg(s, "&cYou do not have permission to use this command."); return; }
                helpHeader(s, "Map Setup"); helpSetup(s); break;
            case "admin":
                if (!admin) { msg(s, "&cYou do not have permission to use this command."); return; }
                helpHeader(s, "Admin"); helpAdmin(s); break;
            default:
                helpHeader(s, "Commands");
                helpPlayer(s);
                msg(s, "");
                helpParty(s);
                if (admin) {
                    msg(s, "");
                    msg(s, "&c&lADMIN &8» &7/bw help setup &8| &7/bw help admin");
                }
                break;
        }
        msg(s, "&8&m----------------------------------------------");
        msg(s, "&7Pages: &f/bw help player &8| &f/bw help party" + (admin ? " &8| &f/bw help setup &8| &f/bw help admin" : ""));
    }

    private boolean player(CommandSender s, String sub, String[] a) {
        switch (sub) {
            case "list":
                msg(s, "&6Arenas:");
                for (Arena ar : mgr.list())
                    msg(s, " &e" + ar.name + " &7[" + (ar.enabled ? ar.state.name() : "DISABLED") + "] "
                            + ar.players.size() + "/" + ar.maxPlayers() + " &8(" + ar.mode() + ")");
                return true;
            case "top": {
                String k = a.length > 1 ? a[1].toLowerCase() : "wins";
                if (!Arrays.asList("wins", "kills", "finals", "beds", "deaths", "games", "losses", "xp").contains(k)) {
                    msg(s, "&cUsage: /bw top <wins|kills|finals|beds|deaths|games|losses|xp>");
                    return true;
                }
                msg(s, "&6Top " + k + ":");
                int i = 1;
                for (Map.Entry<String, Integer> e : pl.stats.top(k, 10)) msg(s, " &e" + (i++) + ". &f" + e.getKey() + " &7- &a" + e.getValue());
                return true;
            }
            case "cosmetics": case "cos": {
                if (a.length >= 2 && a[1].equalsIgnoreCase("give")) {
                    if (!s.hasPermission("bwx.admin")) { msg(s, "&cYou do not have permission to use this command."); return true; }
                    if (a.length < 4) { msg(s, "&cUsage: /bw cosmetics give <player> <category.id|all>"); return true; }
                    @SuppressWarnings("deprecation")
                    org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(a[2]);
                    int n = pl.cos.give(op.getUniqueId(), a[3]);
                    msg(s, n < 0 ? "&cUnknown cosmetic. Use <category.id> (example: final.firework-show) or all." : "&aUnlocked &e" + n + "&a cosmetic(s) for &e" + a[2] + "&a.");
                    return true;
                }
                if (!(s instanceof Player)) { msg(s, "Players only."); return true; }
                CosmeticsMenu.openMain((Player) s, pl);
                return true;
            }
            case "achievements": case "ach": {
                if (!(s instanceof Player)) { msg(s, "Players only."); return true; }
                AchievementsMenu.open((Player) s, pl, 0);
                return true;
            }
            case "coins": {
                if (a.length >= 4 && Arrays.asList("give", "take", "set").contains(a[1].toLowerCase())) {
                    if (!s.hasPermission("bwx.admin")) { msg(s, "&cYou do not have permission to use this command."); return true; }
                    int n;
                    try { n = Integer.parseInt(a[3]); } catch (NumberFormatException ex) { msg(s, "&cUsage: /bw coins <give|take|set> <player> <amount>"); return true; }
                    @SuppressWarnings("deprecation")
                    org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(a[2]);
                    UUID tu = op.getUniqueId();
                    String how = a[1].toLowerCase();
                    pl.stats.setCoins(tu, how.equals("set") ? n : how.equals("give") ? pl.stats.coins(tu) + n : pl.stats.coins(tu) - n);
                    pl.stats.save();
                    msg(s, "&e" + a[2] + " &7now has &6" + pl.stats.coins(tu) + " &7coins.");
                    return true;
                }
                UUID cu;
                String cn;
                if (a.length > 1) {
                    @SuppressWarnings("deprecation")
                    org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(a[1]);
                    cu = op.getUniqueId(); cn = a[1];
                } else if (s instanceof Player) { cu = ((Player) s).getUniqueId(); cn = s.getName(); }
                else { msg(s, "&cUsage: /bw coins <player>"); return true; }
                msg(s, "&e" + cn + " &7has &6" + pl.stats.coins(cu) + " &7coins.");
                return true;
            }
            case "stats": {
                UUID u;
                String n;
                if (a.length > 1) {
                    @SuppressWarnings("deprecation")
                    org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(a[1]);
                    u = op.getUniqueId(); n = a[1];
                } else if (s instanceof Player) { u = ((Player) s).getUniqueId(); n = s.getName(); }
                else return true;
                if (s instanceof Player) { // players get the GUI
                    @SuppressWarnings("deprecation")
                    org.bukkit.OfflinePlayer target = a.length > 1 ? Bukkit.getOfflinePlayer(a[1]) : (Player) s;
                    StatsMenu.open((Player) s, pl, target);
                    return true;
                }
                msg(s, "&6Stats of &e" + n);
                msg(s, " &7level: &e" + pl.stats.level(u));
                for (String k : new String[]{"games", "wins", "losses", "kills", "finals", "deaths", "beds", "xp", "streak", "beststreak", "bestscore"})
                    msg(s, " &7" + k + ": &a" + pl.stats.get(u, k));
                return true;
            }
            case "about": {
                msg(s, "&8&m----------------------------------------------");
                msg(s, "&b&lBedWarsX &fv" + pl.getDescription().getVersion());
                msg(s, " &7Made by &6&l" + Protect.AUTHOR + " &7(&e" + Protect.NICK + "&7)");
                msg(s, " &8" + Protect.COPYRIGHT);
                msg(s, "&8&m----------------------------------------------");
                return true;
            }
            default: break;
        }
        if (!(s instanceof Player)) { msg(s, "Players only."); return true; }
        Player p = (Player) s;
        switch (sub) {
            case "gui": {
                if (mgr.of(p) != null) { p.sendMessage(Msg.m("gui-in-game")); return true; }
                if (a.length < 2) { HubMenus.openMaps(p, pl); return true; }
                String mode = Arena.canonType(a[1]);
                if (mode == null) { p.sendMessage(Msg.m("gui-usage")); return true; }
                HubMenus.openArenaList(p, pl, mode);
                return true;
            }
            case "join": {
                if (mgr.of(p) != null) { p.sendMessage(Msg.m("already-in-game")); return true; }
                if (a.length < 2) { msg(p, "&cUsage: /bw join <arena>"); return true; }
                Arena ar = mgr.get(a[1]);
                if (ar == null) { p.sendMessage(Msg.m("unknown-arena")); return true; }
                pl.parties.tryJoin(p, ar);
                return true;
            }
            case "quickjoin": {
                if (mgr.of(p) != null) { p.sendMessage(Msg.m("already-in-game")); return true; }
                Party qp = pl.parties.of(p);
                int need = (qp != null && qp.leader.equals(p.getUniqueId())) ? qp.size() : 1;
                Arena ar = mgr.quick(a.length > 1 ? a[1] : null, need);
                if (ar == null) p.sendMessage(Msg.m("no-arena"));
                else pl.parties.tryJoin(p, ar);
                return true;
            }
            case "leave": {
                Arena ar = mgr.of(p);
                if (ar == null) p.sendMessage(Msg.m("not-in-game"));
                else ar.leave(p);
                return true;
            }
            case "shout": {
                Arena ar = mgr.of(p);
                if (ar == null) { p.sendMessage(Msg.m("not-in-game")); return true; }
                Arena.PD d = ar.players.get(p.getUniqueId());
                if (a.length < 2) { msg(p, "&cUsage: /shout <message>"); return true; }
                if (ar.state != Arena.State.PLAYING || d == null || d.team == null || !d.alive) {
                    msg(p, "&cYou can only shout during a match while you are alive.");
                    return true;
                }
                String text = String.join(" ", Arrays.copyOfRange(a, 1, a.length)).replace("&", "").replace("\u00A7", "");
                String lp = pl.getConfig().getBoolean("levels.chat-prefix", true)
                        ? Msg.m("level-prefix", "%level%", "" + pl.stats.level(p.getUniqueId())) : "";
                ar.broadcast(lp + Msg.m("chat-shout") + d.team.chat + "[" + d.team.display + "] " + p.getName() + "\u00A7f: " + text);
                return true;
            }
            case "party":
                party(p, a);
                return true;
            case "rejoin": {
                Arena ar = mgr.rejoin.get(p.getUniqueId());
                if (ar == null || !ar.rejoin(p)) p.sendMessage(Msg.m("nothing-rejoin"));
                return true;
            }
            default: return true;
        }
    }

    private void party(Player p, String[] a) {
        PartyManager pm = pl.parties;
        if (a.length < 2) { msg(p, "&6/bw party &e<create|invite <player>|accept|leave|kick <player>|disband|list>"); return; }
        switch (a[1].toLowerCase()) {
            case "create": pm.create(p); break;
            case "invite": {
                Player t = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : null;
                if (t == null) { p.sendMessage(Msg.m("party-player-offline")); return; }
                pm.invite(p, t);
                break;
            }
            case "accept": pm.accept(p); break;
            case "leave":
                if (pm.of(p) == null) p.sendMessage(Msg.m("party-none"));
                else pm.leave(p.getUniqueId());
                break;
            case "kick":
                if (a.length > 2) pm.kick(p, a[2]);
                break;
            case "disband": pm.disband(p); break;
            case "list": pm.list(p); break;
            default: msg(p, "&6/bw party &e<create|invite|accept|leave|kick|disband|list>"); break;
        }
    }

    private static net.md_5.bungee.api.chat.BaseComponent[] legacy(String text) {
        return net.md_5.bungee.api.chat.TextComponent.fromLegacyText(U.c(text));
    }

    private static net.md_5.bungee.api.chat.HoverEvent hover(String text) {
        return new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                new net.md_5.bungee.api.chat.ComponentBuilder(text).create());
    }

    /** One BedWars1058-style line: "- /bw command (STATUS)"; the command is struck through once it is done. */
    private void guideLine(Player p, String shown, boolean done, boolean runNow, String click, String hover, List<net.md_5.bungee.api.chat.BaseComponent> extras) {
        net.md_5.bungee.api.chat.TextComponent line = new net.md_5.bungee.api.chat.TextComponent(" ");
        net.md_5.bungee.api.chat.TextComponent dash = new net.md_5.bungee.api.chat.TextComponent("- ");
        dash.setColor(net.md_5.bungee.api.ChatColor.BLUE);
        net.md_5.bungee.api.chat.TextComponent cmd = new net.md_5.bungee.api.chat.TextComponent(shown);
        cmd.setColor(net.md_5.bungee.api.ChatColor.WHITE);
        cmd.setStrikethrough(done);
        net.md_5.bungee.api.chat.ClickEvent.Action act = runNow
                ? net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND
                : net.md_5.bungee.api.chat.ClickEvent.Action.SUGGEST_COMMAND;
        net.md_5.bungee.api.chat.HoverEvent he = hover(hover);
        dash.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(act, click));
        cmd.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(act, click));
        dash.setHoverEvent(he);
        cmd.setHoverEvent(he);
        line.addExtra(dash);
        line.addExtra(cmd);
        if (extras != null && !extras.isEmpty()) {
            line.addExtra(new net.md_5.bungee.api.chat.TextComponent(" "));
            for (net.md_5.bungee.api.chat.BaseComponent bc : extras) line.addExtra(bc);
        }
        p.spigot().sendMessage(line);
    }

    private List<net.md_5.bungee.api.chat.BaseComponent> status(String text) {
        List<net.md_5.bungee.api.chat.BaseComponent> l = new ArrayList<>();
        if (text != null && !text.isEmpty()) l.addAll(Arrays.asList(legacy(text)));
        return l;
    }

    /** Small clickable square for a map generator (diamond / emerald): runs "/bw edit <arena> addgenerator <type>". */
    private net.md_5.bungee.api.chat.BaseComponent genSquare(String type, net.md_5.bungee.api.ChatColor color, String editPrefix) {
        net.md_5.bungee.api.chat.TextComponent sq = new net.md_5.bungee.api.chat.TextComponent("\u258C" + type.substring(0, 1).toUpperCase() + type.substring(1));
        sq.setColor(color);
        sq.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, editPrefix + "addgenerator " + type));
        sq.setHoverEvent(hover("\u00A77Click: add a \u00A7f" + type + " \u00A77generator where you stand"));
        return sq;
    }

    private String setStatus(boolean set) { return set ? "&a(SET)" : "&c(NOT SET)"; }

    private interface TeamTest { boolean missing(Arena.BT t); }

    /**
     * "(Remaining: [coloured squares])" - one clickable square per team that still misses this step
     * (click = run the command for that team, like BedWars1058). Returns null when nothing is missing.
     */
    private List<net.md_5.bungee.api.chat.BaseComponent> remaining(Arena ar, TeamTest test, String cmdFor, String extraArg) {
        List<net.md_5.bungee.api.chat.BaseComponent> out = new ArrayList<>();
        List<net.md_5.bungee.api.chat.BaseComponent> squares = new ArrayList<>();
        for (Arena.BT t : ar.teams.values()) {
            if (!test.missing(t)) continue;
            net.md_5.bungee.api.chat.TextComponent sq = new net.md_5.bungee.api.chat.TextComponent("\u258C");
            sq.setColor(t.color.chat.asBungee());
            sq.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                    ep(ar) + cmdFor + " " + (extraArg == null ? "" : extraArg + " ") + t.label()));
            sq.setHoverEvent(hover("\u00A7" + t.color.chat.getChar() + t.label() + " \u00A77- click to set"));
            squares.add(sq);
        }
        if (squares.isEmpty()) return null;
        out.addAll(status("&c(Remaining: "));
        out.addAll(squares);
        out.addAll(status("&c)"));
        return out;
    }

    /** Click prefix of every setup command: "/bw edit <arena> ". */
    private static String ep(Arena ar) { return "/bw edit " + ar.name + " "; }

    private void teamStep(Player p, Arena ar, String shown, String cmd, String hover, TeamTest missing) {
        if (ar.teams.isEmpty()) { guideLine(p, shown, false, false, ep(ar) + cmd + " ", hover, status("&c(NO TEAMS)")); return; }
        List<net.md_5.bungee.api.chat.BaseComponent> rem = remaining(ar, missing, cmd, null);
        guideLine(p, shown, rem == null, false, ep(ar) + cmd + " ", hover, rem == null ? status("&a(SET)") : rem);
    }

    /** Setup assistant (status list, struck-through when done, click to use). Every command is /bw edit <arena> <command>. */
    private void sendSetupGuide(Player p, Arena ar) {
        String e = ep(ar);
        String sh = "/bw edit " + ar.name + " ";
        msg(p, "");
        msg(p, "&6\u25B6BedWarsX v" + pl.getDescription().getVersion() + " &a- " + ar.name + " &7(everything is &f/bw edit " + ar.name + " ...&7)");
        guideLine(p, sh + "setWaitingSpawn", ar.lobby != null, true, e + "setwaitingspawn", "Waiting spawn inside the map", status(setStatus(ar.lobby != null)));
        boolean wp = ar.waitPos1 != null && ar.waitPos2 != null;
        guideLine(p, sh + "waitingPos 1/2", wp, false, e + "waitingpos ", "Stand on each corner of the waiting lobby (1, then 2). It is removed when the game starts.", status(setStatus(wp)));
        guideLine(p, sh + "autoCreateTeams", false, true, e + "autocreateteams", "Detect the beds in the loaded map and create the teams automatically", status("&e(auto detect)"));
        guideLine(p, sh + "createTeam <name> <color>", false, false, e + "createteam ", "Create a team: createTeam <name> <red|blue|green|yellow|aqua|white|pink|gray>", status("&e(" + ar.teams.size() + " CREATED)"));
        guideLine(p, sh + "removeTeam <name>", false, false, e + "removeteam ", "Remove a team", null);
        teamStep(p, ar, sh + "setSpawn <team>", "setspawn", "Stand on the team spawn - its bed is detected automatically", t -> t.spawn == null);
        teamStep(p, ar, sh + "setBed <team>", "setbed", "Only needed when the bed was not detected: look at the team's bed, then run it", t -> t.bed == null);
        teamStep(p, ar, sh + "setShop <team>", "setshop", "Stand where the team's item shop NPC should be", t -> t.shop == null);
        teamStep(p, ar, sh + "setUpgrade <team>", "setupgrade", "Stand where the team's upgrades NPC should be", t -> t.upgrade == null);
        int gens = ar.genCfg.size();
        for (Arena.BT t : ar.teams.values()) if (t.gen != null) gens++;
        List<net.md_5.bungee.api.chat.BaseComponent> gl = new ArrayList<>();
        // (Teams: squares)  -> iron + gold generator of that team
        List<net.md_5.bungee.api.chat.BaseComponent> rg = remaining(ar, t -> t.gen == null, "addgenerator", "iron");
        if (rg != null) { gl.addAll(rg); gl.add(new net.md_5.bungee.api.chat.TextComponent(" ")); }
        // (Map: diamond, emerald)  -> clickable squares, they run addGenerator diamond / emerald
        gl.addAll(status("&7(Map: "));
        gl.add(genSquare("diamond", net.md_5.bungee.api.ChatColor.AQUA, e));
        gl.add(new net.md_5.bungee.api.chat.TextComponent(" "));
        gl.add(genSquare("emerald", net.md_5.bungee.api.ChatColor.GREEN, e));
        gl.addAll(status("&7) &a(" + gens + " &bDONE&a)"));
        guideLine(p, sh + "addGenerator", false, false, e + "addgenerator ", "addGenerator <iron|gold|diamond|emerald> [team] - iron/gold = team generator, diamond/emerald = map generator. Click a coloured square to place that generator where you stand.", gl);
        guideLine(p, sh + "removeGenerator", false, true, e + "removegenerator", "Stand next to a generator and run it to remove it", null);
        guideLine(p, sh + "setType <type>", ar.type != null, false, e + "settype ", "solo, doubles, 3v3v3v3, 4v4v4v4, 1v1, 4v4",
                status(ar.type == null ? "&c(NOT SET)" : "&a(" + Arena.canonType(ar.type) + ")"));
        guideLine(p, sh + "markers", false, true, e + "markers", "Show the setup holograms again (they mark every spawn, bed, shop and generator)", null);
        guideLine(p, sh + "save", ar.enabled, true, e + "save", "Validate, save and enable the map", ar.missing() == null ? status(ar.enabled ? "&a(ENABLED)" : "&a(READY)") : status("&c(NOT READY)"));
    }

    private String findWorldFolder(String name) {
        if (name == null) return null;
        World loaded = Bukkit.getWorld(name);
        if (loaded != null) return loaded.getName();
        File[] fs = Bukkit.getWorldContainer().listFiles();
        if (fs == null) return null;
        for (File f : fs)
            if (f.isDirectory() && f.getName().equalsIgnoreCase(name) && new File(f, "level.dat").isFile()) return f.getName();
        return null;
    }

    private boolean isBedWarsLobbyWorld(String worldName) {
        String spawn = pl.getConfig().getString("hub.spawn", "");
        if (spawn == null || spawn.isEmpty()) return false;
        return spawn.split(";", 2)[0].equalsIgnoreCase(worldName);
    }

    /** The arena must be disabled and the admin must stand inside its template world. */
    private boolean editReady(Player p, Arena a) {
        if (a.enabled) { msg(p, "&cThis map is enabled. Run &f/bw edit " + a.name + " disable &cfirst (or &f/bw edit " + a.name + " &cto edit it)."); return false; }
        if (!p.getWorld().getName().equals(a.template)) {
            msg(p, "&cYou must be inside the map world &e" + a.template + "&c. Run &f/bw edit " + a.name + " &cto go there.");
            return false;
        }
        return true;
    }

    private boolean admin(CommandSender s, String sub, String[] a) {
        if (!(s instanceof Player)) { msg(s, "Players only."); return true; }
        Player p = (Player) s;
        Location loc = p.getLocation();
        switch (sub) {
            case "leaderboard": {
                if (a.length < 2) { msg(p, "&cUsage: /bw leaderboard <create <wins|kills|finals|beds>|remove>"); return true; }
                if (a[1].equalsIgnoreCase("create")) {
                    if (a.length < 3) { msg(p, "&cUsage: /bw leaderboard create <wins|kills|finals|beds>"); return true; }
                    String type = a[2].toLowerCase();
                    if (!Arrays.asList("wins", "kills", "finals", "beds").contains(type)) { msg(p, "&cType must be wins, kills, finals, or beds."); return true; }
                    pl.ui.createLeaderboard(p, type);
                    msg(p, "&aLeaderboard created for &e" + type + "&a at your location.");
                } else if (a[1].equalsIgnoreCase("remove")) {
                    if (pl.ui.removeNearestLeaderboard(p.getLocation(), 5.0)) msg(p, "&aNearest BedWars leaderboard removed.");
                    else msg(p, "&cNo leaderboard found within 5 blocks.");
                } else msg(p, "&cUsage: /bw leaderboard <create|remove>");
                return true;
            }
            case "sethub": {
                if (mgr.isGameWorld(loc.getWorld())) { msg(p, "&cYou cannot set the BedWars lobby inside a running game world."); return true; }
                pl.getConfig().set("hub.spawn", loc.getWorld().getName() + ";" + U.ls(loc));
                pl.getConfig().set("hub.teleport-on-join", true);
                pl.saveConfig();
                msg(p, "&aBedWars lobby spawn set to your current location.");
                msg(p, "&7New joins are sent here, and the spectator &fBedWars Lobby &7button returns players here.");
                msg(p, "&7This is separate from the map waiting spawn (&f/bw setwaitingplayer&7) and from the main network hub.");
                return true;
            }
            case "create": {
                if (a.length < 2) { msg(p, "&cUsage: /bw create <name> [solo|doubles|3v3v3v3|4v4v4v4|1v1|4v4] [world]"); return true; }
                if (!a[1].matches("[A-Za-z0-9_\\-]{1,32}") || a[1].toLowerCase().startsWith("bwx_")) {
                    msg(p, "&cMap name may only use letters, numbers, - and _ (max 32), and cannot start with bwx_.");
                    return true;
                }
                if (mgr.get(a[1]) != null) { msg(p, "&cThat arena already exists."); return true; }
                // /bw create Dream                 -> world folder "Dream", type chosen later with /bw setType
                // /bw create Dream doubles         -> world folder "Dream", type doubles (teams are created for you)
                // /bw create Dream doubles MyWorld -> world folder "MyWorld"
                // /bw create Dream MyWorld         -> world folder "MyWorld", type chosen later
                String mode = null;
                String worldArg = null;
                if (a.length > 2) {
                    if (Arena.canonType(a[2]) != null) { mode = Arena.canonType(a[2]); if (a.length > 3) worldArg = a[3]; }
                    else worldArg = a[2];
                }
                String template = findWorldFolder(worldArg != null ? worldArg : a[1]);
                if (template == null && worldArg == null) {
                    // fallback: you are standing inside the map world itself (never the lobby, main world or a game copy)
                    World cur = p.getWorld();
                    if (!isBedWarsLobbyWorld(cur.getName()) && !mgr.isGameWorld(cur) && !cur.equals(Bukkit.getWorlds().get(0))) template = cur.getName();
                }
                if (template == null) {
                    String wanted = worldArg != null ? worldArg : a[1];
                    msg(p, "&cWorld folder '&e" + wanted + "&c' was not found in the server folder.");
                    msg(p, "&7Upload the map world folder first, or use &f/bw create " + a[1] + " [type] <worldName>&7.");
                    return true;
                }
                World templateWorld = Bukkit.getWorld(template);
                if (templateWorld == null) templateWorld = Bukkit.createWorld(new WorldCreator(template));
                if (templateWorld == null) { msg(p, "&cCould not load map world '&e" + template + "&c'. Check console."); return true; }

                pl.tpKit.restore(p); // setup needs the real items again
                Arena na = mgr.create(a[1], templateWorld.getName(), Math.max(1, Arena.sizeOf(mode)));
                if (mode != null) {
                    na.type = mode;
                    createDefaultTeams(na);
                }
                na.min = 2;
                na.save();
                mgr.editing.put(p.getUniqueId(), na.name);
                p.teleport(templateWorld.getSpawnLocation());
                msg(p, "&aMap &e" + na.name + "&a created from world &e" + templateWorld.getName() + "&a. You were teleported to it.");
                pl.markers.refresh(na);
                sendSetupGuide(p, na);
                return true;
            }
            case "edit":
            case "setup": {
                Arena ar = a.length > 1 ? mgr.get(a[1]) : null;
                if (ar == null) {
                    msg(p, "&cUsage: /bw edit <arena> [command ...]");
                    StringBuilder names = new StringBuilder();
                    for (Arena x : mgr.list()) names.append(names.length() == 0 ? "" : "&7, ").append("&e").append(x.name);
                    if (names.length() > 0) msg(p, "&7Maps: " + names);
                    return true;
                }
                if (a.length > 2) return editSub(p, ar, Arrays.copyOfRange(a, 2, a.length));
                pl.tpKit.restore(p); // setup needs the real items again
                if (ar.world != null) { msg(p, "&cThis map has players or a running game. Stop it first (/bw forcestop " + ar.name + ")."); return true; }
                String folder = findWorldFolder(ar.template);
                if (folder == null) { msg(p, "&cThe world folder '&e" + ar.template + "&c' is missing from the server folder."); return true; }
                World w = Bukkit.getWorld(folder);
                if (w == null) w = Bukkit.createWorld(new WorldCreator(folder));
                if (w == null) { msg(p, "&cCould not load template world."); return true; }
                if (ar.enabled) {
                    ar.enabled = false;
                    ar.save();
                    msg(p, "&eMap disabled so you can edit it. Run &f/bw edit " + ar.name + " save &ewhen you are done.");
                }
                mgr.editing.put(p.getUniqueId(), ar.name);
                Location dest = w.getSpawnLocation();
                if (ar.lobby != null) {
                    try { dest = U.sl(ar.lobby, w); } catch (Exception ignored) { /* keep world spawn */ }
                }
                p.teleport(dest);
                pl.markers.refresh(ar);
                msg(p, "&aEditing &e" + ar.name + "&a. You were teleported to the map; holograms mark everything already set.");
                sendSetupGuide(p, ar);
                return true;
            }
            case "delete": {
                Arena ar = a.length > 1 ? mgr.get(a[1]) : null;
                if (ar == null) { msg(p, "&cUsage: /bw delete <arena>"); return true; }
                pl.markers.remove(ar);
                mgr.delete(ar);
                msg(p, "&aDeleted (the map world folder is kept).");
                return true;
            }
            case "build": {
                if (mgr.of(p) != null) { msg(p, "&cYou cannot use build mode inside a game."); return true; }
                boolean on;
                if (a.length > 1 && a[1].equalsIgnoreCase("on")) { on = true; pl.lobby.set(p, true); }
                else if (a.length > 1 && a[1].equalsIgnoreCase("off")) { on = false; pl.lobby.set(p, false); }
                else on = pl.lobby.toggle(p);
                p.sendMessage(Msg.m(on ? "build-on" : "build-off"));
                return true;
            }
            case "tp": {
                if (a.length < 2) { msg(p, "&cUsage: /bw tp <map|lobby>"); return true; }
                if (a[1].equalsIgnoreCase("lobby") || a[1].equalsIgnoreCase("hub")) {
                    if (!pl.tpKit.returnToLobby(p)) { // nothing stored: plain teleport like before
                        p.setGameMode(org.bukkit.GameMode.SURVIVAL);
                        teleportToHub(p);
                    }
                    msg(p, "&aTeleported to the BedWars lobby.");
                    return true;
                }
                Arena ar = mgr.get(a[1]);
                if (ar == null) { msg(p, "&cMap not found."); return true; }
                if (mgr.of(p) != null) { msg(p, "&cLeave your current game first (/bw leave)."); return true; }
                if (ar.world != null) {
                    // running game: fly around as a spectator without joining it
                    p.teleport(ar.specLoc());
                    pl.tpKit.enter(p); // items stored, only the return bed (spectator mode would make the bed unusable)
                    msg(p, "&aWatching &e" + ar.name + "&a (" + ar.state.name() + "). Right click the bed (or &f/bw tp lobby&a) to leave.");
                } else {
                    String folder = findWorldFolder(ar.template);
                    World w = folder == null ? null : Bukkit.getWorld(folder);
                    if (w == null && folder != null) w = Bukkit.createWorld(new WorldCreator(folder));
                    if (w == null) { msg(p, "&cThe map world '&e" + ar.template + "&c' is missing or could not be loaded."); return true; }
                    Location dest = w.getSpawnLocation();
                    if (ar.lobby != null) { try { dest = U.sl(ar.lobby, w); } catch (Exception ignored) { /* world spawn */ } }
                    p.teleport(dest);
                    pl.tpKit.enter(p);
                    msg(p, "&aTeleported to the map &e" + ar.name + "&a (no game running, this is the map itself). Right click the bed (or &f/bw tp lobby&a) to return.");
                }
                return true;
            }
            case "forcestart": {
                Arena ar = mgr.of(p);
                if (ar == null) { msg(p, "&cJoin an arena first."); return true; }
                ar.forceStart();
                return true;
            }
            case "forcestop": {
                Arena ar = a.length > 1 ? mgr.get(a[1]) : mgr.of(p);
                if (ar == null) { msg(p, "&cArena not found."); return true; }
                ar.forceStop();
                msg(p, "&aStopped.");
                return true;
            }
            default: break;
        }
        return true;
    }

    /** Everything below /bw edit <arena> <command> ... (a[0] = the command, a[1..] = its arguments). */
    private boolean editSub(Player p, Arena ar, String[] a) {
        String sub = a[0].toLowerCase();
        Location loc = p.getLocation();
        if (!EDIT_CMDS.contains(sub)) {
            msg(p, "&cUnknown edit command &e" + a[0] + "&c. Run &f/bw edit " + ar.name + " &cto see the setup list, or &f/bw help setup&c.");
            return true;
        }
        mgr.editing.put(p.getUniqueId(), ar.name);
        if (sub.equals("save")) sub = "enable";
        if (sub.equals("enable") || sub.equals("disable") || sub.equals("info") || sub.equals("markers")) {
            if (sub.equals("info")) {
                msg(p, "&6" + ar.name + " &7template=" + ar.template + " teams=" + ar.teams.size() + " size=" + ar.teamSize
                        + " min=" + ar.min + " gens=" + ar.genCfg.size() + " shops=" + ar.shopCfg.size() + " enabled=" + ar.enabled);
                String m = ar.missing();
                msg(p, m == null ? "&aSetup complete." : "&cMissing: " + m);
                return true;
            }
            if (sub.equals("markers")) {
                if (!editReady(p, ar)) return true;
                pl.markers.refresh(ar);
                msg(p, "&a[" + ar.name + "] &6Setup holograms refreshed.");
                return true;
            }
            if (sub.equals("enable")) {
                String m = ar.missing();
                if (m != null) { msg(p, "&cMissing: " + m); return true; }
                pl.markers.remove(ar);
                ar.enabled = true;
                ar.save();
                msg(p, "&a[" + ar.name + "] &6Map saved and enabled. Players can now join it.");
                teleportToHub(p);
            } else {
                ar.forceStop();
                ar.enabled = false;
                ar.save();
                msg(p, "&eArena disabled. Run &f/bw edit " + ar.name + " &eto edit it.");
            }
            return true;
        }
        if (!editReady(p, ar)) return true;
        TeamColor tc = a.length > 1 ? TeamColor.of(a[1]) : null;
        Arena.BT bt = a.length > 1 ? ar.findTeam(a[1]) : null;
        if (bt != null && (sub.equals("setspawn") || sub.equals("setbed") || sub.equals("setshop") || sub.equals("setupgrade"))
                && !teamOrderOk(p, ar, bt, sub)) return true;
        switch (sub) {
            case "setlobby":
            case "setwaitingplayer":
            case "setwaitingspawn":
            case "setwai":
                ar.lobby = U.ls(loc);
                msg(p, "&a[" + ar.name + "] &6Waiting spawn set");
                break;
            case "waitingpos": {
                if (a.length < 2 || !(a[1].equals("1") || a[1].equals("2"))) { msg(p, "&cUsage: /bw edit " + ar.name + " waitingPos <1|2>"); return true; }
                String v = U.ls(loc.getBlock().getLocation());
                if (a[1].equals("1")) ar.waitPos1 = v; else ar.waitPos2 = v;
                msg(p, "&a[" + ar.name + "] &6Waiting lobby position " + a[1] + " set");
                break;
            }
            case "settype": {
                String ct = a.length > 1 ? Arena.canonType(a[1]) : null;
                if (ct == null) { msg(p, "&cUsage: /bw edit " + ar.name + " setType <solo|doubles|3v3v3v3|4v4v4v4|1v1|4v4>"); return true; }
                ar.type = ct;
                ar.teamSize = Arena.sizeOf(ct);
                msg(p, "&a[" + ar.name + "] &6Type set: &f" + ct);
                if (ar.teams.size() > Arena.teamsOf(ct))
                    msg(p, "&e" + ct + " allows at most " + Arena.teamsOf(ct) + " teams but the map has " + ar.teams.size() + ". Use removeTeam.");
                else if (ar.teams.size() < Arena.teamsOf(ct))
                    msg(p, "&7" + ct + " can have up to " + Arena.teamsOf(ct) + " teams - at least 2 are needed to save the map.");
                break;
            }
            case "createteam": {
                if (a.length < 2) { msg(p, "&cUsage: /bw edit " + ar.name + " createTeam <name> <color>"); return true; }
                TeamColor col = a.length > 2 ? TeamColor.of(a[2]) : TeamColor.of(a[1]);
                if (col == null) { msg(p, "&cUnknown color. Use: red, blue, green, yellow, aqua, white, pink, gray"); return true; }
                if (ar.teams.containsKey(col)) { msg(p, "&cThere is already a team with the color " + col.chat + col.display + "&c."); return true; }
                if (ar.findTeam(a[1]) != null) { msg(p, "&cA team called &e" + a[1] + "&c already exists."); return true; }
                if (ar.type != null && ar.teams.size() >= Arena.teamsOf(ar.type)) { msg(p, "&c" + Arena.canonType(ar.type) + " only has " + Arena.teamsOf(ar.type) + " teams."); return true; }
                Arena.BT nt = new Arena.BT(col);
                nt.name = a.length > 2 ? a[1] : col.display;
                ar.teams.put(col, nt);
                msg(p, "&a[" + ar.name + "] &6Team created: " + col.chat + nt.label());
                break;
            }
            case "autocreateteams": {
                int made = 0;
                for (org.bukkit.Chunk ch : p.getWorld().getLoadedChunks()) {
                    for (org.bukkit.block.BlockState st : ch.getTileEntities()) {
                        Block b = st.getBlock();
                        if (!b.getType().name().endsWith("_BED")) continue;
                        try {
                            org.bukkit.block.data.type.Bed bd = (org.bukkit.block.data.type.Bed) b.getBlockData();
                            if (bd.getPart() != org.bukkit.block.data.type.Bed.Part.HEAD) continue;
                        } catch (Throwable ignored) { /* keep */ }
                        TeamColor col = TeamColor.ofDye(b.getType().name().replace("_BED", ""));
                        if (col == null || ar.teams.containsKey(col)) continue;
                        if (ar.type != null && ar.teams.size() >= Arena.teamsOf(ar.type)) continue;
                        Arena.BT nt = new Arena.BT(col);
                        nt.name = col.display;
                        nt.bed = U.ls(b.getLocation().add(0.5, 0, 0.5));
                        ar.teams.put(col, nt);
                        made++;
                    }
                }
                if (made == 0) {
                    if (ar.teams.isEmpty() && ar.type != null) { createDefaultTeams(ar); msg(p, "&a[" + ar.name + "] &6No beds found - created the default teams for " + Arena.canonType(ar.type)); }
                    else msg(p, "&cNo new team beds found in the loaded chunks. Walk around the map so its chunks load, or use /bw createTeam.");
                } else msg(p, "&a[" + ar.name + "] &6" + made + " team(s) created from the beds (bed positions already set).");
                break;
            }
            case "removeteam":
            case "delteam": {
                Arena.BT rt = bt != null ? bt : (tc != null ? ar.teams.get(tc) : null);
                if (rt == null) { msg(p, "&cUsage: /bw edit " + ar.name + " removeTeam <name>"); return true; }
                ar.teams.remove(rt.color);
                msg(p, "&a[" + ar.name + "] &6Team removed: " + rt.color.chat + rt.label());
                break;
            }
            case "setshop":
            case "setupgrade": {
                if (bt == null) { msg(p, "&cUsage: /bw " + sub + " <teamName>"); return true; }
                if (sub.equals("setshop")) bt.shop = U.ls(loc); else bt.upgrade = U.ls(loc);
                msg(p, "&a[" + ar.name + "] &6" + (sub.equals("setshop") ? "Shop" : "Upgrade") + " npc set for: " + bt.color.chat + bt.label());
                break;
            }
            case "addgenerator": {
                String gt = a.length > 1 ? a[1].toLowerCase() : "";
                if (gt.equals("diamond") || gt.equals("emerald")) {
                    ar.genCfg.add(gt.toUpperCase() + "@" + U.ls(loc.getBlock().getLocation().add(0.5, 0, 0.5)));
                    msg(p, "&a[" + ar.name + "] &6Generator added: &f" + gt);
                } else if (gt.equals("iron") || gt.equals("gold")) {
                    Arena.BT target = a.length > 2 ? ar.findTeam(a[2]) : null;
                    if (a.length > 2 && target == null) { msg(p, "&cUnknown team: " + a[2]); return true; }
                    if (target == null && strictOrder()) target = teamInProgress(ar, null); // the team you are setting up right now
                    if (target != null && !teamOrderOk(p, ar, target, "addgenerator")) return true;
                    if (target == null) {
                        double best = Double.MAX_VALUE;
                        for (Arena.BT t : ar.teams.values()) {
                            if (t.spawn == null) continue;
                            try {
                                Location sl = U.sl(t.spawn, loc.getWorld());
                                double d = sl.distanceSquared(loc);
                                if (d < best) { best = d; target = t; }
                            } catch (Exception ignored) { /* skip */ }
                        }
                    }
                    if (target == null) { msg(p, "&cNo team found. Set a team spawn first, or add the team name: addGenerator " + gt + " <team>."); return true; }
                    target.gen = U.ls(loc.getBlock().getLocation().add(0.5, 0, 0.5));
                    msg(p, "&a[" + ar.name + "] &6Generator set for: " + target.color.chat + target.label() + " &7(iron + gold + forge emeralds)");
                } else { msg(p, "&cUsage: /bw edit " + ar.name + " addGenerator <iron|gold|diamond|emerald> [team]"); return true; }
                break;
            }
            case "removegenerator": {
                double best = 36; // 6 blocks
                int bi = -1; Arena.BT bgt = null;
                for (int i = 0; i < ar.genCfg.size(); i++) {
                    try {
                        double d = U.sl(ar.genCfg.get(i).split("@", 2)[1], loc.getWorld()).distanceSquared(loc);
                        if (d < best) { best = d; bi = i; bgt = null; }
                    } catch (Exception ignored) { /* skip */ }
                }
                for (Arena.BT t : ar.teams.values()) {
                    if (t.gen == null) continue;
                    try {
                        double d = U.sl(t.gen, loc.getWorld()).distanceSquared(loc);
                        if (d < best) { best = d; bi = -1; bgt = t; }
                    } catch (Exception ignored) { /* skip */ }
                }
                if (bgt != null) { bgt.gen = null; msg(p, "&a[" + ar.name + "] &6Generator removed from team " + bgt.color.chat + bgt.label()); }
                else if (bi >= 0) { String r = ar.genCfg.remove(bi); msg(p, "&a[" + ar.name + "] &6Generator removed: &f" + r.split("@", 2)[0].toLowerCase()); }
                else { msg(p, "&cNo generator within 6 blocks. Stand next to the one you want to remove."); return true; }
                break;
            }
            case "setspec": ar.spec = U.ls(loc); msg(p, "&a[" + ar.name + "] &6Spectator spawn set"); break;
            case "setmin": {
                try { ar.min = Math.max(2, Integer.parseInt(a[1])); msg(p, "&aMin players = " + ar.min); }
                catch (Exception e) { msg(p, "&cUsage: /bw edit " + ar.name + " setmin <n>"); }
                break;
            }
            case "setvoid": {
                try { ar.voidY = a.length > 1 ? Integer.parseInt(a[1]) : loc.getBlockY(); } catch (NumberFormatException e) { ar.voidY = loc.getBlockY(); }
                msg(p, "&aVoid level = " + ar.voidY + " (players below it die)");
                break;
            }
            case "addteam": {
                if (tc == null) { msg(p, "&cUsage: /bw edit " + ar.name + " createTeam <name> <color>"); return true; }
                if (!ar.teams.containsKey(tc)) { Arena.BT nt = new Arena.BT(tc); nt.name = tc.display; ar.teams.put(tc, nt); }
                msg(p, "&a[" + ar.name + "] &6Team created: " + tc.chat + tc.display);
                break;
            }
            case "setspawn":
                if (bt == null) { msg(p, "&cUsage: /bw edit " + ar.name + " setSpawn <teamName> (create the team first)"); return true; }
                bt.spawn = U.ls(loc);
                msg(p, "&a[" + ar.name + "] &6Spawn set for: " + bt.color.chat + bt.label());
                if (bt.bed == null) {
                    // look for this team's bed around the spawn; if there is none the admin sets it himself
                    int radius = Math.max(4, pl.getConfig().getInt("edit.bed-search-radius", 30));
                    Block bed = detectBed(bt, loc, radius);
                    if (bed != null) {
                        bt.bed = U.ls(bed.getLocation().add(0.5, 0, 0.5));
                        msg(p, "&a[" + ar.name + "] &6" + bt.color.chat + bt.label() + " &6bed detected automatically at &f"
                                + bed.getX() + ", " + bed.getY() + ", " + bed.getZ());
                    } else {
                        msg(p, "&e[" + ar.name + "] No " + bt.color.chat + bt.color.display + " &ebed found within " + radius + " blocks of this spawn.");
                        msg(p, "&7Look at the bed and run &f/bw edit " + ar.name + " setBed " + bt.label());
                    }
                }
                break;
            case "setbed": {
                if (bt == null) { msg(p, "&cUsage: /bw edit " + ar.name + " setBed <teamName> (look at the bed)"); return true; }
                Block b = p.getTargetBlockExact(6);
                if (b == null || !b.getType().name().endsWith("_BED")) { msg(p, "&cLook at a bed block."); return true; }
                TeamColor bc = TeamColor.ofDye(b.getType().name().replace("_BED", ""));
                if (bc != bt.color) {
                    msg(p, "&cThat is a " + (bc == null ? b.getType().name().replace("_BED", "").toLowerCase() : bc.chat + bc.display) + " &cbed, but you are setting up " + bt.color.chat + bt.label() + "&c.");
                    msg(p, "&7Look at the " + bt.color.chat + bt.color.display + " &7bed of this team.");
                    return true;
                }
                bt.bed = U.ls(b.getLocation().add(0.5, 0, 0.5));
                msg(p, "&a[" + ar.name + "] &6Bed set for: " + bt.color.chat + bt.label());
                break;
            }
            case "addgen": {
                if (a.length < 2 || !(a[1].equalsIgnoreCase("diamond") || a[1].equalsIgnoreCase("emerald"))) { msg(p, "&cUsage: /bw edit " + ar.name + " addgen <diamond|emerald>"); return true; }
                Location g = loc.getBlock().getLocation().add(0.5, 0, 0.5);
                ar.genCfg.add(a[1].toUpperCase() + "@" + U.ls(g));
                msg(p, "&a[" + ar.name + "] &6Generator added: &f" + a[1].toLowerCase());
                break;
            }
            case "addshop": {
                if (a.length < 2 || !(a[1].equalsIgnoreCase("item") || a[1].equalsIgnoreCase("upgrade"))) { msg(p, "&cUsage: /bw edit " + ar.name + " addshop <item|upgrade>"); return true; }
                ar.shopCfg.add(a[1].toUpperCase() + "@" + U.ls(loc));
                msg(p, "&a[" + ar.name + "] &6Shop set: &f" + a[1].toLowerCase());
                break;
            }
            case "clearshops": ar.shopCfg.clear(); msg(p, "&a[" + ar.name + "] &6Shops cleared"); break;
            case "cleargens": ar.genCfg.clear(); msg(p, "&a[" + ar.name + "] &6Generators cleared"); break;
            default: break;
        }
        ar.save();
        pl.markers.refresh(ar);
        if (SETUP_STEPS.contains(sub)) sendSetupGuide(p, ar);
        return true;
    }


    // ------------------------------------------------------------------ team setup order
    private boolean strictOrder() { return pl.getConfig().getBoolean("edit.strict-team-order", true); }

    /** A team is done when spawn, bed, item shop, upgrade shop and generator are all set. */
    private boolean teamDone(Arena ar, Arena.BT t) { return teamMissing(ar, t) == null; }

    private String teamMissing(Arena ar, Arena.BT t) {
        List<String> m = new ArrayList<>();
        if (t.spawn == null) m.add("spawn");
        if (t.bed == null) m.add("bed");
        if (ar.shopCfg.isEmpty()) {
            if (t.shop == null) m.add("shop");
            if (t.upgrade == null) m.add("upgrade");
        }
        if (t.gen == null) m.add("generator");
        return m.isEmpty() ? null : String.join(", ", m);
    }

    /** The team that was started (spawn set) but is not finished yet, ignoring `except`. */
    private Arena.BT teamInProgress(Arena ar, Arena.BT except) {
        for (Arena.BT o : ar.teams.values())
            if (o != except && o.spawn != null && !teamDone(ar, o)) return o;
        return null;
    }

    /**
     * Team setup is done one team at a time: the spawn comes first, and while a team is unfinished
     * you cannot start / edit another team. (config: edit.strict-team-order)
     */
    private boolean teamOrderOk(Player p, Arena ar, Arena.BT t, String sub) {
        if (!strictOrder()) return true;
        if (!sub.equals("setspawn") && t.spawn == null) {
            msg(p, "&cSet the spawn of " + t.color.chat + t.label() + " &cfirst: &f/bw edit " + ar.name + " setSpawn " + t.label());
            return false;
        }
        Arena.BT other = teamInProgress(ar, t);
        if (other != null) {
            msg(p, "&cFinish team " + other.color.chat + other.label() + " &cfirst &7(missing: " + teamMissing(ar, other) + ")&c.");
            msg(p, "&7You can only set up one team at a time.");
            return false;
        }
        return true;
    }

    /** Nearest bed of the team's colour around a point (HEAD half preferred); null when there is none. */
    private Block detectBed(Arena.BT t, Location from, int radius) {
        org.bukkit.Material mat = t.color.block("BED");
        World w = from.getWorld();
        int cx = from.getBlockX(), cy = from.getBlockY(), cz = from.getBlockZ();
        int vr = Math.max(8, radius / 2);
        Block bestHead = null, bestAny = null;
        double dh = Double.MAX_VALUE, da = Double.MAX_VALUE;
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                for (int y = Math.max(0, cy - vr); y <= Math.min(w.getMaxHeight() - 1, cy + vr); y++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType() != mat) continue;
                    double d = (x - cx) * (double) (x - cx) + (y - cy) * (double) (y - cy) + (z - cz) * (double) (z - cz);
                    if (d < da) { da = d; bestAny = b; }
                    try {
                        org.bukkit.block.data.type.Bed bd = (org.bukkit.block.data.type.Bed) b.getBlockData();
                        if (bd.getPart() == org.bukkit.block.data.type.Bed.Part.HEAD && d < dh) { dh = d; bestHead = b; }
                    } catch (Throwable ignored) { /* keep */ }
                }
            }
        }
        return bestHead != null ? bestHead : bestAny;
    }

    /** Creates the default teams (red, blue, green ...) for the arena's game type. */
    private void createDefaultTeams(Arena ar) {
        TeamColor[] cs = TeamColor.values();
        int n = Arena.teamsOf(ar.type);
        for (int i = 0; i < n && i < cs.length; i++) {
            if (ar.teams.containsKey(cs[i])) continue;
            Arena.BT t = new Arena.BT(cs[i]);
            t.name = cs[i].display;
            ar.teams.put(cs[i], t);
        }
    }

    private void teleportToHub(Player p) {
        pl.tpKit.restore(p); // an admin coming back from /bw tp gets his items back
        try {
            String spawn = pl.getConfig().getString("hub.spawn", "");
            if (spawn == null || spawn.isEmpty()) return;
            String[] parts = spawn.split(";", 2);
            World w = parts.length == 2 ? Bukkit.getWorld(parts[0]) : null;
            if (w != null) p.teleport(U.sl(parts[1], w));
        } catch (Exception ignored) { /* stay where you are */ }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        boolean admin = s.hasPermission("bwx.admin");
        if (a.length == 1) {
            out.addAll(PLAYER_CMDS);
            if (admin) out.addAll(ADMIN_CMDS);
        } else if (a.length == 2) {
            String sub = a[0].toLowerCase();
            if (sub.equals("help")) out.addAll(admin ? Arrays.asList("player", "party", "setup", "admin") : Arrays.asList("player", "party"));
            else if (sub.equals("gui")) out.addAll(Arrays.asList("solo", "doubles", "3v3v3v3", "4v4v4v4", "1v1", "4v4"));
            else if (!admin && !sub.equals("join")) { /* nothing */ }
            else if (sub.equals("tp")) { out.add("lobby"); for (Arena ar : mgr.list()) out.add(ar.name); }
            else if (sub.equals("build")) out.addAll(Arrays.asList("on", "off"));
            else if (sub.equals("join") || sub.equals("edit") || sub.equals("setup") || sub.equals("delete") || sub.equals("forcestop"))
                for (Arena ar : mgr.list()) out.add(ar.name);
            else if (sub.equals("top")) out.addAll(Arrays.asList("wins", "kills", "finals", "beds"));
            else if (sub.equals("party")) out.addAll(Arrays.asList("create", "invite", "accept", "leave", "kick", "disband", "list"));
            else if (sub.equals("leaderboard")) out.addAll(Arrays.asList("create", "remove"));
            else if (sub.equals("coins") && admin) out.addAll(Arrays.asList("give", "take", "set"));
            else if ((sub.equals("cosmetics") || sub.equals("cos")) && admin) out.add("give");
        } else if (admin && a.length == 3 && (a[0].equalsIgnoreCase("edit") || a[0].equalsIgnoreCase("setup"))) {
            out.addAll(EDIT_CMDS);
        } else if (admin && a.length >= 4 && (a[0].equalsIgnoreCase("edit") || a[0].equalsIgnoreCase("setup"))) {
            Arena ea = mgr.get(a[1]);
            String sub = a[2].toLowerCase();
            if (a.length == 4) {
                if (sub.equals("delteam") || sub.equals("removeteam") || sub.equals("setspawn") || sub.equals("setbed") || sub.equals("setshop") || sub.equals("setupgrade")) {
                    if (ea != null) for (Arena.BT t : ea.teams.values()) out.add(t.label());
                }
                else if (sub.equals("addteam")) for (TeamColor t : TeamColor.values()) out.add(t.name().toLowerCase());
                else if (sub.equals("settype")) out.addAll(Arrays.asList("solo", "doubles", "3v3v3v3", "4v4v4v4", "1v1", "4v4"));
                else if (sub.equals("waitingpos")) out.addAll(Arrays.asList("1", "2"));
                else if (sub.equals("addgenerator")) out.addAll(Arrays.asList("iron", "gold", "diamond", "emerald"));
                else if (sub.equals("createteam")) out.add("<name>");
                else if (sub.equals("addgen")) out.addAll(Arrays.asList("diamond", "emerald"));
                else if (sub.equals("addshop")) out.addAll(Arrays.asList("item", "upgrade"));
            } else if (a.length == 5) {
                if (sub.equals("createteam")) for (TeamColor t : TeamColor.values()) out.add(t.name().toLowerCase());
                else if (sub.equals("addgenerator") && ea != null) for (Arena.BT t : ea.teams.values()) out.add(t.label());
            }
        } else if (admin && a.length == 3 && a[0].equalsIgnoreCase("leaderboard") && a[1].equalsIgnoreCase("create")) {
            out.addAll(Arrays.asList("wins", "kills", "finals", "beds"));
        } else if (admin && a.length == 3 && a[0].equalsIgnoreCase("create")) {
            out.addAll(Arrays.asList("1v1", "4v4", "solo", "doubles", "3v3v3v3", "4v4v4v4"));
        } else if (admin && a.length == 4 && a[0].equalsIgnoreCase("create")) {
            for (World w : Bukkit.getWorlds()) out.add(w.getName());
        }
        List<String> res = new ArrayList<>();
        String cur = a[a.length - 1].toLowerCase();
        for (String o : out) if (o.toLowerCase().startsWith(cur)) res.add(o);
        return res;
    }
}
