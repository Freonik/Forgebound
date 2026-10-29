package cz.forgebound;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry.EnchantmentCost;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.EnchantmentKeys;
import io.papermc.paper.registry.keys.tags.EnchantmentTagKeys;
import io.papermc.paper.registry.keys.tags.ItemTypeTagKeys;
import io.papermc.paper.registry.set.RegistrySet;
import io.papermc.paper.tag.PreFlattenTagRegistrar;
import io.papermc.paper.tag.TagEntry;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Runs before the worlds load (Paper plugin bootstrap). Adds Forgebound's own enchantments to the game:
 * they are real enchantments - in the enchanting table, on villager books and in loot, shown by the client
 * like vanilla ones. What they do is handled in {@link EnchantManager}.
 *
 * The enchantments are always registered (items that have them must stay valid); the config only decides
 * where they can be found. Changes need a restart.
 */
public final class ForgeboundBootstrap implements PluginBootstrap {

    public static final Key FORGE_TOUCH = Key.key("forgebound", "forge_touch");
    public static final Key BOUNTY_HUNTER = Key.key("forgebound", "bounty_hunter");

    @Override
    public void bootstrap(BootstrapContext context) {
        YamlConfiguration config = readConfig(context.getDataDirectory().resolve("config.yml"));
        TypedKey<Enchantment> touch = EnchantmentKeys.create(FORGE_TOUCH);
        TypedKey<Enchantment> hunter = EnchantmentKeys.create(BOUNTY_HUNTER);

        context.getLifecycleManager().registerEventHandler(RegistryEvents.ENCHANTMENT.compose(), event -> {
            event.registry().register(touch, b -> b
                    .description(Component.translatable("enchantment.forgebound.forge_touch", "Forge Touch"))
                    .supportedItems(event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_MINING_LOOT))
                    .weight(2)
                    .maxLevel(1)
                    .minimumCost(EnchantmentCost.of(15, 0))
                    .maximumCost(EnchantmentCost.of(65, 0))
                    .anvilCost(4)
                    .activeSlots(EquipmentSlotGroup.MAINHAND)
                    .exclusiveWith(RegistrySet.keySet(RegistryKey.ENCHANTMENT, EnchantmentKeys.SILK_TOUCH)));
            event.registry().register(hunter, b -> b
                    .description(Component.translatable("enchantment.forgebound.bounty_hunter", "Bounty Hunter"))
                    .supportedItems(event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_SHARP_WEAPON))
                    .weight(2)
                    .maxLevel(3)
                    .minimumCost(EnchantmentCost.of(10, 9))
                    .maximumCost(EnchantmentCost.of(40, 9))
                    .anvilCost(4)
                    .activeSlots(EquipmentSlotGroup.MAINHAND));
            CustomEnchants.register(event);
        });

        // the bundled datapack: the Scorched Lands biome and the ruin loot tables. Always loaded, so chunks that
        // already have the biome stay valid even when the generation is switched off in the config.
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY, event -> {
            try {
                java.net.URL url = ForgeboundBootstrap.class.getResource("/datapack");
                if (url != null) {
                    event.registrar().discoverPack(url.toURI(), "forgebound");
                }
            } catch (java.net.URISyntaxException | java.io.IOException ex) {
                context.getLogger().warn("Could not load the Forgebound datapack: " + ex.getMessage());
            }
        });

        context.getLifecycleManager().registerEventHandler(LifecycleEvents.TAGS.preFlatten(RegistryKey.ENCHANTMENT), event -> {
            PreFlattenTagRegistrar<Enchantment> tags = event.registrar();
            addTo(tags, config, "forge-touch", touch);
            addTo(tags, config, "bounty-hunter", hunter);
        });
    }

    /** Puts an enchantment into the vanilla tags that decide where it shows up. */
    private static void addTo(PreFlattenTagRegistrar<Enchantment> tags, YamlConfiguration config, String id,
                              TypedKey<Enchantment> key) {
        String path = "enchantments." + id + ".";
        Set<TagEntry<Enchantment>> entry = Set.of(TagEntry.valueEntry(key));
        if (config.getBoolean(path + "enchanting-table", true)) {
            tags.addToTag(EnchantmentTagKeys.NON_TREASURE, entry);
            tags.addToTag(EnchantmentTagKeys.IN_ENCHANTING_TABLE, entry);
        }
        if (config.getBoolean(path + "villagers", true)) {
            tags.addToTag(EnchantmentTagKeys.TRADEABLE, entry);
        }
        if (config.getBoolean(path + "loot", true)) {
            tags.addToTag(EnchantmentTagKeys.ON_RANDOM_LOOT, entry);
        }
    }

    /** The config isn't loaded this early, so it's read straight from the file (missing = defaults). */
    private static YamlConfiguration readConfig(Path file) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            if (Files.isRegularFile(file)) {
                config.loadFromString(Files.readString(file));
            }
        } catch (Exception ignored) {
            // broken config: the plugin will complain about it later, here we just use the defaults
        }
        return config;
    }

    /** Ids of the enchantments, e.g. for the Codex and tests. */
    public static List<Key> all() {
        List<Key> all = new ArrayList<>(List.of(FORGE_TOUCH, BOUNTY_HUNTER));
        for (CustomEnchants.Spec s : CustomEnchants.Spec.values()) {
            all.add(s.key());
        }
        return all;
    }
}
