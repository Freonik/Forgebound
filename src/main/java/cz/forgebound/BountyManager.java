package cz.forgebound;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Bounties on legend holders, Bounty Tokens and the token exchange (/bounty),
 * the Legend Tracker compass and the "pick a legendary material" menu.
 */
public final class BountyManager implements Listener {

    private static final int TOKEN_MODEL = 1402;

    private final Forgebound plugin;
    private final ItemManager items;
    private final NamespacedKey tokenKey;
    private final NamespacedKey trackerKey;
    private final File file;
    private final YamlConfiguration data;
    /** player -> minutes carried a legend since the last bounty point */
    private final Map<UUID, Integer> minutes = new HashMap<>();
    /** "killer|victim" -> last time that bounty was claimed (anti farming) */
    private final Map<String, Long> claimed = new HashMap<>();
    /** player -> last "you're being tracked" warning */
    private final Map<UUID, Long> warned = new HashMap<>();

    public BountyManager(Forgebound plugin, ItemManager items) {
        this.plugin = plugin;
        this.items = items;
        this.tokenKey = Forgebound.key("bounty_token");
        this.trackerKey = Forgebound.key("legend_tracker");
        this.file = new File(plugin.getDataFolder(), "bounties.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("bounty.enabled", true);
    }

    private void save() {
        plugin.saveYaml(data, file);
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                minuteTick();
            }
        }.runTaskTimer(plugin, 1200L, 1200L);
        new BukkitRunnable() {
            @Override
            public void run() {
                trackerTick();
            }
        }.runTaskTimer(plugin, 100L, Math.max(100L, plugin.getConfig().getLong("bounty.tracker-update-seconds", 30) * 20));
    }

    /** Adds to a player's bounty (e.g. for fleeing a Legend Hunt). */
    public void addBounty(Player player, int amount) {
        data.set("bounty." + player.getUniqueId(), bountyOf(player.getUniqueId()) + amount);
        data.set("names." + player.getUniqueId(), player.getName());
        save();
    }

    public void shutdown() {
        save();
    }

    // ================================================================ bounties

    /** Every player's current bounty (for /top). */
    public Map<UUID, Double> allBounties() {
        Map<UUID, Double> map = new HashMap<>();
        var section = data.getConfigurationSection("bounty");
        if (section != null) {
            for (String k : section.getKeys(false)) {
                int b = data.getInt("bounty." + k);
                if (b > 0) {
                    try {
                        map.put(UUID.fromString(k), (double) b);
                    } catch (IllegalArgumentException ignored) {
                        // skip
                    }
                }
            }
        }
        return map;
    }

    public int bountyOf(UUID player) {
        return data.getInt("bounty." + player, 0);
    }

    public boolean carriesLegend(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            ItemManager.Entry entry = items.customOf(stack);
            if (entry != null && entry.kind != ItemManager.Kind.MATERIAL) {
                return true;
            }
        }
        return false;
    }

    /** Every minute: carrying a legend slowly raises your bounty. */
    private void minuteTick() {
        if (!enabled()) {
            return;
        }
        int per = Math.max(1, plugin.getConfig().getInt("bounty.minutes-per-token", 10));
        int max = Math.max(1, plugin.getConfig().getInt("bounty.max-tokens", 20));
        List<Integer> announce = plugin.getConfig().getIntegerList("bounty.announce-at");
        if (announce.isEmpty()) {
            announce = List.of(5, 10, 20);
        }
        boolean changed = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!carriesLegend(player)) {
                continue;
            }
            int m = minutes.merge(player.getUniqueId(), 1, Integer::sum);
            if (m < per) {
                continue;
            }
            minutes.put(player.getUniqueId(), 0);
            int now = bountyOf(player.getUniqueId());
            if (now >= max) {
                continue;
            }
            now++;
            data.set("bounty." + player.getUniqueId(), now);
            data.set("names." + player.getUniqueId(), player.getName());
            changed = true;
            if (announce.contains(now)) {
                Bukkit.broadcast(plugin.msg("bounty-raised", "&c☠ &f{player} &7now has a bounty of &6{tokens} Bounty Tokens&7!",
                        "player", player.getName(), "tokens", String.valueOf(now)));
            }
        }
        if (changed) {
            save();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Player killer = PvpManager.killerOf(event);
        if (!enabled() || killer == null || killer.equals(victim)) {
            return;
        }
        if (plugin.teams() != null && plugin.teams().allies(killer, victim)
                && !plugin.getConfig().getBoolean("teams.bounty-on-teammates", false)) {
            return;                                              // no bounty farming inside a team
        }
        int bounty = bountyOf(victim.getUniqueId());
        if (bounty <= 0) {
            return;
        }
        String pair = killer.getUniqueId() + "|" + victim.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = claimed.get(pair);
        long cooldown = plugin.getConfig().getLong("bounty.same-target-cooldown-minutes", 30) * 60_000L;
        if (last != null && now - last < cooldown) {
            return;
        }
        claimed.put(pair, now);
        data.set("bounty." + victim.getUniqueId(), 0);
        save();
        give(killer, token(bounty));
        Bukkit.broadcast(plugin.msg("bounty-claimed", "&c☠ &f{killer} &7claimed the bounty on &f{victim}&7: &6{tokens} Bounty Tokens&7!",
                "killer", killer.getName(), "victim", victim.getName(), "tokens", String.valueOf(bounty)));
        killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.7f);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        warned.remove(event.getPlayer().getUniqueId());
    }

    // ================================================================ items

    public ItemStack token(int amount) {
        ItemStack stack = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(ItemManager.color("&6&lBounty Token"));
        meta.lore(List.of(ItemManager.color("&7Paid for hunting down a legend holder."),
                ItemManager.color("&eRight-click &7to spend it.")));
        meta.setCustomModelData(TOKEN_MODEL);
        meta.setMaxStackSize(64);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(tokenKey, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        stack.setAmount(Math.max(1, Math.min(64, amount)));
        return stack;
    }

    private boolean isToken(ItemStack stack) {
        return stack != null && stack.getType() == Material.KNOWLEDGE_BOOK && stack.hasItemMeta()
                && stack.getPersistentDataContainer().has(tokenKey, PersistentDataType.BYTE);
    }

    public ItemStack tracker() {
        ItemStack stack = new ItemStack(Material.COMPASS);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(ItemManager.color("&c&lLegend Tracker"));
        meta.setCustomModelData(1502);
        meta.lore(List.of(ItemManager.color("&7Points roughly at the nearest player"),
                ItemManager.color("&7carrying a legend. Updates every 30s."),
                ItemManager.color("&8Searching...")));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(trackerKey, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean isTracker(ItemStack stack) {
        return stack != null && stack.getType() == Material.COMPASS && stack.hasItemMeta()
                && stack.getPersistentDataContainer().has(trackerKey, PersistentDataType.BYTE);
    }

    public void give(Player player, ItemStack stack) {
        player.getInventory().addItem(stack).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    public int countTokens(Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isToken(stack)) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    public boolean takeTokens(Player player, int amount) {
        if (countTokens(player) < amount) {
            return false;
        }
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            if (isToken(contents[i])) {
                int take = Math.min(amount, contents[i].getAmount());
                contents[i].setAmount(contents[i].getAmount() - take);
                player.getInventory().setItem(i, contents[i].getAmount() <= 0 ? null : contents[i]);
                amount -= take;
            }
        }
        return true;
    }

    /** Tokens open the exchange; trackers can't be bound to a lodestone; neither is used in crafting. */
    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (isToken(item)) {
            event.setUseItemInHand(Event.Result.DENY);
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                if (plugin.shop() != null && plugin.shop().enabled()) {
                    plugin.shop().open(event.getPlayer());
                } else {
                    openExchange(event.getPlayer());
                }
            }
        } else if (isTracker(item) && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.LODESTONE) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack stack : event.getInventory().getMatrix()) {
            if (isToken(stack) || isTracker(stack)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    // ================================================================ Legend Tracker

    private void trackerTick() {
        double offset = plugin.getConfig().getDouble("bounty.tracker-offset", 50);
        List<Player> holders = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (carriesLegend(p)) {
                holders.add(p);
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (EquipmentSlot hand : new EquipmentSlot[] {EquipmentSlot.HAND, EquipmentSlot.OFF_HAND}) {
                ItemStack stack = player.getInventory().getItem(hand);
                if (!isTracker(stack)) {
                    continue;
                }
                Player target = holders.stream()
                        .filter(h -> !h.equals(player) && h.getWorld() == player.getWorld()
                                && (plugin.teams() == null || !plugin.teams().allies(player, h)))
                        .min(Comparator.comparingDouble(h -> h.getLocation().distanceSquared(player.getLocation())))
                        .orElse(null);
                CompassMeta meta = (CompassMeta) stack.getItemMeta();
                List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
                while (lore.size() > 2) {
                    lore.remove(lore.size() - 1);
                }
                if (target == null) {
                    meta.setLodestone(null);
                    lore.add(ItemManager.color("&8No legend in this world..."));
                } else {
                    Location aim = target.getLocation().clone().add(
                            ThreadLocalRandom.current().nextDouble(-offset, offset), 0,
                            ThreadLocalRandom.current().nextDouble(-offset, offset));
                    meta.setLodestone(aim);
                    meta.setLodestoneTracked(false);
                    double d = target.getLocation().distance(player.getLocation());
                    String range = d < 100 ? "< 100" : d < 250 ? "< 250" : d < 500 ? "< 500" : d < 1000 ? "< 1000" : "1000+";
                    lore.add(ItemManager.color("&cA legend is &f" + range + " &cblocks away"));
                    Long last = warned.get(target.getUniqueId());
                    if (last == null || System.currentTimeMillis() - last > 120_000) {
                        warned.put(target.getUniqueId(), System.currentTimeMillis());
                        target.sendActionBar(plugin.raw("bounty-tracked", "&8You feel like someone is tracking you..."));
                    }
                }
                meta.lore(lore);
                stack.setItemMeta(meta);
                player.getInventory().setItem(hand, stack);
            }
        }
    }

    // ================================================================ /bounty and the exchange

    private static final class ExchangeHolder implements InventoryHolder {
        Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class PickerHolder implements InventoryHolder {
        Inventory inventory;
        Consumer<ItemManager.Entry> onPick;
        List<ItemManager.Entry> options;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private int cost(String what, int def) {
        return Math.max(1, plugin.getConfig().getInt("bounty.exchange." + what, def));
    }

    public boolean onCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("player-only", "&cOnly players can use this command."));
            return true;
        }
        openExchange(player);
        return true;
    }

    public void openExchange(Player player) {
        ExchangeHolder holder = new ExchangeHolder();
        boolean pack = plugin.menus() != null && plugin.menus().usesPack(player);
        holder.inventory = Bukkit.createInventory(holder, 27,
                pack ? MenuManager.glyphTitle('\ue004') : ItemManager.color("&8Bounty Exchange"));
        Inventory inv = holder.inventory;
        ItemStack filler = pack ? MenuManager.clearTile() : named(new ItemStack(Material.BLACK_STAINED_GLASS_PANE), " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }
        int tokens = countTokens(player);
        // your bounty + the most wanted players
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(player);
        sm.displayName(ItemManager.color("&6&lBounties"));
        List<Component> lore = new ArrayList<>();
        lore.add(ItemManager.color("&7Your bounty: &6" + bountyOf(player.getUniqueId()) + " tokens"));
        lore.add(ItemManager.color("&7Your tokens: &6" + tokens));
        lore.add(Component.empty());
        lore.add(ItemManager.color("&c&lMost wanted"));
        var section = data.getConfigurationSection("bounty");
        if (section != null) {
            section.getKeys(false).stream()
                    .filter(k -> data.getInt("bounty." + k) > 0)
                    .sorted(Comparator.comparingInt(k -> -data.getInt("bounty." + k)))
                    .limit(5)
                    .forEach(k -> lore.add(ItemManager.color("&f" + data.getString("names." + k, "?")
                            + " &8- &6" + data.getInt("bounty." + k))));
        }
        sm.lore(lore);
        head.setItemMeta(sm);
        inv.setItem(4, head);
        inv.setItem(11, offer(tracker(), cost("tracker", 3), tokens));
        inv.setItem(13, offer(named(new ItemStack(Material.ENDER_EYE), "&d&lRandom legendary material"),
                cost("random-material", 4), tokens));
        inv.setItem(15, offer(named(new ItemStack(Material.NETHER_STAR), "&6&lChoose a legendary material"),
                cost("chosen-material", 10), tokens));
        player.openInventory(inv);
    }

    private static ItemStack named(ItemStack stack, String name) {
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(ItemManager.color(name));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack offer(ItemStack stack, int price, int have) {
        ItemMeta meta = stack.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
        lore.add(Component.empty());
        lore.add(ItemManager.color((have >= price ? "&a" : "&c") + "Costs " + price + " Bounty Tokens"));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    /** Legendary materials that can currently drop (switched on in "craftable"). */
    public List<ItemManager.Entry> enabledMaterials() {
        return items.all(ItemManager.Kind.MATERIAL).stream().filter(items::craftable).toList();
    }

    /** A menu with every enabled legendary material; the pick is passed to onPick. */
    public void openPicker(Player player, String title, Consumer<ItemManager.Entry> onPick) {
        PickerHolder holder = new PickerHolder();
        holder.options = enabledMaterials();
        holder.onPick = onPick;
        int size = Math.max(9, Math.min(54, ((holder.options.size() + 8) / 9) * 9));
        boolean pack = plugin.menus() != null && plugin.menus().usesPack(player);
        holder.inventory = Bukkit.createInventory(holder, size,
                pack ? MenuManager.glyphTitle((char) (0xe004 + size / 9)) : ItemManager.color(title));
        for (int i = 0; i < holder.options.size() && i < size; i++) {
            holder.inventory.setItem(i, holder.options.get(i).item.clone());
        }
        player.openInventory(holder.inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder top = event.getView().getTopInventory().getHolder();
        if (!(top instanceof ExchangeHolder) && !(top instanceof PickerHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getRawSlot();
        if (top instanceof PickerHolder picker) {
            if (slot >= 0 && slot < picker.options.size()) {
                ItemManager.Entry chosen = picker.options.get(slot);
                player.closeInventory();
                picker.onPick.accept(chosen);
            }
            return;
        }
        switch (slot) {
            case 11 -> buy(player, cost("tracker", 3), () -> give(player, tracker()));
            case 13 -> buy(player, cost("random-material", 4), () -> {
                List<ItemManager.Entry> list = enabledMaterials();
                if (!list.isEmpty()) {
                    give(player, list.get(ThreadLocalRandom.current().nextInt(list.size())).item.clone());
                }
            });
            case 15 -> {
                int price = cost("chosen-material", 10);
                if (countTokens(player) < price) {
                    notEnough(player, price);
                    return;
                }
                openPicker(player, "&8Choose a legendary material", chosen -> {
                    if (takeTokens(player, price)) {
                        give(player, chosen.item.clone());
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                    } else {
                        notEnough(player, price);
                    }
                });
            }
            default -> { }
        }
    }

    private void buy(Player player, int price, Runnable reward) {
        if (!takeTokens(player, price)) {
            notEnough(player, price);
            return;
        }
        reward.run();
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        openExchange(player);
    }

    private void notEnough(Player player, int price) {
        player.sendMessage(plugin.msg("bounty-not-enough", "&cYou need &6{price} Bounty Tokens&c.", "price", String.valueOf(price)));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder top = event.getView().getTopInventory().getHolder();
        if (top instanceof ExchangeHolder || top instanceof PickerHolder) {
            event.setCancelled(true);
        }
    }
}
