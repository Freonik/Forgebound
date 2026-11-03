package cz.forgebound;

import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Mythic weapons: the prizes of the big events. Far stronger than legends, but they fade after a few days
 * (mythic.days) - then they crumble to dust wherever they are found (inventory, ender chest, chests).
 */
public final class MythicManager implements Listener {

    private final Forgebound plugin;
    private final ItemManager items;
    private final NamespacedKey expiresKey;

    public MythicManager(Forgebound plugin, ItemManager items) {
        this.plugin = plugin;
        this.items = items;
        this.expiresKey = Forgebound.key("mythic_expires");
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    sweep(p.getInventory(), p);
                }
            }
        }.runTaskTimer(plugin, 200L, 400L);
    }

    /** A fresh mythic weapon, fading after the configured number of days. */
    public ItemStack create(ItemManager.Entry entry) {
        ItemStack stack = entry.item.clone();
        double days = plugin.getConfig().getDouble("big-events.mythic-days", 7);
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(expiresKey, PersistentDataType.LONG,
                System.currentTimeMillis() + (long) (days * 86_400_000L));
        stack.setItemMeta(meta);
        refresh(stack, entry);
        return stack;
    }

    /** When the mythic weapon fades (ms), or 0 if it has no date yet. */
    public long expiresAt(ItemStack stack) {
        Long at = stack.getPersistentDataContainer().get(expiresKey, PersistentDataType.LONG);
        return at == null ? 0 : at;
    }

    /** Lore = the weapon's own lore + how long it still lasts. */
    private void refresh(ItemStack stack, ItemManager.Entry entry) {
        long left = expiresAt(stack) - System.currentTimeMillis();
        ItemMeta meta = stack.getItemMeta();
        List<Component> lore = entry.item.getItemMeta().hasLore() ? new ArrayList<>(entry.item.getItemMeta().lore()) : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(ItemManager.color("&5&l✦ MYTHIC &8- &7fades in &d" + format(left)));
        meta.lore(lore);
        stack.setItemMeta(meta);
    }

    static String format(long ms) {
        long minutes = Math.max(0, ms / 60_000L);
        long d = minutes / 1440;
        long h = (minutes % 1440) / 60;
        long m = minutes % 60;
        return d > 0 ? d + "d " + h + "h" : h > 0 ? h + "h " + m + "m" : m + "m";
    }

    /** Crumbles faded mythic weapons and keeps the "fades in" line up to date. */
    private void sweep(Inventory inventory, Player viewer) {
        ItemStack[] contents = inventory.getContents();
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            ItemManager.Entry entry = items.weaponOf(stack);
            if (entry == null || !entry.mythic) {
                continue;
            }
            long at = expiresAt(stack);
            if (at == 0) {                         // made some other way (e.g. /forge legend give): the clock starts now
                contents[i] = create(entry);
                changed = true;
            } else if (at <= now) {
                contents[i] = null;
                changed = true;
                plugin.getLogger().info("[BigEvent] A mythic " + entry.ref() + " faded"
                        + (viewer != null ? " (" + viewer.getName() + ")" : ""));
                if (viewer != null) {
                    viewer.sendMessage(plugin.msg("mythic-faded", "&5✦ {item} &7has faded - mythic power never lasts.",
                            "item", ItemManager.nameOf(entry.item)));
                    viewer.playSound(viewer.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 0.5f);
                    viewer.getWorld().spawnParticle(Particle.REVERSE_PORTAL, viewer.getLocation().add(0, 1, 0), 60, 0.4, 0.6, 0.4, 0.05);
                }
            } else {
                ItemStack before = stack.clone();
                refresh(stack, entry);
                changed |= !before.equals(stack);
            }
        }
        if (changed) {
            inventory.setContents(contents);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        sweep(event.getPlayer().getInventory(), event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player p) {
            sweep(event.getInventory(), p);
        }
    }
}
