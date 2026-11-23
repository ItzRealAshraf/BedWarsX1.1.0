/*
 * BedWarsX - Copyright (c) 2026 ASHRAF ELHENAWY (ItzRealAshraf). All rights reserved.
 * Copying, re-selling, re-branding or removing the credits is not allowed.
 */
package dev.bedwarsx;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** GUI and hotbar items for the dedicated BedWars lobby. */
public final class HubMenus {
    private HubMenus() {}

    /** Item ids (stored inside the item, so renaming them in gui.yml never breaks the click handling). */
    public static final String T_MAP = "map-selector", T_PARTY = "party-menu", T_MAIN = "main-hub", T_STATS = "stats-menu", T_COS = "cosmetics";

    public static class Holder implements InventoryHolder {
        public final String type;
        /** Game type this menu belongs to (Solo, Doubles, 3v3v3v3, 4v4v4v4, 1v1, 4v4), or null for the selector. */
        public String mode;
        public Inventory inventory;
        /** Party menu: which page of invitable players is shown. */
        public int page;
        /** Cosmetics menu: the item waiting for a second click to confirm a purchase. */
        public String pending;
        public final List<String> arenas = new ArrayList<>();
        public final List<UUID> players = new ArrayList<>();
        public final List<UUID> roster = new ArrayList<>();
        public Holder(String type) { this.type = type; }
        @Override public Inventory getInventory() { return inventory; }
    }

    /** Hotbar item described at <path> in gui.yml (slot, material, name, lore); tagged with the last path segment. */
    public static ItemStack hot(BedWarsX pl, String path) {
        Material m = Material.matchMaterial(pl.guiCfg.getString(path + ".material", "STONE"));
        if (m == null) m = Material.STONE;
        List<String> lore = pl.guiCfg.getStringList(path + ".lore");
        ItemStack it = U.item(m, 1, pl.guiCfg.getString(path + ".name", path), lore.toArray(new String[0]));
        return U.tag(it, path.substring(path.lastIndexOf('.') + 1));
    }

    public static void setHot(BedWarsX pl, Player p, String path) {
        int slot = pl.guiCfg.getInt(path + ".slot", 0);
        if (slot < 0 || slot >= 9) return;
        ItemStack it = hot(pl, path);
        if (it.getItemMeta() instanceof SkullMeta) { // player-head items show the skin of the player who holds them
            SkullMeta sm = (SkullMeta) it.getItemMeta();
            sm.setOwningPlayer(p);
            it.setItemMeta(sm);
        }
        p.getInventory().setItem(slot, it);
    }

    public static void giveItems(Player p) {
        BedWarsX pl = org.bukkit.plugin.java.JavaPlugin.getPlugin(BedWarsX.class);
        setHot(pl, p, "hotbar.hub." + T_MAP);
        setHot(pl, p, "hotbar.hub." + T_PARTY);
        setHot(pl, p, "hotbar.hub." + T_STATS);
        setHot(pl, p, "hotbar.hub." + T_COS);
        setHot(pl, p, "hotbar.hub." + T_MAIN);
        p.updateInventory();
    }

    /** Game types shown in the Map Selector (keys in gui.yml -> map-selector.modes). */
    private static final String[] MODE_KEYS = {"solo", "doubles", "3v3v3v3", "4v4v4v4", "1v1", "4v4"};
    private static final String[] MODE_NAMES = {"Solo", "Doubles", "3v3v3v3", "4v4v4v4", "1v1", "4v4"};

    private static boolean joinable(Arena a) {
        return a.enabled && (a.state == Arena.State.WAITING || a.state == Arena.State.STARTING);
    }

    private static int playingIn(BedWarsX pl, String mode) {
        int n = 0;
        for (Arena a : pl.mgr.list()) if (a.enabled && a.mode().equalsIgnoreCase(mode)) n += a.players.size();
        return n;
    }

    private static String ph(String s, String... kv) {
        if (s == null) return "";
        for (int i = 0; i + 1 < kv.length; i += 2) s = s.replace(kv[i], kv[i + 1]);
        return s;
    }

    /** Item described at <path> in gui.yml (material, name, lore) with placeholders replaced. */
    private static ItemStack gi(BedWarsX pl, String path, String... kv) {
        Material m = Material.matchMaterial(pl.guiCfg.getString(path + ".material", "STONE"));
        if (m == null) m = Material.STONE;
        List<String> lore = pl.guiCfg.getStringList(path + ".lore");
        String[] l = new String[lore.size()];
        for (int i = 0; i < l.length; i++) l[i] = ph(lore.get(i), kv);
        return U.item(m, 1, ph(pl.guiCfg.getString(path + ".name", ""), kv), l);
    }

    /** Every lobby menu is ONE chest (27 slots). */
    private static final int SIZE = 27;
    // default slots of the compact layout (used when gui.yml has no slot, or an old double-chest slot that does not fit anymore)
    private static final int SEL_CLOSE = 26;
    private static final int MODE_PLAY = 12, MODE_MAPS = 14, MODE_BACK = 18, MODE_CLOSE = 22, MODE_RANDOM = 26;
    private static final int LIST_BACK = 18, LIST_REFRESH = 22, LIST_NONE = 13, LIST_MAX = 18;
    private static final int P_CREATE = 18, P_REFRESH = 19, P_QUICK = 20, P_ACCEPT = 22, P_INFO = 24, P_DISBAND = 26, P_NONE = 13;
    /** Party menu: members on the top row (the book is in the middle), invite heads in the middle row (9 per page). */
    private static final int[] ROSTER_SLOTS = {0, 1, 2, 3, 5, 6, 7, 8};
    private static final int HEADS_FROM = 9, HEADS_PER_PAGE = 9;

    private static int slotOf(BedWarsX pl, String path, int def) {
        int s = pl.guiCfg.getInt(path + ".slot", def);
        return s >= 0 && s < SIZE ? s : def;
    }

    private static void put(Inventory inv, int slot, ItemStack it) { if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, it); }

    /** Map Selector: the 6 game types. */
    public static void openMaps(Player p, BedWarsX pl) {
        Holder h = new Holder("HUB_MODES");
        Inventory inv = Bukkit.createInventory(h, SIZE, U.c(pl.guiCfg.getString("map-selector.title", "&8Map Selector")));
        h.inventory = inv;
        for (int i = 0; i < MODE_KEYS.length; i++) {
            String path = "map-selector.modes." + MODE_KEYS[i];
            String m = MODE_NAMES[i];
            int maps = 0;
            for (Arena a : pl.mgr.list()) if (a.enabled && a.mode().equalsIgnoreCase(m)) maps++;
            Material mat = Material.matchMaterial(pl.guiCfg.getString(path + ".material", "RED_BED"));
            List<String> lore = pl.guiCfg.getStringList("map-selector.mode-lore");
            String[] l = new String[lore.size()];
            for (int k = 0; k < l.length; k++)
                l[k] = ph(lore.get(k), "%desc%", pl.guiCfg.getString(path + ".desc", ""), "%mode%", m, "%playing%", "" + playingIn(pl, m), "%maps%", "" + maps);
            put(inv, slotOf(pl, path, 11 + i), U.item(mat == null ? Material.RED_BED : mat, 1, ph(pl.guiCfg.getString(path + ".name", "&aBed Wars " + m), "%mode%", m), l));
        }
        put(inv, slotOf(pl, "map-selector.close", SEL_CLOSE), gi(pl, "map-selector.close"));
        p.openInventory(inv);
    }

    /** The "Bed Wars <mode>" menu: bed = play, sign = map list, pearl = random map, white block = back, barrier = close. */
    public static void openMode(Player p, BedWarsX pl, String mode) {
        Holder h = new Holder("HUB_MODE");
        h.mode = mode;
        Inventory inv = Bukkit.createInventory(h, SIZE, U.c(ph(pl.guiCfg.getString("mode-menu.title", "&8Bed Wars %mode%"), "%mode%", mode)));
        h.inventory = inv;
        int free = 0;
        for (Arena a : pl.mgr.list()) if (joinable(a) && a.mode().equalsIgnoreCase(mode)) free++;
        String[] kv = {"%mode%", mode, "%playing%", "" + playingIn(pl, mode), "%open%", "" + free};
        String[] keys = {"play", "maps", "back", "close", "random"};
        int[] defs = {MODE_PLAY, MODE_MAPS, MODE_BACK, MODE_CLOSE, MODE_RANDOM};
        for (int i = 0; i < keys.length; i++)
            put(inv, slotOf(pl, "mode-menu." + keys[i], defs[i]), gi(pl, "mode-menu." + keys[i], kv));
        p.openInventory(inv);
    }

    /** Sign button: every map of this mode, click one to join it. */
    public static void openArenaList(Player p, BedWarsX pl, String mode) {
        Holder h = new Holder("HUB_MAPS");
        h.mode = mode;
        Inventory inv = Bukkit.createInventory(h, SIZE, U.c(ph(pl.guiCfg.getString("map-list.title", "&8%mode% - Select a Map"), "%mode%", mode)));
        h.inventory = inv;
        int slot = 0;
        for (Arena a : pl.mgr.list()) {
            if (!a.enabled || !a.mode().equalsIgnoreCase(mode)) continue;
            if (slot >= LIST_MAX) break;
            inv.setItem(slot, gi(pl, joinable(a) ? "map-list.open" : "map-list.closed", "%map%", a.name, "%mode%", a.mode(),
                    "%players%", "" + a.players.size(), "%max%", "" + a.maxPlayers(), "%state%", a.state.name()));
            h.arenas.add(a.name);
            slot++;
        }
        if (slot == 0) put(inv, LIST_NONE, gi(pl, "map-list.none", "%mode%", mode));
        put(inv, slotOf(pl, "map-list.back", LIST_BACK), gi(pl, "map-list.back", "%mode%", mode));
        put(inv, slotOf(pl, "map-list.refresh", LIST_REFRESH), gi(pl, "map-list.refresh", "%mode%", mode));
        p.openInventory(inv);
    }

    private static void play(Player p, BedWarsX pl, String mode, boolean random) {
        Party party = pl.parties.of(p);
        int need = party != null && party.leader.equals(p.getUniqueId()) ? party.size() : 1;
        Arena chosen;
        if (random) {
            List<Arena> ok = new ArrayList<>();
            for (Arena a : pl.mgr.list())
                if (joinable(a) && a.mode().equalsIgnoreCase(mode) && a.players.size() + need <= a.maxPlayers() && need <= a.teamSize) ok.add(a);
            chosen = ok.isEmpty() ? null : ok.get(new java.util.Random().nextInt(ok.size()));
        } else chosen = pl.mgr.quick(mode, need);
        if (chosen == null) { p.sendMessage(Msg.m("no-arena")); return; }
        p.closeInventory();
        pl.parties.tryJoin(p, chosen);
    }

    public static void openParty(Player p, BedWarsX pl) { openParty(p, pl, 0); }

    /** Party Manager (one chest): members + book on top, invite heads in the middle, buttons at the bottom. */
    public static void openParty(Player p, BedWarsX pl, int page) {
        Holder h = new Holder("HUB_PARTY");
        String base = "party-menu.";
        Inventory inv = Bukkit.createInventory(h, SIZE, U.c(pl.guiCfg.getString(base + "title", "&8BedWars &7» &5Party Manager")));
        h.inventory = inv;
        Party party = pl.parties.of(p);
        int max = pl.getConfig().getInt("party.max-size", 4);
        String leaderName = party == null ? "-" : String.valueOf(Bukkit.getOfflinePlayer(party.leader).getName());

        // online players that can be invited (9 per page, the Refresh button turns the page)
        List<Player> candidates = new ArrayList<>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(p) || pl.mgr.of(target) != null || pl.parties.of(target) != null) continue;
            candidates.add(target);
        }
        int pages = Math.max(1, (candidates.size() + HEADS_PER_PAGE - 1) / HEADS_PER_PAGE);
        h.page = ((page % pages) + pages) % pages;
        String[] kv = {"%members%", party == null ? "0" : "" + party.size(), "%max%", "" + max, "%leader%", leaderName,
                "%page%", "" + (h.page + 1), "%pages%", "" + pages};

        // status book (lore differs with / without a party)
        {
            String path = base + "status";
            Material m = Material.matchMaterial(pl.guiCfg.getString(path + ".material", "BOOK"));
            List<String> lore = pl.guiCfg.getStringList(path + (party == null ? ".lore-none" : ".lore-party"));
            String[] l = new String[lore.size()];
            for (int i = 0; i < l.length; i++) l[i] = ph(lore.get(i), kv);
            put(inv, slotOf(pl, path, 4), U.item(m == null ? Material.BOOK : m, 1, ph(pl.guiCfg.getString(path + ".name", "&d&lPARTY STATUS"), kv), l));
        }

        // roster: the leader can click a member head to kick that member
        if (party != null) {
            int n = 0;
            for (UUID id : party.members) {
                if (n >= ROSTER_SLOTS.length) break;
                Player member = Bukkit.getPlayer(id);
                String prefix = pl.guiCfg.getString(base + (id.equals(party.leader) ? "roster.leader-prefix" : "roster.member-prefix"), "");
                String loreKey = id.equals(p.getUniqueId()) ? "roster.lore-self"
                        : (party.leader.equals(p.getUniqueId()) ? "roster.lore-kick" : "roster.lore-not-leader");
                inv.setItem(ROSTER_SLOTS[n++], head(member, id, prefix, pl.guiCfg.getString(base + loreKey, "")));
                h.roster.add(id);
            }
        }

        for (int i = h.page * HEADS_PER_PAGE; i < candidates.size() && i < (h.page + 1) * HEADS_PER_PAGE; i++) {
            Player target = candidates.get(i);
            inv.setItem(HEADS_FROM + (i - h.page * HEADS_PER_PAGE), head(target, target.getUniqueId(),
                    pl.guiCfg.getString(base + "invite-head.prefix", "&aInvite &f"),
                    pl.guiCfg.getString(base + "invite-head.lore", "&7Click to send a party invite")));
            h.players.add(target.getUniqueId());
        }
        if (candidates.isEmpty()) put(inv, P_NONE, gi(pl, base + "no-players"));

        boolean leader = party != null && party.leader.equals(p.getUniqueId());
        if (party == null) put(inv, slotOf(pl, base + "create", P_CREATE), gi(pl, base + "create"));
        else put(inv, slotOf(pl, base + "leave", P_CREATE), gi(pl, base + "leave"));
        put(inv, slotOf(pl, base + "refresh", P_REFRESH), gi(pl, base + "refresh", kv));
        put(inv, slotOf(pl, base + "quick-join", P_QUICK), gi(pl, base + "quick-join"));
        String acc = pl.parties.hasInvite(p) ? "accept" : "no-invite";
        put(inv, slotOf(pl, base + acc, P_ACCEPT), gi(pl, base + acc));
        put(inv, slotOf(pl, base + "invite-info", P_INFO), gi(pl, base + "invite-info"));
        String dis = leader ? "disband" : "disband-locked";
        put(inv, slotOf(pl, base + dis, P_DISBAND), gi(pl, base + dis));
        p.openInventory(inv);
    }

    private static ItemStack head(Player online, UUID id, String prefix, String loreLine) {
        ItemStack item = new ItemStack(U.mat("PLAYER_HEAD"));
        if (online != null) {
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(online);
                meta.setDisplayName(U.c(prefix + online.getName()));
                List<String> lore = new ArrayList<>();
                lore.add(U.c(loreLine));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
        } else {
            String offlineName = Bukkit.getOfflinePlayer(id).getName();
            item = U.item(Material.PLAYER_HEAD, 1, prefix + (offlineName == null ? "Unknown" : offlineName) + " &8(offline)");
        }
        return item;
    }

    public static void click(Player p, BedWarsX pl, Holder h, int slot) {
        if (h.type.equals(CosmeticsMenu.MAIN) || h.type.equals(CosmeticsMenu.CAT)) { CosmeticsMenu.click(p, pl, h, slot); return; }
        if (h.type.equals(StatsMenu.TYPE)) { StatsMenu.click(p, pl, slot); return; }
        if (h.type.equals(AchievementsMenu.TYPE)) { AchievementsMenu.click(p, pl, h, slot); return; }
        if (h.type.equals("HUB_MODES")) {
            if (slot == slotOf(pl, "map-selector.close", SEL_CLOSE)) { p.closeInventory(); return; }
            for (int i = 0; i < MODE_KEYS.length; i++)
                if (slotOf(pl, "map-selector.modes." + MODE_KEYS[i], 11 + i) == slot) { openMode(p, pl, MODE_NAMES[i]); return; }
            return;
        }
        if (h.type.equals("HUB_MODE")) {
            if (slot == slotOf(pl, "mode-menu.play", MODE_PLAY)) play(p, pl, h.mode, false);
            else if (slot == slotOf(pl, "mode-menu.maps", MODE_MAPS)) openArenaList(p, pl, h.mode);
            else if (slot == slotOf(pl, "mode-menu.back", MODE_BACK)) openMaps(p, pl);
            else if (slot == slotOf(pl, "mode-menu.close", MODE_CLOSE)) p.closeInventory();
            else if (slot == slotOf(pl, "mode-menu.random", MODE_RANDOM)) play(p, pl, h.mode, true);
            return;
        }
        if (h.type.equals("HUB_MAPS")) {
            if (slot == slotOf(pl, "map-list.back", LIST_BACK)) { openMode(p, pl, h.mode); return; }
            if (slot == slotOf(pl, "map-list.refresh", LIST_REFRESH)) { openArenaList(p, pl, h.mode); return; }
            if (slot < 0 || slot >= h.arenas.size()) return;
            Arena a = pl.mgr.get(h.arenas.get(slot));
            if (a == null || !joinable(a)) {
                p.sendMessage(U.c("&cThat map is no longer available."));
                openArenaList(p, pl, h.mode);
                return;
            }
            p.closeInventory();
            pl.parties.tryJoin(p, a);
            return;
        }
        if (!h.type.equals("HUB_PARTY")) return;
        String base = "party-menu.";
        Party party = pl.parties.of(p);
        int ri = -1;
        for (int i = 0; i < ROSTER_SLOTS.length; i++) if (ROSTER_SLOTS[i] == slot) ri = i;
        if (ri >= 0 && party != null) {
            if (ri >= h.roster.size()) return;
            UUID target = h.roster.get(ri);
            if (target.equals(p.getUniqueId())) return;
            if (!party.leader.equals(p.getUniqueId())) {
                p.sendMessage(Msg.m("party-not-leader"));
                return;
            }
            String targetName = Bukkit.getOfflinePlayer(target).getName();
            if (targetName != null) pl.parties.kick(p, targetName);
            openParty(p, pl, h.page);
            return;
        }
        if (slot == slotOf(pl, base + "create", P_CREATE) || slot == slotOf(pl, base + "leave", P_CREATE)) {
            if (party == null) pl.parties.create(p);
            else pl.parties.leave(p.getUniqueId());
            openParty(p, pl, 0);
            return;
        }
        if (slot == slotOf(pl, base + "refresh", P_REFRESH)) { openParty(p, pl, h.page + 1); return; }
        if (slot == slotOf(pl, base + "invite-info", P_INFO)) { openParty(p, pl, h.page); return; }
        if (slot == slotOf(pl, base + "quick-join", P_QUICK)) {
            int need = party != null && party.leader.equals(p.getUniqueId()) ? party.size() : 1;
            Arena arena = pl.mgr.quick(null, need);
            if (arena == null) p.sendMessage(Msg.m("no-arena"));
            else { p.closeInventory(); pl.parties.tryJoin(p, arena); }
            return;
        }
        if (slot == slotOf(pl, base + "accept", P_ACCEPT) && pl.parties.hasInvite(p)) {
            pl.parties.accept(p);
            openParty(p, pl, 0);
            return;
        }
        if (slot == slotOf(pl, base + "disband", P_DISBAND) || slot == slotOf(pl, base + "disband-locked", P_DISBAND)) {
            if (party != null && party.leader.equals(p.getUniqueId())) pl.parties.disband(p);
            else if (party != null) p.sendMessage(Msg.m("party-not-leader"));
            openParty(p, pl, 0);
            return;
        }
        if (slot < HEADS_FROM || slot >= HEADS_FROM + HEADS_PER_PAGE) return;
        int index = slot - HEADS_FROM;
        if (index >= h.players.size()) return;
        Player target = Bukkit.getPlayer(h.players.get(index));
        if (target == null) { p.sendMessage(U.c("&cThat player is no longer online.")); openParty(p, pl, h.page); return; }
        pl.parties.invite(p, target);
        openParty(p, pl, h.page);
    }
}
