package cz.forgebound;

import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /forge locate &lt;biome&gt;: where the nearest of the plugin's own biome pairs is. Vanilla /locate biome can't find
 * them - they're painted onto chunks after generation, the world's biome source doesn't know about them.
 * The search works on the noise, so it also finds places nobody has generated yet (only those turn into the biome).
 */
final class BiomeLocator {

    static final List<String> NAMES = List.of("ember_crags", "glassed_plains", "ashen_forest", "shattered_peaks");

    private BiomeLocator() {
    }

    static void locate(Forgebound plugin, CommandSender sender, String name) {
        World w = sender instanceof Player p && p.getWorld().getEnvironment() == World.Environment.NORMAL ? p.getWorld()
                : Bukkit.getWorlds().getFirst();
        long seed = w.getSeed();
        // the noises of the biomes that win over the one asked for (in this order of priority)
        List<ScorchedNoise> above = new ArrayList<>();
        if (plugin.scorched() != null) {
            above.add(plugin.scorched().noise(seed));
        }
        ScorchedNoise target = null;
        String label = null;
        boolean on = false;
        switch (name) {
            case "ember_crags" -> {
                on = plugin.emberCrags() != null && plugin.emberCrags().on();
                target = on ? plugin.emberCrags().noise(seed) : null;
                label = "&c🔥 Ember Crags &7(Molten Depths below)";
            }
            case "glassed_plains" -> {
                addIfOn(above, plugin.emberCrags() != null && plugin.emberCrags().on() ? plugin.emberCrags().noise(seed) : null);
                on = plugin.glassedPlains() != null && plugin.glassedPlains().on();
                target = on ? plugin.glassedPlains().noise(seed) : null;
                label = "&d✦ Glassed Plains &7(Crystal Hollows below)";
            }
            case "ashen_forest" -> {
                addIfOn(above, plugin.emberCrags() != null && plugin.emberCrags().on() ? plugin.emberCrags().noise(seed) : null);
                addIfOn(above, plugin.glassedPlains() != null && plugin.glassedPlains().on() ? plugin.glassedPlains().noise(seed) : null);
                on = plugin.ashenForest() != null && plugin.ashenForest().on();
                target = on ? plugin.ashenForest().noise(seed) : null;
                label = "&7🌲 Ashen Forest &7(Hollow Roots below)";
            }
            case "shattered_peaks" -> {
                addIfOn(above, plugin.emberCrags() != null && plugin.emberCrags().on() ? plugin.emberCrags().noise(seed) : null);
                addIfOn(above, plugin.glassedPlains() != null && plugin.glassedPlains().on() ? plugin.glassedPlains().noise(seed) : null);
                addIfOn(above, plugin.ashenForest() != null && plugin.ashenForest().on() ? plugin.ashenForest().noise(seed) : null);
                on = plugin.shatteredPeaks() != null && plugin.shatteredPeaks().on();
                target = on ? plugin.shatteredPeaks().noise(seed) : null;
                label = "&b❄ Shattered Peaks &7(Frozen Vaults below)";
            }
            default -> {
                sender.sendMessage(ItemManager.color("&7Unknown biome. Try: &f" + String.join(", ", NAMES)));
                return;
            }
        }
        if (target == null) {
            sender.sendMessage(ItemManager.color("&7This biome is turned off (or the Forgebound datapack biomes are missing)."));
            return;
        }
        Location at = sender instanceof Player p && p.getWorld().equals(w) ? p.getLocation() : new Location(w, 0, 0, 0);
        int[] found = target.nearest(at.getBlockX(), at.getBlockZ(), 30_000, (x, z) -> {
            for (ScorchedNoise n : above) {
                if (n.inside(x, z)) {
                    return true;
                }
            }
            return false;
        });
        if (found == null) {
            sender.sendMessage(ItemManager.color("&7None within 30 000 blocks."));
            return;
        }
        int dist = (int) Math.hypot(found[0] - at.getBlockX(), found[1] - at.getBlockZ());
        sender.sendMessage(ItemManager.color(label + " &7at &f" + found[0] + " ~ " + found[1] + " &8(" + dist + " blocks) &e[tp]")
                .clickEvent(ClickEvent.suggestCommand("/tp @s " + found[0] + " 200 " + found[1])));
        sender.sendMessage(ItemManager.color("&8Only chunks generated from now on get the biome; explored land stays as it is."));
    }

    private static void addIfOn(List<ScorchedNoise> list, ScorchedNoise n) {
        if (n != null) {
            list.add(n);
        }
    }
}
