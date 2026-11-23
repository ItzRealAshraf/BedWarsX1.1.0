/*
 * BedWarsX - Copyright (c) 2026 ASHRAF ELHENAWY (ItzRealAshraf). All rights reserved.
 * Copying, re-selling, re-branding or removing the credits is not allowed.
 */
package dev.bedwarsx;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PartyManager {
    private final BedWarsX pl;
    private final Map<UUID, Party> byMember = new HashMap<>();
    private final Map<UUID, Party> invites = new HashMap<>();
    private final Map<UUID, Long> inviteExp = new HashMap<>();

    public PartyManager(BedWarsX pl) { this.pl = pl; }

    public Party of(Player p) { return byMember.get(p.getUniqueId()); }

    /** Whether the player has a currently valid pending invitation. */
    public boolean hasInvite(Player p) {
        Party pt = invites.get(p.getUniqueId());
        Long exp = inviteExp.get(p.getUniqueId());
        return pt != null && exp != null && exp >= System.currentTimeMillis() && byMember.get(pt.leader) == pt;
    }

    private void send(UUID u, String key, String... kv) {
        Player p = Bukkit.getPlayer(u);
        if (p != null) p.sendMessage(Msg.m(key, kv));
    }

    private void bc(Party pt, String key, String... kv) {
        for (UUID u : new ArrayList<>(pt.members)) send(u, key, kv);
    }

    private String nameOf(UUID u) {
        String n = Bukkit.getOfflinePlayer(u).getName();
        return n == null ? "?" : n;
    }

    private int max() { return pl.getConfig().getInt("party.max-size", 4); }

    public boolean create(Player p) {
        if (of(p) != null) { p.sendMessage(Msg.m("party-already")); return false; }
        Party pt = new Party();
        pt.leader = p.getUniqueId();
        pt.members.add(pt.leader);
        byMember.put(pt.leader, pt);
        p.sendMessage(Msg.m("party-created"));
        return true;
    }

    public void invite(Player from, Player to) {
        if (from.equals(to)) { from.sendMessage(Msg.m("party-self")); return; }
        if (of(to) != null) { from.sendMessage(Msg.m("party-target-in-party", "%player%", to.getName())); return; }
        Party pt = of(from);
        if (pt == null) {
            create(from);
            pt = of(from);
        } else if (!pt.leader.equals(from.getUniqueId())) {
            from.sendMessage(Msg.m("party-not-leader"));
            return;
        }
        if (pt.size() >= max()) { from.sendMessage(Msg.m("party-full")); return; }
        invites.put(to.getUniqueId(), pt);
        inviteExp.put(to.getUniqueId(), System.currentTimeMillis() + 60000L);
        from.sendMessage(Msg.m("party-invite-sent", "%player%", to.getName()));
        to.sendMessage(Msg.m("party-invite-received", "%player%", from.getName()));
        net.md_5.bungee.api.chat.TextComponent accept = new net.md_5.bungee.api.chat.TextComponent("[ACCEPT]");
        accept.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        accept.setBold(true);
        accept.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/bw party accept"));
        accept.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                new net.md_5.bungee.api.chat.ComponentBuilder("Click to join the party").create()));
        to.spigot().sendMessage(accept);
    }

    public void accept(Player p) {
        Party pt = invites.get(p.getUniqueId());
        Long exp = inviteExp.get(p.getUniqueId());
        if (pt == null || exp == null || exp < System.currentTimeMillis() || byMember.get(pt.leader) != pt) {
            p.sendMessage(Msg.m("party-no-invite"));
            return;
        }
        if (of(p) != null) { p.sendMessage(Msg.m("party-already")); return; }
        if (pt.size() >= max()) { p.sendMessage(Msg.m("party-full")); return; }
        invites.remove(p.getUniqueId());
        inviteExp.remove(p.getUniqueId());
        pt.members.add(p.getUniqueId());
        byMember.put(p.getUniqueId(), pt);
        bc(pt, "party-joined", "%player%", p.getName());
    }

    /** Leave (or disconnect). Handles leader transfer / auto-disband. */
    public void leave(UUID u) {
        invites.remove(u);
        inviteExp.remove(u);
        Party pt = byMember.remove(u);
        if (pt == null) return;
        pt.members.remove(u);
        send(u, "party-you-left");
        if (pt.members.isEmpty()) return;
        bc(pt, "party-left", "%player%", nameOf(u));
        if (pt.leader.equals(u)) {
            pt.leader = pt.members.iterator().next();
            bc(pt, "party-new-leader", "%player%", nameOf(pt.leader));
        }
    }

    public void kick(Player leader, String name) {
        Party pt = of(leader);
        if (pt == null) { leader.sendMessage(Msg.m("party-none")); return; }
        if (!pt.leader.equals(leader.getUniqueId())) { leader.sendMessage(Msg.m("party-not-leader")); return; }
        for (UUID u : new ArrayList<>(pt.members)) {
            if (u.equals(leader.getUniqueId()) || !nameOf(u).equalsIgnoreCase(name)) continue;
            String targetName = nameOf(u);
            bc(pt, "party-kicked", "%player%", targetName);
            pt.members.remove(u);
            byMember.remove(u);
            invites.remove(u);
            inviteExp.remove(u);
            return;
        }
        leader.sendMessage(Msg.m("party-not-member"));
    }

    public void disband(Player leader) {
        Party pt = of(leader);
        if (pt == null) { leader.sendMessage(Msg.m("party-none")); return; }
        if (!pt.leader.equals(leader.getUniqueId())) { leader.sendMessage(Msg.m("party-not-leader")); return; }
        for (UUID u : new ArrayList<>(pt.members)) {
            byMember.remove(u);
            send(u, "party-disbanded");
        }
        pt.members.clear();
    }

    public void list(Player p) {
        Party pt = of(p);
        if (pt == null) { p.sendMessage(Msg.m("party-none")); return; }
        p.sendMessage(Msg.m("party-list-header"));
        for (UUID u : pt.members)
            p.sendMessage(Msg.m("party-list-entry", "%player%", nameOf(u), "%role%", u.equals(pt.leader) ? Msg.m("party-role-leader") : ""));
    }

    /** Join an arena (whole party if the player leads one). */
    public boolean tryJoin(Player p, Arena a) {
        Party pt = of(p);
        if (pt == null) return a.join(p);
        if (!pt.leader.equals(p.getUniqueId())) { p.sendMessage(Msg.m("party-leader-only")); return false; }
        if (pt.size() > a.teamSize) { p.sendMessage(Msg.m("party-too-big")); return false; }
        if (a.players.size() + pt.size() > a.maxPlayers()) { p.sendMessage(Msg.m("party-no-room")); return false; }
        List<Player> group = new ArrayList<>();
        for (UUID u : pt.members) {
            Player m = Bukkit.getPlayer(u);
            if (m == null) continue;
            if (!m.equals(p) && pl.mgr.of(m) != null) { p.sendMessage(Msg.m("party-busy", "%player%", m.getName())); return false; }
            group.add(m);
        }
        if (!a.join(p)) return false;
        for (Player m : group) if (!m.equals(p)) a.join(m);
        a.groupTeam(group);
        return true;
    }
}
