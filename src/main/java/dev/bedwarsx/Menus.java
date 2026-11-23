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
import java.util.Map;
import java.util.UUID;

/**
 * All GUIs. Menus are identified by a custom InventoryHolder (never by title / InventoryView),
 * which keeps this class working on every Minecraft version.
 */
public class Menus {

    public static class H implements InventoryHolder {
        public final String type;
        public String cat = "Quick Buy";
        public Inventory inv;
        public List<SI> items = new ArrayList<>();
        public List<UUID> targets = new ArrayList<>();
        public List<TeamColor> tc = new ArrayList<>();
        public H(String t) { type = t; }
        @Override public Inventory getInventory() { return inv; }
    }

    public static class SI {
        public final String id, cat, icon, name, costMat;
        public final int amt, cost;
        SI(String id, String cat, String icon, int amt, String name, String costMat, int cost) {
            this.id = id; this.cat = cat; this.icon = icon; this.amt = amt; this.name = name; this.costMat = costMat; this.cost = cost;
        }
    }

    static class R { Material icon, cm; int amt, cost; String name; boolean max; }

    /** Everything below is filled from shop.yml by load(); the values here are only safety defaults. */
    public static final List<String> CATS = new ArrayList<>();
    static final List<String> CAT_ICON = new ArrayList<>();
    public static final List<String> DEFAULT_QUICK = new ArrayList<>();

    static String[] PICKS = {"WOODEN_PICKAXE", "IRON_PICKAXE", "GOLDEN_PICKAXE", "DIAMOND_PICKAXE"};
    static String[] PICKN = {"Wooden Pickaxe", "Iron Pickaxe", "Golden Pickaxe", "Diamond Pickaxe"};
    static String[] AXES = {"WOODEN_AXE", "STONE_AXE", "IRON_AXE", "DIAMOND_AXE"};
    static String[] AXEN = {"Wooden Axe", "Stone Axe", "Iron Axe", "Diamond Axe"};
    static String[] PICKC = {"IRON_INGOT", "IRON_INGOT", "GOLD_INGOT", "GOLD_INGOT"};
    static String[] AXEC = {"IRON_INGOT", "IRON_INGOT", "GOLD_INGOT", "GOLD_INGOT"};
    static int[] PICKP = {10, 10, 3, 6};
    static int[] AXEP = {10, 10, 3, 6};
    static int[] EFFP = {1, 2, 3, 3};
    static int[] EFFA = {1, 1, 2, 3};

    static String shopTitle = "&8Item Shop - %category%";
    static String upgradesTitle = "&8Team Upgrades & Traps";
    static int sharpCost = 4, healCost = 1;
    static int[] ARMOR_COST = {2, 4, 8, 16};
    static int[] MINER_COST = {2, 4};
    static int[] FORGE_COST = {2, 4, 6, 8};
    static int[] TRAP_COST = {1, 2, 4};
    static final String[] FORGE_DESC = {"+50% resources", "+100% resources", "Spawn emeralds", "+200% resources"};

    public static final List<SI> ALL = new ArrayList<>();

    private static int[] ints(List<Integer> l, int[] def) {
        if (l == null || l.size() < def.length) return def;
        int[] r = new int[def.length];
        for (int i = 0; i < r.length; i++) r[i] = l.get(i);
        return r;
    }

    /** (Re)loads the item shop and the team upgrade prices from shop.yml. Called on enable and on /bw reload. */
    public static void load(org.bukkit.configuration.file.YamlConfiguration y) {
        ALL.clear();
        CATS.clear();
        CAT_ICON.clear();
        DEFAULT_QUICK.clear();
        shopTitle = y.getString("shop.title", shopTitle);
        org.bukkit.configuration.ConfigurationSection cats = y.getConfigurationSection("shop.categories");
        if (cats != null) for (String k : cats.getKeys(false)) { CATS.add(k.replace('_', ' ')); CAT_ICON.add(cats.getString(k + ".icon", "STONE")); }
        if (CATS.isEmpty()) { CATS.add("Quick Buy"); CAT_ICON.add("NETHER_STAR"); }
        DEFAULT_QUICK.addAll(y.getStringList("shop.quick-buy-default"));
        org.bukkit.configuration.ConfigurationSection items = y.getConfigurationSection("shop.items");
        if (items != null) {
            for (String id : items.getKeys(false)) {
                String b = id + ".";
                String cat = items.getString(b + "category", "Blocks").replace('_', ' ');
                add(id, cat, items.getString(b + "icon", "STONE"), Math.max(1, items.getInt(b + "amount", 1)),
                        items.getString(b + "name", id), items.getString(b + "currency", "IRON_INGOT"), Math.max(0, items.getInt(b + "cost", 1)));
            }
        }
        // pickaxe / axe tiers
        for (int kind = 0; kind < 2; kind++) {
            List<java.util.Map<?, ?>> tiers = y.getMapList(kind == 0 ? "shop.tools.pickaxe" : "shop.tools.axe");
            if (tiers.size() < 4) continue;
            String[] ic = new String[4], nm = new String[4], cu = new String[4];
            int[] co = new int[4], ef = new int[4];
            for (int i = 0; i < 4; i++) {
                java.util.Map<?, ?> t = tiers.get(i);
                ic[i] = String.valueOf(t.get("icon")); nm[i] = String.valueOf(t.get("name")); cu[i] = String.valueOf(t.get("currency"));
                try { co[i] = Integer.parseInt(String.valueOf(t.get("cost"))); } catch (Exception e) { co[i] = 10; }
                try { ef[i] = Integer.parseInt(String.valueOf(t.get("efficiency"))); } catch (Exception e) { ef[i] = 1; }
            }
            if (kind == 0) { PICKS = ic; PICKN = nm; PICKC = cu; PICKP = co; EFFP = ef; }
            else { AXES = ic; AXEN = nm; AXEC = cu; AXEP = co; EFFA = ef; }
        }
        // team upgrades
        upgradesTitle = y.getString("upgrades.title", upgradesTitle);
        sharpCost = y.getInt("upgrades.sharpened-swords.cost", 4);
        healCost = y.getInt("upgrades.heal-pool.cost", 1);
        ARMOR_COST = ints(y.getIntegerList("upgrades.reinforced-armor.costs"), ARMOR_COST);
        MINER_COST = ints(y.getIntegerList("upgrades.maniac-miner.costs"), MINER_COST);
        FORGE_COST = ints(y.getIntegerList("upgrades.iron-forge.costs"), FORGE_COST);
        TRAP_COST = ints(y.getIntegerList("upgrades.traps.costs"), TRAP_COST);
    }

    private static void add(String id, String cat, String icon, int amt, String name, String cm, int cost) {
        ALL.add(new SI(id, cat, icon, amt, name, cm, cost));
    }

    public static SI find(String id) {
        for (SI s : ALL) if (s.id.equals(id)) return s;
        return null;
    }

    public static ItemStack tool(int kind, int idx) {
        ItemStack i = new ItemStack(U.mat(kind == 0 ? PICKS[idx] : AXES[idx]));
        U.addEnch(i, "efficiency", kind == 0 ? EFFP[idx] : EFFA[idx]);
        return U.unbreakable(i);
    }

    static String costName(Material m) {
        switch (m) {
            case IRON_INGOT: return "Iron";
            case GOLD_INGOT: return "Gold";
            case DIAMOND: return "Diamond";
            case EMERALD: return "Emerald";
            default: return m.name();
        }
    }

    static int count(Player p, Material m) {
        int n = 0;
        for (ItemStack i : p.getInventory().getContents()) if (i != null && i.getType() == m) n += i.getAmount();
        return n;
    }

    static void take(Player p, Material m, int n) {
        ItemStack[] c = p.getInventory().getContents();
        for (int s = 0; s < c.length && n > 0; s++) {
            ItemStack i = c[s];
            if (i == null || i.getType() != m) continue;
            if (i.getAmount() <= n) { n -= i.getAmount(); p.getInventory().setItem(s, null); }
            else { i.setAmount(i.getAmount() - n); n = 0; }
        }
    }

    static void give(Player p, ItemStack i) {
        for (ItemStack left : p.getInventory().addItem(i).values()) p.getWorld().dropItem(p.getLocation(), left);
    }

    // =====================================================================  ITEM SHOP
    static R resolve(Arena.PD d, SI s) {
        R r = new R();
        r.icon = U.mat(s.icon); r.amt = s.amt; r.name = s.name; r.cm = U.mat(s.costMat); r.cost = s.cost;
        switch (s.id) {
            case "wool": r.icon = d.team.block("WOOL"); break;
            case "clay": r.icon = d.team.block("TERRACOTTA"); break;
            case "glass": r.icon = d.team.block("STAINED_GLASS"); break;
            case "pick":
                if (d.pick >= 4) r.max = true;
                else { r.icon = U.mat(PICKS[d.pick]); r.name = PICKN[d.pick]; r.cm = U.mat(PICKC[d.pick]); r.cost = PICKP[d.pick]; }
                break;
            case "axe":
                if (d.axe >= 4) r.max = true;
                else { r.icon = U.mat(AXES[d.axe]); r.name = AXEN[d.axe]; r.cm = U.mat(AXEC[d.axe]); r.cost = AXEP[d.axe]; }
                break;
            case "chain": if (d.armor >= 1) r.max = true; break;
            case "iron": if (d.armor >= 2) r.max = true; break;
            case "diamond": if (d.armor >= 3) r.max = true; break;
            case "shears": if (d.shears) r.max = true; break;
            default: break;
        }
        return r;
    }

    public static void openShop(Player p, Arena a, String cat) {
        Arena.PD d = a.players.get(p.getUniqueId());
        if (d == null || d.team == null) return;
        H h = new H("SHOP");
        h.cat = cat;
        Inventory inv = Bukkit.createInventory(h, 54, U.c(shopTitle.replace("%category%", cat)));
        h.inv = inv;
        for (int i = 0; i < CATS.size() && i < 9; i++)
            inv.setItem(i, U.item(U.mat(CAT_ICON.get(i)), 1, (CATS.get(i).equals(cat) ? "&a" : "&e") + CATS.get(i), "&7Click to view"));
        ItemStack pane = U.item(U.mat("GRAY_STAINED_GLASS_PANE"), 1, " ");
        for (int i = 9; i < 18; i++) inv.setItem(i, pane);
        List<SI> list = new ArrayList<>();
        if (cat.equals("Quick Buy")) {
            for (String id : a.pl.stats.fav(p.getUniqueId())) { SI s = find(id); if (s != null) list.add(s); }
        } else for (SI s : ALL) if (s.cat.equals(cat)) list.add(s);
        h.items = list;
        for (int i = 0; i < list.size() && i < 36; i++) {
            SI s = list.get(i);
            R r = resolve(d, s);
            if (r.max) { inv.setItem(18 + i, U.item(r.icon, 1, "&a" + s.name, "&aAlready owned / maxed")); continue; }
            boolean afford = count(p, r.cm) >= r.cost;
            inv.setItem(18 + i, U.item(r.icon, r.amt, (afford ? "&a" : "&c") + r.name,
                    "&7Cost: " + (afford ? "&a" : "&c") + r.cost + " " + costName(r.cm), "",
                    afford ? "&eClick to purchase!" : "&cYou don't have enough " + costName(r.cm) + "!",
                    "&8Shift-click: add/remove from Quick Buy"));
        }
        p.openInventory(inv);
    }

    static void buy(Player p, Arena a, SI s) {
        Arena.PD d = a.players.get(p.getUniqueId());
        if (d == null) return;
        R r = resolve(d, s);
        if (r.max) { p.sendMessage(Msg.m("shop-have")); U.sound(p, "entity.villager.no", 1f, 1f); return; }
        if (count(p, r.cm) < r.cost) {
            p.sendMessage(Msg.m("shop-nomoney", "%item%", costName(r.cm)));
            U.sound(p, "entity.villager.no", 1f, 1f);
            return;
        }
        take(p, r.cm, r.cost);
        switch (s.id) {
            case "wool": case "clay": case "glass":
                give(p, new ItemStack(r.icon, r.amt)); break;
            case "kbstick": {
                ItemStack k = U.item(Material.STICK, 1, "&fKnockback Stick");
                U.addEnch(k, "knockback", 1);
                give(p, k); break;
            }
            case "chain": d.armor = 1; a.giveArmor(p, d); a.upgrades(p); break;
            case "iron": d.armor = 2; a.giveArmor(p, d); a.upgrades(p); break;
            case "diamond": d.armor = 3; a.giveArmor(p, d); a.upgrades(p); break;
            case "shears":
                d.shears = true;
                give(p, U.unbreakable(new ItemStack(Material.SHEARS))); break;
            case "pick": {
                for (int i = 0; i < p.getInventory().getSize(); i++) {
                    ItemStack it = p.getInventory().getItem(i);
                    if (it != null && it.getType().name().endsWith("_PICKAXE")) p.getInventory().setItem(i, null);
                }
                give(p, tool(0, d.pick));
                d.pick++;
                break;
            }
            case "axe": {
                for (int i = 0; i < p.getInventory().getSize(); i++) {
                    ItemStack it = p.getInventory().getItem(i);
                    if (it != null && it.getType().name().endsWith("_AXE")) p.getInventory().setItem(i, null);
                }
                give(p, tool(1, d.axe));
                d.axe++;
                break;
            }
            case "bow": give(p, U.unbreakable(new ItemStack(Material.BOW))); break;
            case "bow2": {
                ItemStack b = new ItemStack(Material.BOW);
                U.addEnch(b, "power", 1);
                give(p, U.unbreakable(b)); break;
            }
            case "bow3": {
                ItemStack b = new ItemStack(Material.BOW);
                U.addEnch(b, "power", 1);
                U.addEnch(b, "punch", 1);
                give(p, U.unbreakable(b)); break;
            }
            case "speed": U.eff(p, "speed", 900, 1); break;
            case "jump": U.eff(p, "jump_boost", 900, 4); break;
            case "invis": U.eff(p, "invisibility", 600, 0); break;
            case "bedbug": give(p, U.item(Material.SNOWBALL, 1, "&fBed Bug", "&7Spawns a silverfish where it lands")); break;
            case "golem": give(p, U.item(U.mat("GHAST_TEAR"), 1, "&eDream Defender", "&7Right click to spawn an Iron Golem")); break;
            case "fireball": give(p, U.item(U.mat("FIRE_CHARGE"), 1, "&cFireball", "&7Right click to launch")); break;
            case "tower": give(p, U.item(Material.CHEST, 1, "&ePop-up Tower", "&7Place it to build a tower")); break;
            case "egg": give(p, U.item(Material.EGG, 1, "&bBridge Egg", "&7Throw it to build a bridge")); break;
            case "milk": give(p, U.item(Material.MILK_BUCKET, 1, "&fMagic Milk", "&7Drink: no traps trigger for 30s")); break;
            default: give(p, new ItemStack(U.mat(s.icon), s.amt)); break;
        }
        a.upgrades(p);
        U.sound(p, "entity.item.pickup", 1f, 1f);
        p.sendMessage(Msg.m("shop-bought", "%item%", r.name));
    }

    // =====================================================================  TEAM UPGRADES
    static String costLine(int cost) { return "&7Cost: &b" + cost + " Diamond" + (cost == 1 ? "" : "s"); }

    public static void openUpgrades(Player p, Arena a) {
        Arena.PD d = a.players.get(p.getUniqueId());
        if (d == null || d.team == null) return;
        Arena.BT t = a.teams.get(d.team);
        H h = new H("UPG");
        Inventory inv = Bukkit.createInventory(h, 36, U.c(upgradesTitle));
        h.inv = inv;
        inv.setItem(10, U.item(Material.IRON_SWORD, 1, "&aSharpened Swords",
                "&7Your team permanently gets Sharpness I", "&7on all swords.", "",
                t.sharp > 0 ? "&aUNLOCKED" : costLine(sharpCost)));
        inv.setItem(11, U.item(Material.IRON_CHESTPLATE, 1, "&aReinforced Armor " + (t.prot >= 4 ? "IV" : roman(t.prot + 1)),
                "&7Permanent Protection on all armor.", "&7Current tier: &e" + t.prot + "/4", "",
                t.prot >= 4 ? "&aMAXED" : costLine(ARMOR_COST[t.prot])));
        inv.setItem(12, U.item(U.mat("GOLDEN_PICKAXE"), 1, "&aManiac Miner " + (t.miner >= 2 ? "II" : roman(t.miner + 1)),
                "&7Haste for your whole team.", "&7Current tier: &e" + t.miner + "/2", "",
                t.miner >= 2 ? "&aMAXED" : costLine(MINER_COST[t.miner])));
        inv.setItem(13, U.item(Material.FURNACE, 1, "&aIron Forge " + (t.forge >= 4 ? "IV" : roman(t.forge + 1)),
                "&7Faster generators on your island.", t.forge < 4 ? "&7Next: &e" + FORGE_DESC[t.forge] : "", "",
                t.forge >= 4 ? "&aMAXED" : costLine(FORGE_COST[t.forge])));
        inv.setItem(14, U.item(Material.BEACON, 1, "&aHeal Pool",
                "&7Regeneration near your base.", "", t.heal ? "&aUNLOCKED" : costLine(healCost)));

        int q = t.traps.size();
        String qc = q >= 3 ? "&cQueue is full" : costLine(TRAP_COST[q]);
        inv.setItem(28, U.item(U.mat("TRIPWIRE_HOOK"), 1, "&aIt's a Trap!", "&7Blindness + Slowness for 8s.", "", qc));
        inv.setItem(29, U.item(U.mat("FEATHER"), 1, "&aCounter-Offensive Trap", "&7Speed II + Jump II for defenders.", "", qc));
        inv.setItem(30, U.item(U.mat("REDSTONE_TORCH"), 1, "&aAlarm Trap", "&7Reveals invisible intruders.", "", qc));
        inv.setItem(31, U.item(U.mat("IRON_PICKAXE"), 1, "&aMiner Fatigue Trap", "&7Mining Fatigue for 10s.", "", qc));
        List<String> ql = new ArrayList<>();
        ql.add("&7Triggers one at a time (30s cooldown).");
        ql.add("");
        for (int i = 0; i < 3; i++) ql.add("&e" + (i + 1) + ". " + (i < q ? "&a" + t.traps.get(i).label : "&8Empty"));
        inv.setItem(33, U.item(Material.BOOK, 1, "&eTrap Queue", ql.toArray(new String[0])));
        p.openInventory(inv);
    }

    static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV"};
        return r[Math.max(0, Math.min(4, n))];
    }

    static void clickUpgrade(Player p, Arena a, int slot) {
        Arena.PD d = a.players.get(p.getUniqueId());
        if (d == null) return;
        Arena.BT t = a.teams.get(d.team);
        int cost = -1;
        String what = null;
        switch (slot) {
            case 10: if (t.sharp == 0) { cost = sharpCost; what = "Sharpened Swords"; } break;
            case 11: if (t.prot < 4) { cost = ARMOR_COST[t.prot]; what = "Reinforced Armor " + roman(t.prot + 1); } break;
            case 12: if (t.miner < 2) { cost = MINER_COST[t.miner]; what = "Maniac Miner " + roman(t.miner + 1); } break;
            case 13: if (t.forge < 4) { cost = FORGE_COST[t.forge]; what = "Iron Forge " + roman(t.forge + 1); } break;
            case 14: if (!t.heal) { cost = healCost; what = "Heal Pool"; } break;
            case 28: case 29: case 30: case 31:
                if (t.traps.size() < 3) { cost = TRAP_COST[t.traps.size()]; what = Arena.Trap.values()[slot - 28].label + " Trap"; }
                else { p.sendMessage(Msg.m("trap-full")); return; }
                break;
            default: return;
        }
        if (cost < 0) { p.sendMessage(Msg.m("already-unlocked")); return; }
        if (count(p, Material.DIAMOND) < cost) {
            p.sendMessage(Msg.m("need-diamonds", "%n%", "" + cost));
            U.sound(p, "entity.villager.no", 1f, 1f);
            return;
        }
        take(p, Material.DIAMOND, cost);
        switch (slot) {
            case 10: t.sharp = 1; break;
            case 11: t.prot++; break;
            case 12: t.miner++; break;
            case 13: t.forge++; break;
            case 14: t.heal = true; break;
            default: t.traps.add(Arena.Trap.values()[slot - 28]); break;
        }
        for (Player m : a.online(d.team)) {
            m.sendMessage(Msg.m("team-purchased", "%player%", p.getName(), "%what%", what));
            a.upgrades(m);
        }
        U.sound(p, "entity.player.levelup", 1f, 1.5f);
    }

    // =====================================================================  TEAM SELECTOR / SPECTATOR
    public static void openTeamSelect(Player p, Arena a) {
        H h = new H("TEAM");
        Inventory inv = Bukkit.createInventory(h, 18, U.c("&8Select a Team"));
        h.inv = inv;
        int i = 0;
        for (Arena.BT t : a.teams.values()) {
            List<String> lore = new ArrayList<>();
            lore.add("&7Players: &e" + a.countTeam(t.color) + "/" + a.teamSize);
            for (Map.Entry<UUID, Arena.PD> e : a.players.entrySet()) {
                if (e.getValue().team != t.color) continue;
                Player q = Bukkit.getPlayer(e.getKey());
                if (q != null) lore.add("&8- &f" + q.getName());
            }
            inv.setItem(i, U.item(t.color.block("WOOL"), 1, t.color.chat + "&l" + t.color.display + " Team", lore.toArray(new String[0])));
            h.tc.add(t.color);
            i++;
        }
        p.openInventory(inv);
    }

    public static void openSpectator(Player p, Arena a) {
        H h = new H("SPEC");
        Inventory inv = Bukkit.createInventory(h, 27, U.c("&8Teleporter"));
        h.inv = inv;
        int i = 0;
        for (Map.Entry<UUID, Arena.PD> e : a.players.entrySet()) {
            Arena.PD d = e.getValue();
            Player q = Bukkit.getPlayer(e.getKey());
            if (q == null || !d.alive || d.respawning || i >= 27) continue;
            ItemStack head = new ItemStack(U.mat("PLAYER_HEAD"));
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            if (sm != null) {
                sm.setOwningPlayer(q);
                sm.setDisplayName(U.c(d.team.chat + q.getName()));
                head.setItemMeta(sm);
            }
            inv.setItem(i, head);
            h.targets.add(q.getUniqueId());
            i++;
        }
        p.openInventory(inv);
    }

    // =====================================================================  click router
    public static void click(final Player p, final Arena a, final H h, int slot, boolean shift) {
        switch (h.type) {
            case "SHOP":
                if (slot >= 0 && slot < CATS.size() && slot < 9) {
                    final String c = CATS.get(slot);
                    Bukkit.getScheduler().runTask(a.pl, new Runnable() { @Override public void run() { openShop(p, a, c); } });
                } else if (slot >= 18 && slot - 18 < h.items.size()) {
                    SI s = h.items.get(slot - 18);
                    if (shift) {
                        a.pl.stats.toggleFav(p.getUniqueId(), s.id);
                        p.sendMessage(Msg.m("quick-updated"));
                    } else buy(p, a, s);
                    Bukkit.getScheduler().runTask(a.pl, new Runnable() { @Override public void run() { openShop(p, a, h.cat); } });
                }
                break;
            case "UPG":
                clickUpgrade(p, a, slot);
                Bukkit.getScheduler().runTask(a.pl, new Runnable() { @Override public void run() { openUpgrades(p, a); } });
                break;
            case "TEAM":
                if (slot >= 0 && slot < h.tc.size()) {
                    a.selectTeam(p, h.tc.get(slot));
                    Bukkit.getScheduler().runTask(a.pl, new Runnable() { @Override public void run() { p.closeInventory(); } });
                }
                break;
            case "SPEC":
                if (slot >= 0 && slot < h.targets.size()) {
                    Player t = Bukkit.getPlayer(h.targets.get(slot));
                    if (t != null) p.teleport(t.getLocation());
                    Bukkit.getScheduler().runTask(a.pl, new Runnable() { @Override public void run() { p.closeInventory(); } });
                }
                break;
            default: break;
        }
    }
}
