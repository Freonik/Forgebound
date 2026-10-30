package cz.forgebound;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class Forgebound extends JavaPlugin implements Listener {

    private ItemManager items;
    private MenuManager menus;
    private RelicManager relics;
    private LootManager loot;
    private ForgeData data;
    private RitualManager rituals;
    private AbilityManager abilities;
    private AdvancementManager advancements;
    private LegendManager legends;
    private BountyManager bounty;
    private MeteorManager meteor;
    private WardenManager warden;
    private ShopManager shop;
    private EventManager events;
    private TopManager top;
    private GhostManager ghost;
    private SeasonManager season;
    private CodexManager codex;
    private ReconManager recon;
    private TeamManager teams;
    private Placeholders placeholders;
    private HudManager hud;
    private EnchantManager enchants;
    private Updater updater;
    private AutoRestart autoRestart;
    private SpawnBuilder spawnBuilder;
    private ProtectedZones protectedZones;
    private CustomEnchants customEnchants;
    private ScorchedLands scorched;
    private EmberCrags emberCrags;
    private GlassedPlains glassedPlains;
    private AshenForest ashenForest;
    private StarManager stars;
    private PvpManager pvp;
    private MythicManager mythics;
    private BigEventManager bigEvents;
    private QuestManager quests;
    private TotemManager totems;
    private boolean papi;

    /** Writes data files one after another in the background, so saving never stalls the server. */
    private final java.util.concurrent.ExecutorService io = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Forgebound-IO");
        t.setDaemon(true);
        return t;
    });

    /** Saves a YAML file in the background (the text is taken now, on the main thread). */
    public void saveYaml(org.bukkit.configuration.file.YamlConfiguration yaml, java.io.File file) {
        String text = yaml.saveToString();
        if (io.isShutdown()) {
            writeFile(text, file);
        } else {
            io.execute(() -> writeFile(text, file));
        }
    }

    private void writeFile(String text, java.io.File file) {
        try {
            java.nio.file.Path target = file.toPath();
            java.nio.file.Files.createDirectories(target.getParent());
            java.nio.file.Path tmp = target.resolveSibling(file.getName() + ".tmp");
            java.nio.file.Files.writeString(tmp, text, java.nio.charset.StandardCharsets.UTF_8);
            try {
                java.nio.file.Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                java.nio.file.Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (java.io.IOException ex) {
            getLogger().warning("Could not save " + file.getName() + ": " + ex.getMessage());
        }
    }

    /**
     * Keys for data stored on items, players and advancements. They keep the old "customcraft" namespace,
     * so everything made before the rename still works.
     */
    public static org.bukkit.NamespacedKey key(String key) {
        return new org.bukkit.NamespacedKey("customcraft", key);
    }

    /** First start after the rename: copy plugins/CustomCraft/ into plugins/Forgebound/. */
    private void migrateOldFolder() {
        java.io.File old = new java.io.File(getDataFolder().getParentFile(), "CustomCraft");
        if (!old.isDirectory() || new java.io.File(getDataFolder(), "config.yml").exists()) {
            return;
        }
        java.nio.file.Path from = old.toPath();
        java.nio.file.Path to = getDataFolder().toPath();
        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(from)) {
            for (java.nio.file.Path src : (Iterable<java.nio.file.Path>) walk::iterator) {
                java.nio.file.Path dst = to.resolve(from.relativize(src).toString());
                if (java.nio.file.Files.isDirectory(src)) {
                    java.nio.file.Files.createDirectories(dst);
                } else {
                    java.nio.file.Files.copy(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            getLogger().info("Copied your old plugins/CustomCraft/ data to plugins/Forgebound/. "
                    + "You can delete plugins/CustomCraft/ once everything works.");
        } catch (java.io.IOException ex) {
            getLogger().severe("Could not copy plugins/CustomCraft/: " + ex.getMessage());
        }
    }

    @Override
    public void onEnable() {
        migrateOldFolder();
        saveDefaultConfig();
        mergeNewDefaults();

        items = new ItemManager(this);
        items.load();

        data = new ForgeData(this);
        data.load();
        rituals = new RitualManager(this, items, data);
        menus = new MenuManager(this, items, rituals);
        relics = new RelicManager(this, items);
        loot = new LootManager(this, items);
        loot.load();

        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getPluginManager().registerEvents(relics, this);
        getServer().getPluginManager().registerEvents(loot, this);
        relics.start();
        getServer().getPluginManager().registerEvents(rituals, this);
        rituals.start();
        abilities = new AbilityManager(this, items);
        abilities.setRelics(relics);
        getServer().getPluginManager().registerEvents(abilities, this);
        abilities.start();
        getServer().getPluginManager().registerEvents(this, this);
        legends = new LegendManager(this, items);
        getServer().getPluginManager().registerEvents(legends, this);
        legends.start();
        advancements = new AdvancementManager(this, items);
        getServer().getPluginManager().registerEvents(advancements, this);
        advancements.register();
        advancements.start();
        bounty = new BountyManager(this, items);
        getServer().getPluginManager().registerEvents(bounty, this);
        bounty.start();
        shop = new ShopManager(this);
        getServer().getPluginManager().registerEvents(shop, this);
        warden = new WardenManager(this);
        getServer().getPluginManager().registerEvents(warden, this);
        warden.start();
        meteor = new MeteorManager(this, items);
        meteor.start();
        getServer().getPluginManager().registerEvents(new MotdManager(this, items), this);
        events = new EventManager(this);
        RiftEvent rift = new RiftEvent(this, events);
        events.setRift(rift);
        LegendHunt hunt = new LegendHunt(this);
        events.setHunt(hunt);
        getServer().getPluginManager().registerEvents(hunt, this);
        getServer().getPluginManager().registerEvents(events, this);
        getServer().getPluginManager().registerEvents(rift, this);
        events.start();

        teams = new TeamManager(this);
        recon = new ReconManager(this);
        getServer().getPluginManager().registerEvents(recon, this);
        recon.start();
        season = new SeasonManager(this);
        getServer().getPluginManager().registerEvents(season, this);
        ghost = new GhostManager(this);
        getServer().getPluginManager().registerEvents(ghost, this);
        ghost.start();
        placeholders = new Placeholders(this);
        top = new TopManager(this);
        top.start();
        hud = new HudManager(this);
        getServer().getPluginManager().registerEvents(hud, this);
        hud.start();
        codex = new CodexManager(this);
        getServer().getPluginManager().registerEvents(codex, this);
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                papi = new PapiExpansion(this).register();
                getLogger().info("PlaceholderAPI found: %forgebound_...% placeholders are available.");
            } catch (Throwable ex) {
                getLogger().warning("Could not hook into PlaceholderAPI: " + ex.getMessage());
            }
        }

        enchants = new EnchantManager(this);
        getServer().getPluginManager().registerEvents(enchants, this);
        getServer().getPluginManager().registerEvents(new ItemComponents(this), this);

        stars = new StarManager(this, items);
        getServer().getPluginManager().registerEvents(stars, this);
        stars.start();
        pvp = new PvpManager(this, items);
        getServer().getPluginManager().registerEvents(pvp, this);
        getServer().getPluginManager().registerEvents(new VillagerRules(this), this);
        pvp.start();
        mythics = new MythicManager(this, items);
        getServer().getPluginManager().registerEvents(mythics, this);
        mythics.start();
        bigEvents = new BigEventManager(this);
        getServer().getPluginManager().registerEvents(bigEvents, this);
        bigEvents.start();
        totems = new TotemManager(this);
        getServer().getPluginManager().registerEvents(totems, this);
        quests = new QuestManager(this, items);
        getServer().getPluginManager().registerEvents(quests, this);
        quests.start();
        scorched = new ScorchedLands(this);
        getServer().getPluginManager().registerEvents(scorched, this);
        scorched.start();
        emberCrags = new EmberCrags(this);
        getServer().getPluginManager().registerEvents(emberCrags, this);
        emberCrags.start();
        glassedPlains = new GlassedPlains(this);
        getServer().getPluginManager().registerEvents(glassedPlains, this);
        glassedPlains.start();
        ashenForest = new AshenForest(this);
        getServer().getPluginManager().registerEvents(ashenForest, this);
        ashenForest.start();
        updater = new Updater(this);
        getServer().getPluginManager().registerEvents(updater, this);
        updater.start();
        autoRestart = new AutoRestart(this);
        spawnBuilder = new SpawnBuilder(this);
        protectedZones = new ProtectedZones(this);
        getServer().getPluginManager().registerEvents(protectedZones, this);
        protectedZones.start();
        customEnchants = new CustomEnchants(this);
        getServer().getPluginManager().registerEvents(customEnchants, this);
        customEnchants.start();
        autoRestart.start();

        RecipesCommand command = new RecipesCommand(this, items, rituals, data);
        new ForgeCommands(this, command).register();

        getLogger().info("Loaded " + items.all(ItemManager.Kind.WEAPON).size() + " weapons, "
                + items.all(ItemManager.Kind.RELIC).size() + " relics.");
    }

    @Override
    public void onDisable() {
        if (updater != null) {
            updater.stop();
        }
        if (autoRestart != null) {
            autoRestart.stop();
        }
        if (spawnBuilder != null) {
            spawnBuilder.shutdown();
        }
        if (protectedZones != null) {
            protectedZones.shutdown();
        }
        if (abilities != null) {
            abilities.shutdown();
        }
        if (legends != null) {
            legends.shutdown();
        }
        if (meteor != null) {
            meteor.shutdown();
        }
        if (events != null) {
            events.shutdown();
        }
        if (hud != null) {
            hud.shutdown();
        }
        if (ghost != null) {
            ghost.shutdown();
        }
        if (season != null) {
            season.shutdown();
        }
        if (recon != null) {
            recon.shutdown();
        }
        if (warden != null) {
            warden.shutdown();
        }
        if (bounty != null) {
            bounty.shutdown();
        }
        if (rituals != null) {
            rituals.shutdown();
        }
        if (pvp != null) {
            pvp.shutdown();
        }
        if (bigEvents != null) {
            bigEvents.shutdown();
        }
        if (quests != null) {
            quests.shutdown();
        }
        if (stars != null) {
            stars.shutdown();
        }
        io.shutdown();
        try {
            if (!io.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                getLogger().warning("Some data files were still being saved when the server stopped.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public RitualManager rituals() {
        return rituals;
    }

    /** Completes one of the plugin's advancements for a player (no-op if they're off). */
    public void award(org.bukkit.entity.Player player, String id) {
        if (advancements != null) {
            advancements.award(player, id);
        }
    }

    public BountyManager bounty() {
        return bounty;
    }

    public TeamManager teams() {
        return teams;
    }

    public ReconManager recon() {
        return recon;
    }

    public CodexManager codex() {
        return codex;
    }

    public HudManager hud() {
        return hud;
    }

    public StarManager stars() {
        return stars;
    }

    public PvpManager pvp() {
        return pvp;
    }

    public MythicManager mythics() {
        return mythics;
    }

    public BigEventManager bigEvents() {
        return bigEvents;
    }

    public QuestManager quests() {
        return quests;
    }

    public TotemManager totems() {
        return totems;
    }

    public GlassedPlains glassedPlains() {
        return glassedPlains;
    }

    public EmberCrags emberCrags() {
        return emberCrags;
    }

    public ScorchedLands scorched() {
        return scorched;
    }

    public Updater updater() {
        return updater;
    }

    public ProtectedZones protectedZones() {
        return protectedZones;
    }

    public ForgeData forgeData() {
        return data;
    }

    public RelicManager relics() {
        return relics;
    }

    public CustomEnchants customEnchants() {
        return customEnchants;
    }

    public SpawnBuilder spawnBuilder() {
        return spawnBuilder;
    }

    public AutoRestart autoRestart() {
        return autoRestart;
    }

    /** This plugin's jar in plugins/ (for the updater). */
    public java.io.File jarFile() {
        return getFile();
    }

    public EnchantManager enchants() {
        return enchants;
    }

    public SeasonManager season() {
        return season;
    }

    public GhostManager ghost() {
        return ghost;
    }

    public TopManager top() {
        return top;
    }

    public Placeholders placeholders() {
        return placeholders;
    }

    /** Is PlaceholderAPI installed and our expansion registered? */
    public boolean papi() {
        return papi;
    }

    public MenuManager menus() {
        return menus;
    }

    public EventManager events() {
        return events;
    }

    public ShopManager shop() {
        return shop;
    }

    public MeteorManager meteor() {
        return meteor;
    }

    public WardenManager warden() {
        return warden;
    }

    public ItemManager items() {
        return items;
    }

    public LegendManager legends() {
        return legends;
    }

    /** Gives a freshly made legend its serial number (see LegendManager). */
    public void stamp(org.bukkit.inventory.ItemStack stack, String ref) {
        if (legends != null) {
            legends.stamp(stack, ref);
        }
    }

    public AdvancementManager advancements() {
        return advancements;
    }

    /** Reloads config.yml and re-registers recipes. */
    public void reload() {
        reloadConfig();
        mergeNewDefaults();
        items.load();
        loot.load();
        if (updater != null) {
            updater.start();
        }
        if (emberCrags != null) {
            emberCrags.load();
        }
        if (glassedPlains != null) {
            glassedPlains.load();
        }
        if (ashenForest != null) {
            ashenForest.load();
        }
        if (scorched != null) {
            scorched.load();
        }
        if (getConfig().getBoolean("discover-recipes-on-join", true)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.discoverRecipes(items.recipeKeys());
            }
        }
    }

    /** Sections where new default entries (new weapons, relics, materials, messages...) are added automatically. */
    private static final java.util.List<String> MERGED_SECTIONS = java.util.List.of("weapons", "relics", "materials",
            "craftable.weapons", "craftable.relics", "craftable.materials", "messages", "ability-settings", "relic-settings",
            "ritual", "ember-crags", "glassed-plains", "ashen-forest", "end-ruins", "ancient-debris", "protection", "custom-enchants", "custom-enchants.open-levels", "custom-enchants.copies", "custom-enchants.list", "custom-enchants.loot", "combat", "combat.duel", "big-events", "big-events.border", "big-events.hunt", "big-events.boss", "quests", "forge-totem", "bounty", "meteor", "warden", "economy", "economy.rewards",
            "economy.shop.permanent", "economy.shop.rotating",
            "events", "events.blood-moon", "events.rift", "events.legend-hunt", "events.happy-hour", "events.happy-hour.types",
            "events.forge-market", "events.forge-market.offers", "events.golden-shipment", "motd",
            "hud", "hud.scoreboard", "hud.tab", "hud.join-title", "codex", "top", "doppelganger", "season", "recon", "teams",
            "dialogs", "enchantments", "enchantments.forge-touch", "enchantments.bounty-hunter", "items", "auto-update",
            "scorched-lands", "scorched-lands.loot", "stars", "ancient-forge");

    /**
     * Copies entries that are new in this plugin version (e.g. a new weapon) into an existing config.yml,
     * so updating doesn't require deleting the config. config.yml is only rewritten when something is added.
     * Entries that were already offered once are remembered in defaults-seen.yml, so ones you delete
     * on purpose are never added back.
     */
    private void mergeNewDefaults() {
        org.bukkit.configuration.Configuration defaults = getConfig().getDefaults();
        if (defaults == null) {
            return;
        }
        java.io.File seenFile = new java.io.File(getDataFolder(), "defaults-seen.yml");
        org.bukkit.configuration.file.YamlConfiguration seenYaml =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(seenFile);
        java.util.Set<String> seen = new java.util.LinkedHashSet<>(seenYaml.getStringList("seen"));
        boolean firstRun = !seenFile.exists();
        java.util.List<String> added = new java.util.ArrayList<>();
        boolean seenChanged = false;
        for (String section : MERGED_SECTIONS) {
            org.bukkit.configuration.ConfigurationSection def = defaults.getConfigurationSection(section);
            if (def == null) {
                continue;
            }
            for (String key : def.getKeys(false)) {
                String path = section + "." + key;
                if (!getConfig().isSet(path) && !seen.contains(path)) {
                    Object value = plain(def.get(key));
                    if (value instanceof java.util.Map<?, ?> map) {
                        getConfig().createSection(path, map);
                    } else {
                        getConfig().set(path, value);
                    }
                    added.add(path);
                }
                seenChanged |= seen.add(path);
            }
        }
        // changed defaults: a value that was never touched (still the old default) moves to the new one, once
        String[][] changedDefaults = {{"doppelganger.attack-reach", "2.5", "2.0"}, {"auto-update.check-hours", "6", "1"},
                {"doppelganger.speed", "0.3", "0.19"},
                {"doppelganger.knockback-resistance", "0.4", "0"}};
        for (String[] c : changedDefaults) {
            String mark = "changed:" + c[0] + "=" + c[2];
            if (seen.contains(mark)) {
                continue;
            }
            if (getConfig().isSet(c[0]) && Math.abs(getConfig().getDouble(c[0]) - Double.parseDouble(c[1])) < 1e-9) {
                getConfig().set(c[0], Double.parseDouble(c[2]));
                added.add(c[0] + " (" + c[1] + " -> " + c[2] + ")");
            }
            seenChanged |= seen.add(mark);
        }
        // the nightly restart only works where the host starts the server again after it stops: off until an admin turns it on
        String restartMark = "changed:auto-update.restart.enabled=false";
        if (!seen.contains(restartMark)) {
            if (getConfig().getBoolean("auto-update.restart.enabled", false)) {
                getConfig().set("auto-update.restart.enabled", false);
                added.add("auto-update.restart.enabled (true -> false)");
            }
            seenChanged |= seen.add(restartMark);
        }
        // quests got much harder (2.27.0): the default pools replace the old ones once
        String questMark = "changed:quests=hard";
        if (!seen.contains(questMark)) {
            for (String pool : new String[]{"quests.daily", "quests.weekly"}) {
                org.bukkit.configuration.ConfigurationSection def = defaults.getConfigurationSection(pool);
                if (def != null) {
                    getConfig().set(pool, null);
                    getConfig().createSection(pool, (java.util.Map<?, ?>) plain(def));
                    added.add(pool + " (harder)");
                }
            }
            seenChanged |= seen.add(questMark);
        }
                // vanilla maces can't be crafted any more: Knightfall's forge recipe swaps the mace for breeze rods
        String maceMark = "changed:weapons.knightfall.forge=no-mace";
        if (!seen.contains(maceMark)) {
            java.util.List<java.util.Map<?, ?>> forge = getConfig().getMapList("weapons.knightfall.forge");
            java.util.List<java.util.Map<String, Object>> fixed = new java.util.ArrayList<>();
            boolean hadMace = false;
            for (java.util.Map<?, ?> m : forge) {
                java.util.Map<String, Object> copy = new java.util.LinkedHashMap<>();
                m.forEach((k, v) -> copy.put(String.valueOf(k), v));
                String item = String.valueOf(copy.get("item"));
                if (item.equalsIgnoreCase("MACE")) {
                    hadMace = true;
                    copy.put("item", "BREEZE_ROD");
                    copy.put("amount", 8);
                } else if (item.equalsIgnoreCase("HEAVY_CORE")) {
                    copy.put("amount", 2);
                }
                fixed.add(copy);
            }
            if (hadMace) {
                getConfig().set("weapons.knightfall.forge", fixed);
                added.add("weapons.knightfall.forge (MACE -> BREEZE_ROD x8, HEAVY_CORE x2)");
            }
            seenChanged |= seen.add(maceMark);
        }
        if (!added.isEmpty()) {
            saveConfig();
            getLogger().info("Added new default config entries: " + String.join(", ", added));
        }
        if (seenChanged || firstRun) {
            seenYaml.options().setHeader(java.util.List.of(
                    "Forgebound remembers which default config entries it already added to config.yml,",
                    "so entries you delete on purpose are not added back. Don't edit this file."));
            seenYaml.set("seen", new java.util.ArrayList<>(seen));
            try {
                seenYaml.save(seenFile);
            } catch (java.io.IOException ex) {
                getLogger().warning("Could not save defaults-seen.yml: " + ex.getMessage());
            }
        }
    }

    /** Config sections -> plain maps, so they are saved as normal YAML. */
    private static Object plain(Object value) {
        if (value instanceof org.bukkit.configuration.ConfigurationSection section) {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            for (String key : section.getKeys(false)) {
                map.put(key, plain(section.get(key)));
            }
            return map;
        }
        return value;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (getConfig().getBoolean("discover-recipes-on-join", true)) {
            event.getPlayer().discoverRecipes(items.recipeKeys());
        }
    }

    /**
     * Message from the config with the prefix. Placeholders are passed in pairs:
     * msg("reloaded", "default", "weapons", 3, "relics", 2) replaces {weapons} and {relics}.
     */
    public Component msg(String key, String def, Object... pairs) {
        return format(getConfig().getString("messages.prefix", ""), key, def, pairs);
    }

    /** Same as {@link #msg}, but without the prefix (for the action bar). */
    public Component raw(String key, String def, Object... pairs) {
        return format("", key, def, pairs);
    }

    private Component format(String prefix, String key, String def, Object... pairs) {
        String raw = getConfig().getString("messages." + key, def);
        Component result = ItemManager.color(prefix + raw);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            final String placeholder = "{" + pairs[i] + "}";
            final Component value = pairs[i + 1] instanceof Component component
                    ? component
                    : Component.text(String.valueOf(pairs[i + 1]));
            result = result.replaceText(b -> b.matchLiteral(placeholder).replacement(value));
        }
        return result;
    }
}
