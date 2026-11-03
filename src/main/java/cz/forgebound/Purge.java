package cz.forgebound;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Admin clean-up after testing:
 * /forge legend purge <name> [confirm]   - wipes a player's leaderboard data (records, bounty, legend stats) under every UUID
 *                                   that name has had (online-mode and offline-mode UUIDs differ)
 * /forge legend release all-admin [confirm] - releases every legend that was given with /forge legend give and never forged
 *                                   (their copies crumble, like with /forge legend release)
 */
final class Purge {

    private Purge() {
    }

    static Set<UUID> uuidsOf(Forgebound plugin, String name) {
        Set<UUID> set = new LinkedHashSet<>();
        set.add(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)));   // offline-mode UUID
        for (OfflinePlayer o : Bukkit.getOfflinePlayers()) {
            if (o.getName() != null && o.getName().equalsIgnoreCase(name)) {
                set.add(o.getUniqueId());
            }
        }
        if (plugin.top() != null) {
            set.addAll(plugin.top().uuidsNamed(name));
        }
        return set;
    }

    static void purge(Forgebound plugin, CommandSender sender, String name, boolean confirm) {
        Set<UUID> ids = uuidsOf(plugin, name);
        if (!confirm) {
            sender.sendMessage(ItemManager.color("&eThis wipes &f" + name + "&e's leaderboard data (records, stars, bounty, legend kills and damage) "
                    + "under these UUIDs:"));
            ids.forEach(u -> sender.sendMessage(ItemManager.color("&7 - &f" + u)));
            sender.sendMessage(ItemManager.color("&7Items, legends and the team stay. Run &e/forge legend purge " + name + " confirm"));
            return;
        }
        int records = plugin.top() != null ? plugin.top().purge(ids) : 0;
        int bounty = plugin.bounty() != null ? plugin.bounty().purge(ids) : 0;
        int stats = plugin.legends() != null ? plugin.legends().purgeStats(ids) : 0;
        plugin.getLogger().info("[Purge] " + sender.getName() + " purged " + name + " " + ids + ": records " + records
                + ", bounty " + bounty + ", legend stats " + stats);
        sender.sendMessage(ItemManager.color("&aPurged &f" + name + "&a: records " + records + ", bounty " + bounty
                + ", legend stats " + stats + " &7(UUIDs with data)."));
    }

    static void releaseAdminGiven(Forgebound plugin, CommandSender sender, boolean confirm) {
        if (plugin.legends() == null) {
            return;
        }
        Map<String, String> given = plugin.legends().adminGiven();
        if (given.isEmpty()) {
            sender.sendMessage(ItemManager.color("&7No legends given by an admin."));
            return;
        }
        if (!confirm) {
            sender.sendMessage(ItemManager.color("&eThese " + given.size() + " legends were given by an admin (never forged):"));
            given.forEach((ref, holder) -> sender.sendMessage(ItemManager.color("&7 - &f" + ref + " &8(" + holder + ")")));
            sender.sendMessage(ItemManager.color("&cTheir copies crumble. &7Run &e/forge legend release all-admin confirm"));
            return;
        }
        given.keySet().forEach(ref -> plugin.legends().revoke(ref));
        plugin.getLogger().info("[Purge] " + sender.getName() + " released " + given.size() + " admin-given legends: " + given.keySet());
        sender.sendMessage(ItemManager.color("&aReleased " + given.size() + " admin-given legends."));
    }
}
