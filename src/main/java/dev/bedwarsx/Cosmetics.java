/*
 * BedWarsX - Copyright (c) 2026 ASHRAF ELHENAWY (ItzRealAshraf). All rights reserved.
 * Copying, re-selling, re-branding or removing the credits is not allowed.
 */
package dev.bedwarsx;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cosmetics: kill / final kill / victory / death effects, trails, projectile trails, bed designs and shop NPC skins.
 * Everything is defined in cosmetics.yml (names, icons, prices, particles). Coins buy them; free-all unlocks everything.
 * Particles / entities are resolved by name at runtime, so ONE jar works on every version.
 */
public class Cosmetics {
    public static final String[] CATS = {"kill", "final", "victory", "death", "trail", "projectile", "bed", "npc"};

    public static class Item {
        public String cat, id, name = "", type = "burst", perm = "", sound = "", block = "", shape = "BALL_LARGE";
        public String entity = "", profession = "", villagerType = "", decor = "";
        public Material mat = Material.PAPER;
        public int price, count = 10, duration = 20, interval = 10, strikes = 1;
        public boolean enabled = true, rainbow;
        public double spread = 0.3, speed = 0.05, yoff = 1.0, radius = 1.5, height = 2.5;
        public float volume = 1f, pitch = 1f, size = 1f;
        public final List<String> lore = new ArrayList<>();
        public final List<Particle> particles = new ArrayList<>();
        public final List<Color> colors = new ArrayList<>();

        public String key() { return cat + "." + id; }

        boolean animated() { return type.equals("helix") || type.equals("ring") || type.equals("column") || type.equals("rain"); }
    }

    private final BedWarsX pl;
    private YamlConfiguration cfg;
    private boolean enabled = true, free;
    private final Map<String, List<Item>> items = new LinkedHashMap<>();
    private final Map<UUID, Location> last = new HashMap<>();

    public Cosmetics(BedWarsX pl) { this.pl = pl; }

    // ------------------------------------------------------------------ loading
    public void load(YamlConfiguration y) {
        cfg = y;
        enabled = y.getBoolean("enabled", true);
        free = y.getBoolean("free-all", false);
        items.clear();
        for (String cat : CATS) {
            List<Item> l = new ArrayList<>();
            ConfigurationSection sec = y.getConfigurationSection("categories." + cat + ".items");
            if (sec != null) for (String id : sec.getKeys(false)) {
                ConfigurationSection c = sec.getConfigurationSection(id);
                if (c == null) continue;
                try { l.add(parse(cat, id, c)); } catch (Throwable t) { pl.getLogger().warning("cosmetics.yml: bad entry " + cat + "." + id + ": " + t); }
            }
            items.put(cat, l);
        }
    }

    private Item parse(String cat, String id, ConfigurationSection c) {
        Item it = new Item();
        it.cat = cat;
        it.id = id;
        it.name = c.getString("name", id);
        Material m = Material.matchMaterial(c.getString("material", "PAPER"));
        it.mat = m == null ? Material.PAPER : m;
        it.price = Math.max(0, c.getInt("price", 0));
        it.perm = c.getString("permission", "");
        it.enabled = c.getBoolean("enabled", true);
        it.type = c.getString("type", "burst").toLowerCase();
        it.count = c.getInt("count", 10);
        it.spread = c.getDouble("spread", 0.3);
        it.speed = c.getDouble("speed", 0.05);
        it.yoff = c.getDouble("height-offset", cat.equals("trail") ? 0.15 : 1.0);
        it.radius = c.getDouble("radius", 1.5);
        it.height = c.getDouble("height", 2.5);
        it.duration = Math.max(1, c.getInt("duration", 20));
        it.interval = Math.max(2, c.getInt("interval", 10));
        it.strikes = Math.max(1, c.getInt("strikes", 1));
        it.sound = c.getString("sound", "");
        it.volume = (float) c.getDouble("volume", 1.0);
        it.pitch = (float) c.getDouble("pitch", 1.0);
        it.size = (float) c.getDouble("size", 1.0);
        it.block = c.getString("block", "");
        it.shape = c.getString("shape", "BALL_LARGE");
        it.rainbow = c.getBoolean("rainbow", false);
        it.entity = c.getString("entity", "");
        it.profession = c.getString("profession", "");
        it.villagerType = c.getString("villager-type", "");
        it.decor = c.getString("decor", "");
        it.lore.addAll(c.getStringList("lore"));
        for (String ps : c.getStringList("particle")) {
            Particle p = particle(ps);
            if (p != null) it.particles.add(p);
        }
        for (String cs : c.getStringList("colors")) {
            try { it.colors.add(Color.fromRGB(Integer.parseInt(cs.replace("#", "").trim(), 16))); } catch (Throwable ignored) { /* skip */ }
        }
        return it;
    }

    /** "FIREWORKS_SPARK|FIREWORK" -> the first name that exists on this server version. */
    private static Particle particle(String spec) {
        for (String n : spec.split("\\|")) {
            try { return Particle.valueOf(n.trim().toUpperCase()); } catch (Throwable ignored) { /* try next */ }
        }
        return null;
    }

    // ------------------------------------------------------------------ lookups
    public boolean enabled() { return enabled; }

    public boolean freeAll() { return free; }

    public List<Item> list(String cat) {
        List<Item> out = new ArrayList<>();
        List<Item> l = items.get(cat);
        if (l != null) for (Item it : l) if (it.enabled) out.add(it);
        return out;
    }

    public Item get(String cat, String id) {
        List<Item> l = items.get(cat);
        if (l != null) for (Item it : l) if (it.id.equalsIgnoreCase(id)) return it;
        return null;
    }

    public String catName(String cat) { return cfg == null ? cat : cfg.getString("categories." + cat + ".name", cat); }

    public String catTitle(String cat) { return cfg == null ? cat : cfg.getString("categories." + cat + ".title", "&8" + cat); }

    public String catIcon(String cat) { return cfg == null ? "PAPER" : cfg.getString("categories." + cat + ".icon", "PAPER"); }

    public List<String> catDesc(String cat) { return cfg == null ? new ArrayList<String>() : cfg.getStringList("categories." + cat + ".desc"); }

    public String text(String key, String def) { return cfg == null ? def : cfg.getString("menu." + key, def); }

    public boolean unlocked(UUID u, Item it) {
        if (free || it.price <= 0) return true;
        if (pl.stats.owns(u, it.key())) return true;
        if (it.perm != null && !it.perm.isEmpty()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.hasPermission(it.perm)) return true;
        }
        return false;
    }

    /** The cosmetic the player has equipped in this category, or null (none, disabled, or not unlocked). */
    public Item selected(UUID u, String cat) {
        if (!enabled) return null;
        String id = pl.stats.selected(u, cat);
        if (id.isEmpty()) return null;
        Item it = get(cat, id);
        return it != null && it.enabled && unlocked(u, it) ? it : null;
    }

    public int owned(UUID u, String cat) {
        int n = 0;
        for (Item it : list(cat)) if (unlocked(u, it)) n++;
        return n;
    }

    /** 0 = bought, 1 = not enough coins, 2 = already unlocked. */
    public int buy(Player p, Item it) {
        UUID u = p.getUniqueId();
        if (unlocked(u, it)) return 2;
        if (pl.stats.coins(u) < it.price) return 1;
        pl.stats.setCoins(u, pl.stats.coins(u) - it.price);
        pl.stats.unlock(u, it.key());
        pl.stats.save();
        return 0;
    }

    /** Admin: unlock "all" or one "<category>.<id>". Returns how many were unlocked, -1 for an unknown id. */
    public int give(UUID u, String spec) {
        int n = 0;
        if (spec.equalsIgnoreCase("all")) {
            for (String cat : CATS) for (Item it : list(cat)) { pl.stats.unlock(u, it.key()); n++; }
        } else {
            String[] a = spec.split("\\.", 2);
            Item it = a.length == 2 ? get(a[0].toLowerCase(), a[1]) : null;
            if (it == null) return -1;
            pl.stats.unlock(u, it.key());
            n = 1;
        }
        pl.stats.save();
        return n;
    }

    // ------------------------------------------------------------------ coins
    public void reward(Player p, String key, int def) {
        int n = pl.getConfig().getInt("coins." + key, def);
        if (n <= 0) return;
        pl.stats.add(p.getUniqueId(), "coins", n);
        if (pl.getConfig().getBoolean("coins.messages", true))
            p.sendMessage(Msg.m("coins-earned", "%n%", "" + n, "%reason%", Msg.m("coins-reason-" + key)));
    }

    // ------------------------------------------------------------------ effect engine
    private static Material mat(String n, Material def) {
        Material m = n == null || n.isEmpty() ? null : Material.matchMaterial(n);
        return m == null ? def : m;
    }

    private static Color hsv(double h) {
        h = h - Math.floor(h);
        double r, g, b, f = h * 6 - Math.floor(h * 6), q = 1 - f;
        switch ((int) (h * 6) % 6) {
            case 0: r = 1; g = f; b = 0; break;
            case 1: r = q; g = 1; b = 0; break;
            case 2: r = 0; g = 1; b = f; break;
            case 3: r = 0; g = q; b = 1; break;
            case 4: r = f; g = 0; b = 1; break;
            default: r = 1; g = 0; b = q; break;
        }
        return Color.fromRGB((int) (r * 255), (int) (g * 255), (int) (b * 255));
    }

    private static Color color(Item it, int tick) {
        if (it.rainbow) return hsv(tick * 0.03);
        if (it.colors.isEmpty()) return Color.RED;
        return it.colors.get(ThreadLocalRandom.current().nextInt(it.colors.size()));
    }

    private void spawn(Item it, Location l, int count, double spread, double speed, int tick) {
        World w = l.getWorld();
        if (w == null || count < 0) return;
        for (Particle pt : it.particles) {
            try {
                Class<?> dt = pt.getDataType();
                if (dt == Void.class) w.spawnParticle(pt, l, count, spread, spread, spread, speed);
                else if (dt == Particle.DustOptions.class)
                    w.spawnParticle(pt, l, Math.max(1, count), spread, spread, spread, 0, new Particle.DustOptions(color(it, tick), it.size));
                else if (dt == BlockData.class)
                    w.spawnParticle(pt, l, count, spread, spread, spread, speed, Bukkit.createBlockData(mat(it.block, Material.REDSTONE_BLOCK)));
                else if (dt == ItemStack.class)
                    w.spawnParticle(pt, l, count, spread, spread, spread, speed, new ItemStack(mat(it.block, Material.REDSTONE)));
            } catch (Throwable ignored) { /* particle not supported here */ }
        }
    }

    private void sound(Item it, Location l) {
        if (it.sound == null || it.sound.isEmpty() || l.getWorld() == null) return;
        try { l.getWorld().playSound(l, "minecraft:" + it.sound, it.volume, it.pitch); } catch (Throwable ignored) { /* ignore */ }
    }

    private void firework(Item it, Location l) {
        try {
            Firework fw = l.getWorld().spawn(l, Firework.class);
            FireworkMeta m = fw.getFireworkMeta();
            FireworkEffect.Type ft;
            try { ft = FireworkEffect.Type.valueOf(it.shape.toUpperCase()); } catch (Throwable t) { ft = FireworkEffect.Type.BALL_LARGE; }
            FireworkEffect.Builder b = FireworkEffect.builder().with(ft).flicker(true).trail(true);
            if (it.colors.isEmpty()) b.withColor(Color.RED);
            else for (Color c : it.colors) b.withColor(c);
            m.addEffect(b.build());
            m.setPower(0);
            fw.setFireworkMeta(m);
            fw.detonate();
        } catch (Throwable ignored) { /* ignore */ }
    }

    /** One frame of an animated effect (helix / ring / column / rain). c already includes the height offset. */
    private void frame(Item it, Location c, int t) {
        double two = Math.PI * 2;
        switch (it.type) {
            case "helix": {
                double a = t * 0.5, h = (t % 30) / 30.0 * it.height;
                for (int k = 0; k < 2; k++)
                    spawn(it, c.clone().add(Math.cos(a + k * Math.PI) * it.radius, h - it.yoff, Math.sin(a + k * Math.PI) * it.radius), 1, 0, 0, t);
                break;
            }
            case "ring": {
                double r = ((t % 20) / 20.0) * it.radius + 0.2;
                for (int i = 0; i < 18; i++) {
                    double a = two * i / 18;
                    spawn(it, c.clone().add(Math.cos(a) * r, -it.yoff + 0.1, Math.sin(a) * r), 1, 0, 0, t);
                }
                break;
            }
            case "column": {
                double h = (t % 20) / 20.0 * it.height;
                spawn(it, c.clone().add(0, h - it.yoff, 0), Math.max(1, it.count), it.spread, it.speed, t);
                break;
            }
            default: { // rain
                ThreadLocalRandom r = ThreadLocalRandom.current();
                for (int i = 0; i < Math.max(1, it.count); i++)
                    spawn(it, c.clone().add((r.nextDouble() - 0.5) * 2 * it.radius, r.nextDouble() * it.height - it.yoff, (r.nextDouble() - 0.5) * 2 * it.radius), 1, 0, 0, t);
            }
        }
    }

    /** Plays a one-shot effect (or starts the animation) at a location. */
    public void play(final Item it, Location base) {
        if (it == null || base == null || base.getWorld() == null) return;
        final Location loc = base.clone().add(0, it.yoff, 0);
        sound(it, loc);
        switch (it.type) {
            case "lightning": {
                final Location strike = base.clone();
                new BukkitRunnable() {
                    int n = 0;
                    @Override public void run() {
                        if (n++ >= it.strikes) { cancel(); return; }
                        try { strike.getWorld().strikeLightningEffect(strike); } catch (Throwable ignored) { /* ignore */ }
                        if (it.count > 0) spawn(it, loc, it.count, it.spread, it.speed, n);
                    }
                }.runTaskTimer(pl, 0L, 5L);
                break;
            }
            case "firework": firework(it, loc); break;
            case "helix": case "ring": case "column": case "rain":
                new BukkitRunnable() {
                    int t = 0;
                    @Override public void run() {
                        if (t >= it.duration) { cancel(); return; }
                        frame(it, loc, t++);
                    }
                }.runTaskTimer(pl, 0L, 1L);
                break;
            default: spawn(it, loc, it.count, it.spread, it.speed, 0);
        }
    }

    // ------------------------------------------------------------------ hooks
    /** A kill: the killer's Final Kill effect (or Kill effect) plays where the victim died. */
    public void onKill(Player killer, Player victim, boolean fin) {
        UUID u = killer.getUniqueId();
        Item it = fin ? selected(u, "final") : null;
        if (it == null) it = selected(u, "kill");
        if (it != null) play(it, victim.getLocation());
    }

    /** The victim's Death effect. */
    public void onDeath(Player victim) {
        Item it = selected(victim.getUniqueId(), "death");
        if (it != null) play(it, victim.getLocation());
    }

    /** Winners celebrate with their Victory effect while the end countdown runs. */
    public void onWin(final Arena a, final Player p) {
        final Item it = selected(p.getUniqueId(), "victory");
        if (it == null) return;
        final int max = Math.max(60, pl.getConfig().getInt("end-countdown", 10) * 20 - 10);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= max || !p.isOnline() || a.state != Arena.State.ENDING) { cancel(); return; }
                Location at = p.getLocation();
                if (it.animated()) frame(it, at.clone().add(0, it.yoff, 0), t);
                else if (t % it.interval == 0) {
                    if (it.type.equals("firework") || it.type.equals("lightning")) {
                        ThreadLocalRandom r = ThreadLocalRandom.current();
                        at = at.clone().add((r.nextDouble() - 0.5) * 8, 0, (r.nextDouble() - 0.5) * 8);
                    }
                    play(it, at);
                }
                t += 2;
            }
        }.runTaskTimer(pl, 10L, 2L);
    }

    /** Trails: a repeating task that draws the equipped trail behind every moving player inside a running game. */
    public void start() {
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                tick += 3;
                if (!enabled) return;
                PotionEffectType inv = U.pot("invisibility");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    Arena a = pl.mgr.of(p);
                    if (a == null || a.state != Arena.State.PLAYING) continue;
                    Arena.PD d = a.players.get(p.getUniqueId());
                    if (d == null || !d.alive || d.respawning) continue;
                    Item it = selected(p.getUniqueId(), "trail");
                    if (it == null) continue;
                    Location now = p.getLocation();
                    Location prev = last.put(p.getUniqueId(), now);
                    if (prev == null || prev.getWorld() != now.getWorld() || prev.distanceSquared(now) < 0.0025) continue;
                    if (inv != null && p.hasPotionEffect(inv)) continue;
                    spawn(it, now.clone().add(0, it.yoff, 0), it.count, it.spread, it.speed, tick);
                }
            }
        }.runTaskTimer(pl, 20L, 3L);
    }

    public void forget(UUID u) { last.remove(u); }

    /** Projectile effects: arrows, snowballs, eggs, fireballs and pearls thrown by a player with an equipped effect. */
    public void onLaunch(final Projectile pr) {
        if (!enabled || !(pr.getShooter() instanceof Player)) return;
        Player p = (Player) pr.getShooter();
        Arena a = pl.mgr.of(p);
        if (a == null || a.state != Arena.State.PLAYING) return;
        final Item it = selected(p.getUniqueId(), "projectile");
        if (it == null) return;
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!pr.isValid() || pr.isDead() || t++ > 200 || pr.getVelocity().lengthSquared() < 0.001) { cancel(); return; }
                spawn(it, pr.getLocation(), it.count, it.spread, it.speed, t);
            }
        }.runTaskTimer(pl, 1L, 1L);
    }

    // ------------------------------------------------------------------ bed designs + shop NPC skins
    private Item teamPick(Arena a, Arena.BT t, String cat) {
        if (!enabled) return null;
        for (Map.Entry<UUID, Arena.PD> e : a.players.entrySet()) {
            if (e.getValue().team != t.color) continue;
            Item it = selected(e.getKey(), cat);
            if (it != null) return it;
        }
        return null;
    }

    /** Game start: every team whose members equipped a bed design gets it on its bed. */
    public void applyBeds(final Arena a) {
        for (final Arena.BT t : a.teams.values()) {
            if (t.bed == null || !t.bedAlive) continue;
            final Item it = teamPick(a, t, "bed");
            if (it == null) continue;
            try {
                final Location base = a.L(t.bed).getBlock().getLocation().add(0.5, 0, 0.5);
                final ArmorStand st = (ArmorStand) a.world.spawnEntity(base.clone().add(0, 0.8, 0), EntityType.ARMOR_STAND);
                st.setVisible(false);
                st.setGravity(false);
                st.setMarker(true);
                st.setSmall(true);
                st.setInvulnerable(true);
                Material dm = mat(it.decor, null);
                if (dm != null && st.getEquipment() != null) st.getEquipment().setHelmet(new ItemStack(dm));
                a.spawned.add(st);
                new BukkitRunnable() {
                    int tk = 0;
                    @Override public void run() {
                        if (!st.isValid() || !t.bedAlive || a.state == Arena.State.WAITING || a.state == Arena.State.STARTING) {
                            st.remove();
                            cancel();
                            return;
                        }
                        tk += 2;
                        st.setHeadPose(new EulerAngle(0, tk * 0.12, 0));
                        if (tk % 4 == 0 && !it.particles.isEmpty()) spawn(it, base.clone().add(0, 1.3, 0), it.count, it.spread, it.speed, tk);
                    }
                }.runTaskTimer(pl, 2L, 2L);
            } catch (Throwable ex) { pl.getLogger().warning("bed design failed: " + ex); }
        }
    }

    public Item npcFor(Arena a, Arena.BT t) { return t == null ? null : teamPick(a, t, "npc"); }

    private static Object field(String cls, String name) {
        try { return Class.forName(cls).getField(name.toUpperCase()).get(null); } catch (Throwable t) { return null; }
    }

    /** Spawns a shop / upgrade NPC: a villager by default, or the equipped NPC skin. */
    public Entity spawnNpc(Location l, Item it) {
        World w = l.getWorld();
        EntityType et = EntityType.VILLAGER;
        if (it != null && !it.entity.isEmpty()) {
            try { et = EntityType.valueOf(it.entity.toUpperCase()); } catch (Throwable ignored) {
                try { et = EntityType.valueOf(it.entity.equalsIgnoreCase("SNOWMAN") ? "SNOW_GOLEM" : "VILLAGER"); } catch (Throwable ignored2) { et = EntityType.VILLAGER; }
            }
        }
        Entity e;
        try { e = w.spawnEntity(l, et); } catch (Throwable ex) { e = w.spawnEntity(l, EntityType.VILLAGER); }
        if (!(e instanceof LivingEntity)) { e.remove(); e = w.spawnEntity(l, EntityType.VILLAGER); }
        if (e instanceof Villager && it != null) {
            Villager v = (Villager) e;
            try {
                Object pr = field("org.bukkit.entity.Villager$Profession", it.profession);
                if (pr != null) v.setProfession((Villager.Profession) pr);
                Object ty = field("org.bukkit.entity.Villager$Type", it.villagerType);
                if (ty != null) v.setVillagerType((Villager.Type) ty);
            } catch (Throwable ignored) { /* keep default look */ }
        }
        return e;
    }
}
