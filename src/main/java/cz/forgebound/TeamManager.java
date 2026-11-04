package cz.forgebound;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Who is on whose team. Two sources, both optional:
 * - vanilla teams (/team) on the main scoreboard
 * - any team/clan plugin through a PlaceholderAPI placeholder (e.g. %betterteams_name%): same value = same team
 */
public final class TeamManager {

    private final Forgebound plugin;
    /** player -> (placeholder value, when) - placeholders are asked a lot, so they're cached for a second */
    private final Map<UUID, String> cached = new HashMap<>();
    private final Map<UUID, Long> cachedAt = new HashMap<>();

    public TeamManager(Forgebound plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("teams.enabled", true);
    }

    /** Are these two different players teammates? */
    public boolean allies(Player a, Player b) {
        if (!enabled() || a == null || b == null || a.equals(b) || plugin.teamsCorrupted()) {
            return false;                                        // corrupted teams: nobody is anybody's ally
        }
        // Forgebound Teams (or another team plugin) tells who is on the same side - teammates and allies
        for (org.bukkit.plugin.RegisteredServiceProvider<java.util.function.BiPredicate> reg
                : Bukkit.getServicesManager().getRegistrations(java.util.function.BiPredicate.class)) {
            if (reg.getPlugin().getName().equals("ForgeboundTeams") && reg.getPlugin().isEnabled()) {
                try {
                    @SuppressWarnings("unchecked")
                    java.util.function.BiPredicate<Player, Player> side = reg.getProvider();
                    if (side.test(a, b)) {
                        return true;
                    }
                } catch (RuntimeException ignored) {
                    // a different BiPredicate - not ours
                }
            }
        }
        if (plugin.getConfig().getBoolean("teams.vanilla", true)) {
            Team ta = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(a.getName());
            Team tb = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(b.getName());
            if (ta != null && ta.equals(tb)) {
                return true;
            }
        }
        String placeholder = plugin.getConfig().getString("teams.placeholder", "");
        if (placeholder != null && !placeholder.isBlank() && plugin.papi()) {
            String va = teamOf(a, placeholder);
            String vb = teamOf(b, placeholder);
            return va != null && va.equals(vb);
        }
        return false;
    }

    /** Forgebound Teams: did these two teams just end an alliance? Then a kill between them pays no bounty. */
    public boolean noBounty(Player killer, Player victim) {
        if (!enabled() || killer == null || victim == null) {
            return false;
        }
        for (org.bukkit.plugin.RegisteredServiceProvider<java.util.function.BiFunction> reg
                : Bukkit.getServicesManager().getRegistrations(java.util.function.BiFunction.class)) {
            if (reg.getPlugin().getName().equals("ForgeboundTeams") && reg.getPlugin().isEnabled()) {
                try {
                    @SuppressWarnings("unchecked")
                    java.util.function.BiFunction<Player, Player, Boolean> f = reg.getProvider();
                    if (Boolean.TRUE.equals(f.apply(killer, victim))) {
                        return true;
                    }
                } catch (RuntimeException ignored) {
                    // a different BiFunction - not ours
                }
            }
        }
        return false;
    }

    /** The placeholder's value for this player, or null if they have no team. */
    private String teamOf(Player p, String placeholder) {
        long now = System.currentTimeMillis();
        Long at = cachedAt.get(p.getUniqueId());
        String value;
        if (at != null && now - at < 1000) {
            value = cached.get(p.getUniqueId());
        } else {
            value = PapiExpansion.apply(p, placeholder);
            value = value == null ? "" : value.trim();
            if (value.equals(placeholder)) {
                value = "";                                      // the placeholder doesn't exist
            }
            cached.put(p.getUniqueId(), value);
            cachedAt.put(p.getUniqueId(), now);
        }
        List<String> none = plugin.getConfig().getStringList("teams.no-team-values");
        String lower = value.toLowerCase(Locale.ROOT);
        if (value.isEmpty() || none.stream().anyMatch(n -> n.toLowerCase(Locale.ROOT).equals(lower))) {
            return null;
        }
        return value;
    }

    /** Online teammates of a player. */
    public List<Player> teammates(Player p) {
        return Bukkit.getOnlinePlayers().stream().filter(o -> allies(p, o)).map(o -> (Player) o).toList();
    }

    public boolean friendlyFire() {
        return plugin.getConfig().getBoolean("teams.friendly-fire", false);
    }
}
