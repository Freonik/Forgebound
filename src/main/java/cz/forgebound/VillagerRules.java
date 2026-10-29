package cz.forgebound;

import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.entity.VillagerReplenishTradeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Villagers (and wandering traders) never sell Mending (combat.no-villager-mending): a Mending book becomes a book
 * of another enchantment for the same price. Trades a villager already has are fixed the next time someone
 * opens it, so old librarians lose it too.
 */
public final class VillagerRules implements Listener {

    private final Forgebound plugin;

    public VillagerRules(Forgebound plugin) {
        this.plugin = plugin;
    }

    private boolean on() {
        return plugin.getConfig().getBoolean("combat.no-villager-mending", true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAcquire(VillagerAcquireTradeEvent event) {
        if (on() && sellsMending(event.getRecipe())) {
            event.setRecipe(replaced(event.getRecipe()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onReplenish(VillagerReplenishTradeEvent event) {
        if (on() && sellsMending(event.getRecipe())) {
            event.setRecipe(replaced(event.getRecipe()));
        }
    }

    /** Before the trade window opens: swap out Mending trades the villager got before this rule existed. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onOpen(PlayerInteractEntityEvent event) {
        if (!on() || !(event.getRightClicked() instanceof AbstractVillager villager)) {
            return;
        }
        List<MerchantRecipe> recipes = new ArrayList<>(villager.getRecipes());
        boolean changed = false;
        for (int i = 0; i < recipes.size(); i++) {
            if (sellsMending(recipes.get(i))) {
                recipes.set(i, replaced(recipes.get(i)));
                changed = true;
            }
        }
        if (changed) {
            villager.setRecipes(recipes);
        }
    }

    static boolean sellsMending(MerchantRecipe recipe) {
        ItemStack result = recipe.getResult();
        if (result == null || !result.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = result.getItemMeta();
        return meta instanceof EnchantmentStorageMeta book && book.hasStoredEnchant(Enchantment.MENDING)
                || meta.hasEnchant(Enchantment.MENDING);
    }

    /** The same trade (price, uses, xp), selling another enchantment instead of Mending. */
    private static MerchantRecipe replaced(MerchantRecipe old) {
        ItemStack result = old.getResult().clone();
        ItemMeta meta = result.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta book) {
            book.removeStoredEnchant(Enchantment.MENDING);
            if (!book.hasStoredEnchants()) {
                Enchantment e = randomBookEnchant();
                book.addStoredEnchant(e, e.getMaxLevel(), false);
            }
        } else {
            meta.removeEnchant(Enchantment.MENDING);
        }
        result.setItemMeta(meta);
        MerchantRecipe r = new MerchantRecipe(result, old.getUses(), old.getMaxUses(), old.hasExperienceReward(),
                old.getVillagerExperience(), old.getPriceMultiplier(), old.getDemand(), old.getSpecialPrice());
        r.setIngredients(old.getIngredients());
        return r;
    }

    /** A random enchantment a librarian could sell (no Mending, no curses, nothing treasure-only). */
    private static Enchantment randomBookEnchant() {
        List<Enchantment> pool = new ArrayList<>();
        for (Enchantment e : Registry.ENCHANTMENT) {
            if (e.equals(Enchantment.MENDING) || e.isCursed() || e.isTreasure() || !e.isTradeable()) {
                continue;
            }
            pool.add(e);
        }
        return pool.isEmpty() ? Enchantment.UNBREAKING : pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }
}
