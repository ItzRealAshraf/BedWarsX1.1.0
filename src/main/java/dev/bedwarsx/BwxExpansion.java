/*
 * BedWarsX - Copyright (c) 2026 ASHRAF ELHENAWY (ItzRealAshraf). All rights reserved.
 * Copying, re-selling, re-branding or removing the credits is not allowed.
 */
package dev.bedwarsx;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

/** PlaceholderAPI: %bwx_wins% %bwx_kills% %bwx_finals% %bwx_beds% %bwx_deaths% %bwx_games% %bwx_losses% %bwx_xp% %bwx_level% %bwx_streak% %bwx_beststreak% %bwx_bestscore% %bwx_bestkills% %bwx_progress% %bwx_progressbar% %bwx_xpneeded% %bwx_kd% %bwx_wl%
 *  %bwx_arena% %bwx_mode% %bwx_state% %bwx_players% %bwx_maxplayers% %bwx_team% */
public class BwxExpansion extends PlaceholderExpansion {
    private final BedWarsX pl;

    public BwxExpansion(BedWarsX pl) { this.pl = pl; }

    @Override public String getIdentifier() { return "bwx"; }
    @Override public String getAuthor() { return "BedWarsX"; }
    @Override public String getVersion() { return pl.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer op, String id) {
        if (op == null) return "";
        UUID u = op.getUniqueId();
        String k = id.toLowerCase();
        switch (k) {
            case "wins": case "losses": case "kills": case "finals": case "deaths": case "beds": case "games": case "xp":
            case "coins":
            case "streak": case "beststreak": case "bestscore": case "bestkills": case "bestfinals": case "bestbeds":
                return String.valueOf(pl.stats.get(u, k));
            case "level": return String.valueOf(pl.stats.level(u));
            case "achievements": return String.valueOf(pl.ach == null ? 0 : pl.ach.count(u));
            case "achievements_total": return String.valueOf(pl.ach == null ? 0 : pl.ach.total());
            case "weekly_missions": return String.valueOf(pl.ach == null ? 0 : pl.ach.weeklyCount(u));
            case "weekly_missions_total": return String.valueOf(pl.ach == null ? 0 : pl.ach.weeklyTotal());
            case "progress": return String.valueOf(pl.stats.percent(u));
            case "progressbar": return U.c(pl.stats.bar(u, 10));
            case "xpneeded": return String.valueOf(pl.stats.xpNeeded(u) - pl.stats.xpInto(u));
            case "kd": return pl.stats.ratio(pl.stats.get(u, "kills"), pl.stats.get(u, "deaths"));
            case "wl": return pl.stats.ratio(pl.stats.get(u, "wins"), pl.stats.get(u, "losses"));
            default: break;
        }
        Player p = op.getPlayer();
        Arena a = p == null ? null : pl.mgr.of(p);
        if (a == null) return "";
        Arena.PD d = a.players.get(u);
        switch (k) {
            case "arena": return a.name;
            case "mode": return a.mode();
            case "state": return a.state.name();
            case "players": return String.valueOf(a.players.size());
            case "maxplayers": return String.valueOf(a.maxPlayers());
            case "team": return d == null || d.team == null ? "" : d.team.display;
            default: return null;
        }
    }
}
