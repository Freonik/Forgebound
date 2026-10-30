package cz.forgebound;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * PvP rules: combat tag (logging out in a fight kills you), the clash of legends (a legend kills a legend
 * carrier = half a star) and no crafting of vanilla maces.
 */
public final class PvpManager implements Listener {

    private final Forgebound plugin;
    private final ItemManager items;
    /** tagged player -> when the tag ends (ms) */
    private final Map<UUID, Long> tagged = new HashMap<>();
    /** tagged player -> who hit them last */
    private final Map<UUID, UUID> lastAttacker = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    /** "killer;victim" -> when that pair may give a duel star again (ms) */
    private final Map<String, Long> duelCooldown = new HashMap<>();

    public PvpManager(Forgebound plugin, ItemManager items) {
        this.plugin = plugin;
        this.items = items;
    }

    private boolean combatOn() {
        return plugin.getConfig().getBoolean("combat.enabled", true);
    }

    public void start() {
        if (plugin.getConfig().getBoolean("combat.disable-mace-crafting", true)) {
            Bukkit.removeRecipe(NamespacedKey.minecraft("mace"));
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 10L);
    }

    public void shutdown() {
        for (UUID id : new java.util.ArrayList<>(tagged.keySet())) {
            untag(id);
        }
    }

    // ================================================================ combat tag

    public boolean inCombat(Player player) {
        Long until = tagged.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    /** player -> when their mace may smash again (ms) */
    private final Map<UUID, Long> smashReady = new HashMap<>();

    /**
     * Mace smash cooldown (combat.mace-smash-cooldown): after a smash (a hit while falling) the next one within the
     * cooldown only does a normal hit's damage. The mace shows the cooldown like an ender pearl.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSmash(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p) || event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        ItemStack mace = p.getInventory().getItemInMainHand();
        double seconds = plugin.getConfig().getDouble("combat.mace-smash-cooldown", 10);
        if (mace.getType() != org.bukkit.Material.MACE || seconds <= 0 || p.getFallDistance() <= 1.5f) {
            return;
        }
        long now = System.currentTimeMillis();
        Long ready = smashReady.get(p.getUniqueId());
        if (ready != null && now < ready) {
            org.bukkit.attribute.AttributeInstance attack = p.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
            double normal = (attack != null ? attack.getValue() : 6) * 1.5;      // a critical hit at most
            if (event.getDamage() > normal) {
                event.setDamage(normal);
            }
            p.sendActionBar(ItemManager.color("&cMace smash recharging: &f" + ((ready - now) / 1000 + 1) + " s"));
            return;
        }
        smashReady.put(p.getUniqueId(), now + (long) (seconds * 1000));
        p.setCooldown(mace, (int) (seconds * 20));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!combatOn() || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (!(damager instanceof Player attacker) || attacker.equals(victim) || !fighting(attacker) || !fighting(victim)) {
            return;
        }
        if (plugin.teams() != null && plugin.teams().allies(attacker, victim)) {
            return;
        }
        tag(victim);
        tag(attacker);
        lastAttacker.put(victim.getUniqueId(), attacker.getUniqueId());
        lastAttacker.put(attacker.getUniqueId(), victim.getUniqueId());
    }

    private static boolean fighting(Player p) {
        return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
    }

    private void tag(Player player) {
        long seconds = Math.max(1, plugin.getConfig().getLong("combat.tag-seconds", 20));
        boolean fresh = !inCombat(player);
        tagged.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        if (fresh) {
            player.sendMessage(plugin.msg("combat-tagged",
                    "&c⚔ You are in combat! &7Logging out now kills you."));
            if (player.isGliding() && plugin.getConfig().getBoolean("combat.disable-elytra", true)) {
                player.setGliding(false);
            }
        }
        if (plugin.getConfig().getBoolean("combat.bossbar", true)) {
            BossBar bar = bars.computeIfAbsent(player.getUniqueId(), k -> {
                BossBar b = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
                player.showBossBar(b);
                return b;
            });
            update(bar, seconds * 1000L, seconds);
        }
    }

    private void update(BossBar bar, long leftMs, long seconds) {
        bar.progress((float) Math.max(0, Math.min(1, leftMs / (seconds * 1000.0))));
        bar.name(plugin.raw("combat-bar", "&c⚔ In combat &f{seconds}s", "seconds", (leftMs + 999) / 1000));
    }

    private void untag(UUID id) {
        tagged.remove(id);
        lastAttacker.remove(id);
        BossBar bar = bars.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (bar != null && p != null) {
            p.hideBossBar(bar);
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        long seconds = Math.max(1, plugin.getConfig().getLong("combat.tag-seconds", 20));
        for (Iterator<Map.Entry<UUID, Long>> it = tagged.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || e.getValue() <= now) {
                it.remove();
                lastAttacker.remove(e.getKey());
                BossBar bar = bars.remove(e.getKey());
                if (p != null) {
                    if (bar != null) {
                        p.hideBossBar(bar);
                    }
                    p.sendActionBar(plugin.raw("combat-over", "&a⚔ You are out of combat."));
                }
                continue;
            }
            BossBar bar = bars.get(e.getKey());
            if (bar != null) {
                update(bar, e.getValue() - now, seconds);
            }
        }
        if (now % 60_000 < 600) {
            duelCooldown.values().removeIf(t -> t < now);
        }
    }

    /** Logging out in a fight = death (the items drop, the last attacker gets the kill). Kicks and restarts don't count. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        boolean fled = combatOn() && inCombat(player) && !Bukkit.isStopping() && !player.isDead()
                && plugin.getConfig().getBoolean("combat.kill-on-logout", true)
                && (event.getReason() == PlayerQuitEvent.QuitReason.DISCONNECTED
                    || event.getReason() == PlayerQuitEvent.QuitReason.TIMED_OUT);
        UUID attackerId = lastAttacker.get(id);
        untag(id);
        if (!fled) {
            return;
        }
        Player attacker = attackerId == null ? null : Bukkit.getPlayer(attackerId);
        if (attacker != null) {
            player.setKiller(attacker);
        }
        plugin.getLogger().info("[Combat] " + player.getName() + " logged out in combat"
                + (attacker != null ? " with " + attacker.getName() : "") + " - killed");
        Bukkit.broadcast(plugin.msg("combat-logout", "&c☠ {player} &7ran from a fight and died.",
                "player", player.getName()));
        player.setHealth(0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.isGliding() && event.getEntity() instanceof Player player && combatOn() && inCombat(player)
                && plugin.getConfig().getBoolean("combat.disable-elytra", true)) {
            event.setCancelled(true);
            player.sendActionBar(plugin.raw("combat-no-elytra", "&cNo elytra in combat."));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!combatOn() || !inCombat(event.getPlayer()) || event.getPlayer().hasPermission("forgebound.admin")) {
            return;
        }
        String label = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        label = label.substring(label.indexOf(':') + 1);
        for (String blocked : plugin.getConfig().getStringList("combat.blocked-commands")) {
            if (label.equals(blocked.toLowerCase(Locale.ROOT).replace("/", ""))) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(plugin.msg("combat-no-command", "&cYou can't use that in combat."));
                return;
            }
        }
    }

    // ================================================================ clash of legends

    /**
     * Who killed this player. Vanilla credits the last player who hit them even when a mob (or the Doppelganger)
     * landed the killing blow; that doesn't count here. Falling or burning after a player's hit still does.
     */
    public static Player killerOf(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) {
            return null;
        }
        Entity cause = event.getDamageSource().getCausingEntity();
        if (cause != null && !(cause instanceof Player)) {
            return null;
        }
        // the Doppelganger hit them last and they then fell or burned: its kill, not the player's who hit them before
        Forgebound plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(Forgebound.class);
        if (cause == null && plugin.ghost() != null && plugin.ghost().hitRecently(victim, 3000)) {
            return null;
        }
        return killer;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        untag(victim.getUniqueId());
        Player killer = killerOf(event);
        if (killer == null || killer.equals(victim) || !plugin.getConfig().getBoolean("combat.duel.enabled", true)
                || (plugin.teams() != null && plugin.teams().allies(killer, victim))) {
            return;
        }
        ItemStack hand = killer.getInventory().getItemInMainHand();
        ItemManager.Entry weapon = items.weaponOf(hand);
        if (weapon == null || weapon.mythic || !carriesLegend(victim, event)) {
            return;
        }
        long now = System.currentTimeMillis();
        String pair = killer.getUniqueId() + ";" + victim.getUniqueId();
        Long until = duelCooldown.get(pair);
        if (until != null && until > now) {
            return;
        }
        duelCooldown.put(pair, now + plugin.getConfig().getLong("combat.duel.same-victim-minutes", 60) * 60_000L);
        plugin.award(killer, "clash_of_legends");
        if (plugin.top() != null) {
            plugin.top().add(killer, "clashes");
        }
        Bukkit.broadcast(plugin.msg("duel-won", "&6⚔ Clash of legends: &e{killer} &7beat the legend carrier &e{player}&7!",
                "killer", killer.getName(), "player", victim.getName()));
        StarManager stars = plugin.stars();
        if (stars == null || !stars.enabled()) {
            return;
        }
        int gain = (int) Math.round(plugin.getConfig().getDouble("combat.duel.stars", 0.5) * 2);
        int cap = (int) Math.round(plugin.getConfig().getDouble("combat.duel.max-stars", 4) * 2);
        int before = stars.halvesOf(hand);
        int after = Math.min(Math.max(before, cap), Stars.clamp(before + gain));
        if (gain <= 0 || after <= before) {
            return;
        }
        stars.setHalves(hand, weapon, after);
        killer.sendMessage(plugin.msg("duel-star", "&6✦ {item} &7grew stronger from the fight: {stars}",
                "item", ItemManager.nameOf(weapon.item), "stars", ItemManager.color(Stars.starText(after))));
        plugin.getLogger().info("[Stars] " + killer.getName() + " won a clash of legends against " + victim.getName()
                + ": " + weapon.ref() + " " + Stars.starCount(before) + " -> " + Stars.starCount(after) + "/5");
    }

    private boolean carriesLegend(Player victim, PlayerDeathEvent event) {
        for (ItemStack stack : event.getDrops()) {
            ItemManager.Entry e = items.weaponOf(stack);
            if (e != null && !e.mythic) {
                return true;
            }
        }
        if (event.getKeepInventory()) {
            for (ItemStack stack : victim.getInventory().getContents()) {
                ItemManager.Entry e = items.weaponOf(stack);
                if (e != null && !e.mythic) {
                    return true;
                }
            }
        }
        return false;
    }

    // ================================================================ no vanilla maces

    private boolean maceBlocked(ItemStack result) {
        return result != null && result.getType() == Material.MACE && items.weaponOf(result) == null
                && plugin.getConfig().getBoolean("combat.disable-mace-crafting", true);
    }

    @EventHandler
    public void onPrepare(PrepareItemCraftEvent event) {
        if (maceBlocked(event.getInventory().getResult())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        if (maceBlocked(event.getResult())) {
            event.setCancelled(true);
        }
    }
}
