package cz.forgebound;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Big events, started by an admin only - planned ahead and announced (a day, an hour, 10 minutes, a minute before):
 * <ul>
 *   <li><b>border</b> - The Closing World: the world border shrinks around one spot, the last one standing wins.</li>
 *   <li><b>hunt</b> - The Hunt: everyone shows on the locator bar, the most player kills wins.</li>
 *   <li><b>boss</b> - The Forge Colossus: a world boss, the most damage wins.</li>
 * </ul>
 * The winner gets a mythic weapon (fades after a few days), the others Bounty Tokens.
 */
public final class BigEventManager implements Listener {

    public static final List<String> TYPES = List.of("border", "hunt", "boss");

    private final Forgebound plugin;
    private final File file;
    private final YamlConfiguration data;
    private final NamespacedKey bossKey;
    /** id -> planned event */
    private final Map<String, Planned> planned = new LinkedHashMap<>();
    /** planned id -> announcement thresholds (minutes) already sent */
    private final Map<String, Set<Integer>> announced = new HashMap<>();
    private BossBar countdown;
    private Running running;

    record Planned(String id, String type, long at, String world, double x, double z, String by) { }

    public BigEventManager(Forgebound plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "big-events.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        this.bossKey = Forgebound.key("big_event_boss");
    }

    private ConfigurationSection cfg(String type) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("big-events." + type);
        return s != null ? s : new YamlConfiguration();
    }

    private ZoneId zone() {
        try {
            return ZoneId.of(plugin.getConfig().getString("big-events.timezone", "Europe/Prague"));
        } catch (RuntimeException ex) {
            return ZoneId.systemDefault();
        }
    }

    public static String title(String type) {
        return switch (type) {
            case "border" -> "&c&lThe Closing World";
            case "hunt" -> "&6&lThe Hunt";
            case "boss" -> "&4&lThe Forge Colossus";
            default -> type;
        };
    }

    // ================================================================ lifecycle

    public void start() {
        ConfigurationSection list = data.getConfigurationSection("planned");
        if (list != null) {
            for (String id : list.getKeys(false)) {
                ConfigurationSection s = list.getConfigurationSection(id);
                planned.put(id, new Planned(id, s.getString("type"), s.getLong("at"), s.getString("world"),
                        s.getDouble("x"), s.getDouble("z"), s.getString("by", "?")));
            }
        }
        restoreBackups();
        // a boss left over from a crash or restart
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getPersistentDataContainer().has(bossKey, PersistentDataType.BYTE)) {
                    e.remove();
                }
            }
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 40L, 20L);
    }

    public void shutdown() {
        if (running != null) {
            running.end(false);
            running = null;
        }
        hideCountdown();
        save();
    }

    private void save() {
        data.set("planned", null);
        for (Planned p : planned.values()) {
            String base = "planned." + p.id() + ".";
            data.set(base + "type", p.type());
            data.set(base + "at", p.at());
            data.set(base + "world", p.world());
            data.set(base + "x", p.x());
            data.set(base + "z", p.z());
            data.set(base + "by", p.by());
        }
        plugin.saveYaml(data, file);
    }

    /** The world border / locator bar from before an event that never finished (crash). */
    private void restoreBackups() {
        ConfigurationSection b = data.getConfigurationSection("backup.border");
        if (b != null) {
            World w = Bukkit.getWorld(b.getString("world", ""));
            if (w != null) {
                WorldBorder border = w.getWorldBorder();
                border.setCenter(b.getDouble("x"), b.getDouble("z"));
                border.setSize(b.getDouble("size"));
                border.setDamageAmount(b.getDouble("damage"));
                border.setDamageBuffer(b.getDouble("buffer"));
                plugin.getLogger().info("[BigEvent] Restored the world border of " + w.getName() + " after an unfinished event.");
            }
            data.set("backup.border", null);
        }
        ConfigurationSection l = data.getConfigurationSection("backup.locator");
        if (l != null) {
            for (String world : l.getKeys(false)) {
                World w = Bukkit.getWorld(world);
                if (w != null) {
                    w.setGameRule(GameRules.LOCATOR_BAR, l.getBoolean(world));
                }
            }
            data.set("backup.locator", null);
        }
        plugin.saveYaml(data, file);
    }

    // ================================================================ planning

    private static final Pattern RELATIVE = Pattern.compile("\\+(\\d+)\\s*([mhd])");
    private static final Pattern CLOCK = Pattern.compile("(?:(\\d{1,2})\\.(\\d{1,2})\\.?(\\d{4})?\\s+)?(\\d{1,2}):(\\d{2})(?::\\d{2})?");

    /** "+30m", "+2h", "20:00" (next time it's 20:00), "25.10 20:00", "25.10.2026 20:00". Returns ms or -1. */
    long parseTime(String text) {
        String t = text.trim().toLowerCase(Locale.ROOT);
        Matcher r = RELATIVE.matcher(t);
        if (r.matches()) {
            long n = Long.parseLong(r.group(1));
            long unit = switch (r.group(2)) {
                case "m" -> 60_000L;
                case "h" -> 3_600_000L;
                default -> 86_400_000L;
            };
            return System.currentTimeMillis() + n * unit;
        }
        Matcher c = CLOCK.matcher(t);
        if (!c.matches()) {
            return -1;
        }
        ZonedDateTime now = ZonedDateTime.now(zone());
        LocalTime time;
        try {
            time = LocalTime.of(Integer.parseInt(c.group(4)), Integer.parseInt(c.group(5)));
        } catch (RuntimeException ex) {
            return -1;
        }
        LocalDate date;
        if (c.group(1) != null) {
            int year = c.group(3) != null ? Integer.parseInt(c.group(3)) : now.getYear();
            try {
                date = LocalDate.of(year, Integer.parseInt(c.group(2)), Integer.parseInt(c.group(1)));
            } catch (RuntimeException ex) {
                return -1;
            }
        } else {
            date = now.toLocalDate();
            if (!LocalDateTime.of(date, time).atZone(zone()).isAfter(now)) {
                date = date.plusDays(1);
            }
        }
        return LocalDateTime.of(date, time).atZone(zone()).toInstant().toEpochMilli();
    }

    private String when(long at) {
        return DateTimeFormatter.ofPattern("d.M. HH:mm").format(java.time.Instant.ofEpochMilli(at).atZone(zone()));
    }

    public void plan(CommandSender sender, String type, String timeText) {
        if (!TYPES.contains(type)) {
            sender.sendMessage(ItemManager.color("&cUnknown event. Use: " + String.join(", ", TYPES)));
            return;
        }
        long at = parseTime(timeText);
        if (at < 0 || at < System.currentTimeMillis() + 30_000L) {
            sender.sendMessage(ItemManager.color("&cTime not understood or in the past. Examples: &f20:00&c, &f25.10 20:00&c, &f+30m&c, &f+2h"));
            return;
        }
        Location where = defaultLocation(type, sender instanceof Player p ? p : null);
        String id = Long.toString(at / 60_000L, 36) + type.charAt(0);
        Planned p = new Planned(id, type, at, where.getWorld().getName(), where.getX(), where.getZ(), sender.getName());
        planned.put(id, p);
        save();
        sender.sendMessage(ItemManager.color("&aPlanned " + title(type) + " &afor &f" + when(at) + " &7(id &f" + id
                + "&7, at " + where.getWorld().getName() + " " + (int) where.getX() + " " + (int) where.getZ() + ")"));
        plugin.getLogger().info("[BigEvent] " + sender.getName() + " planned " + type + " for " + when(at) + " (id " + id + ")");
        Bukkit.broadcast(plugin.msg("big-event-planned", "&d✦ Big event planned: {event} &7- &f{when}&7. Be there!",
                "event", ItemManager.color(title(type)), "when", when(at)));
    }

    /** Where the event happens: the boss where the admin stands, the border around big-events.border.center. */
    private Location defaultLocation(String type, Player admin) {
        World main = Bukkit.getWorlds().getFirst();
        if (type.equals("boss") && admin != null) {
            return admin.getLocation();
        }
        if (type.equals("border")) {
            List<Double> c = cfg("border").getDoubleList("center");
            return new Location(main, c.size() > 0 ? c.get(0) : 0, 64, c.size() > 1 ? c.get(1) : 0);
        }
        return main.getSpawnLocation();
    }

    public void list(CommandSender sender) {
        if (running != null) {
            sender.sendMessage(ItemManager.color("&dRunning: " + title(running.type()) + " &7- &f/forge bigevent stop &7ends it without prizes"));
        }
        if (planned.isEmpty()) {
            sender.sendMessage(ItemManager.color("&7No big events planned. &f/forge bigevent plan <border|hunt|boss> <20:00 | 25.10 20:00 | +2h>"));
            return;
        }
        for (Planned p : planned.values()) {
            sender.sendMessage(ItemManager.color("&7- &f" + p.id() + " &7" + title(p.type()) + " &7at &f" + when(p.at())
                    + " &8(in " + MythicManager.format(p.at() - System.currentTimeMillis()) + ", " + p.world() + " "
                    + (int) p.x() + " " + (int) p.z() + ", by " + p.by() + ")"));
        }
    }

    public void cancel(CommandSender sender, String id) {
        Planned p = planned.remove(id);
        if (p == null) {
            sender.sendMessage(ItemManager.color("&cNo planned event with id " + id + "."));
            return;
        }
        announced.remove(id);
        save();
        hideCountdown();
        sender.sendMessage(ItemManager.color("&7Cancelled " + title(p.type()) + " &7at " + when(p.at()) + "."));
        Bukkit.broadcast(plugin.msg("big-event-cancelled", "&7The big event {event} &7at {when} was cancelled.",
                "event", ItemManager.color(title(p.type())), "when", when(p.at())));
    }

    public void startNow(CommandSender sender, String type) {
        if (!TYPES.contains(type)) {
            sender.sendMessage(ItemManager.color("&cUnknown event. Use: " + String.join(", ", TYPES)));
            return;
        }
        if (running != null) {
            sender.sendMessage(ItemManager.color("&cAnother big event is running."));
            return;
        }
        Location at = defaultLocation(type, sender instanceof Player p ? p : null);
        begin(new Planned("now", type, System.currentTimeMillis(), at.getWorld().getName(), at.getX(), at.getZ(), sender.getName()),
                sender);
    }

    public void stop(CommandSender sender) {
        if (running == null) {
            sender.sendMessage(ItemManager.color("&7No big event is running."));
            return;
        }
        plugin.getLogger().info("[BigEvent] " + sender.getName() + " stopped " + running.type());
        running.end(false);
        running = null;
        Bukkit.broadcast(plugin.msg("big-event-stopped", "&7The big event was stopped by an admin."));
    }

    public List<String> ids() {
        return new ArrayList<>(planned.keySet());
    }

    /** The next planned event, for the join message and the HUD. */
    public String nextText() {
        if (running != null) {
            return title(running.type()) + " &a(now!)";
        }
        return planned.values().stream().min(Comparator.comparingLong(Planned::at))
                .map(p -> title(p.type()) + " &7" + when(p.at())).orElse("");
    }

    // ================================================================ the clock

    private void tick() {
        long now = System.currentTimeMillis();
        if (running != null) {
            if (!running.tick()) {
                running = null;
            }
        }
        List<Integer> marks = plugin.getConfig().getIntegerList("big-events.announce-minutes");
        if (marks.isEmpty()) {
            marks = List.of(1440, 60, 10, 1);
        }
        Planned next = null;
        for (Planned p : new ArrayList<>(planned.values())) {
            long left = p.at() - now;
            if (left <= 0) {
                planned.remove(p.id());
                announced.remove(p.id());
                save();
                if (left < -10 * 60_000L) {
                    plugin.getLogger().info("[BigEvent] Missed " + p.type() + " planned for " + when(p.at()) + " (server was off)");
                } else if (running != null) {
                    plugin.getLogger().info("[BigEvent] Skipped " + p.type() + ": another event is running");
                } else {
                    begin(p, null);
                }
                continue;
            }
            Set<Integer> sent = announced.computeIfAbsent(p.id(), k -> new HashSet<>());
            for (int m : marks) {
                if (left <= m * 60_000L && left > (m * 60_000L) - 60_000L * Math.max(1, m / 10) && sent.add(m)) {
                    announce(p, m);
                }
                if (left <= m * 60_000L) {
                    sent.add(m);               // don't announce bigger marks late
                }
            }
            if (next == null || p.at() < next.at()) {
                next = p;
            }
        }
        // countdown bar in the last 10 minutes
        if (next != null && next.at() - now <= 600_000L && running == null) {
            if (countdown == null) {
                countdown = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10);
            }
            long left = next.at() - now;
            countdown.progress((float) Math.max(0, Math.min(1, left / 600_000.0)));
            countdown.name(ItemManager.color(title(next.type()) + " &7starts in &f" + clock(left)));
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showBossBar(countdown);
            }
        } else {
            hideCountdown();
        }
    }

    private void hideCountdown() {
        if (countdown != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(countdown);
            }
            countdown = null;
        }
    }

    static String clock(long ms) {
        long s = Math.max(0, ms / 1000);
        return s >= 3600 ? (s / 3600) + ":" + String.format("%02d:%02d", (s % 3600) / 60, s % 60)
                : (s / 60) + ":" + String.format("%02d", s % 60);
    }

    private void announce(Planned p, int minutes) {
        String in = minutes >= 1440 ? (minutes / 1440) + (minutes >= 2880 ? " days" : " day")
                : minutes >= 60 ? (minutes / 60) + (minutes >= 120 ? " hours" : " hour")
                : minutes + (minutes == 1 ? " minute" : " minutes");
        String hint = switch (p.type()) {
            case "border" -> "&7The border closes in on &f" + (int) p.x() + " " + (int) p.z() + "&7. Last one standing wins.";
            case "hunt" -> "&7Everyone shows on the locator bar. Most player kills wins.";
            default -> "&7A world boss near &f" + (int) p.x() + " " + (int) p.z() + "&7. Most damage wins.";
        };
        Bukkit.broadcast(plugin.msg("big-event-soon", "&d✦ {event} &7starts in &f{in} &8({when})&7. {hint}",
                "event", ItemManager.color(title(p.type())), "in", in, "when", when(p.at()), "hint", ItemManager.color(hint)));
        for (Player pl : Bukkit.getOnlinePlayers()) {
            pl.playSound(pl.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, minutes <= 1 ? 1.4f : 0.9f);
        }
    }

    private void begin(Planned p, CommandSender admin) {
        hideCountdown();
        World w = Bukkit.getWorld(p.world());
        if (w == null) {
            w = Bukkit.getWorlds().getFirst();
        }
        Running r = switch (p.type()) {
            case "border" -> new BorderRun(w, p.x(), p.z());
            case "hunt" -> new HuntRun();
            default -> new BossRun(new Location(w, p.x(), 0, p.z()));
        };
        String problem = r.begin();
        if (problem != null) {
            plugin.getLogger().info("[BigEvent] " + p.type() + " could not start: " + problem);
            Bukkit.broadcast(plugin.msg("big-event-failed", "&7{event} &7could not start: {why}",
                    "event", ItemManager.color(title(p.type())), "why", problem));
            if (admin != null) {
                admin.sendMessage(ItemManager.color("&c" + problem));
            }
            return;
        }
        running = r;
        plugin.getLogger().info("[BigEvent] " + p.type() + " started");
    }

    // ================================================================ shared bits

    private static boolean playing(Player p) {
        return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }

    private static String name(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "?" : n;
    }

    /** Prizes go to online players right away, the rest when they join. */
    private void reward(UUID id, int tokens, String mythic, int materials, String type) {
        reward(id, tokens, mythic, materials, type, 0);
    }

    private void reward(UUID id, int tokens, String mythic, int materials, String type, int totems) {
        Player p = Bukkit.getPlayer(id);
        if (p == null) {
            String base = "pending." + id + ".";
            data.set(base + "tokens", data.getInt(base + "tokens") + tokens);
            data.set(base + "materials", data.getInt(base + "materials") + materials);
            data.set(base + "totems", data.getInt(base + "totems") + totems);
            if (mythic != null) {
                List<String> list = new ArrayList<>(data.getStringList(base + "mythics"));
                list.add(mythic);
                data.set(base + "mythics", list);
            }
            plugin.saveYaml(data, file);
            return;
        }
        give(p, tokens, mythic, materials);
        giveTotems(p, totems);
        if (mythic != null) {
            plugin.award(p, "champion");
        }
    }

    private void giveTotems(Player p, int totems) {
        for (int i = 0; i < totems && plugin.totems() != null && plugin.bounty() != null; i++) {
            plugin.bounty().give(p, plugin.totems().item());
            p.sendMessage(plugin.msg("big-event-totem", "&d✦ &7You got a &6Totem of the Forge&7."));
        }
    }

    private void give(Player p, int tokens, String mythic, int materials) {
        if (tokens > 0 && plugin.bounty() != null) {
            plugin.bounty().give(p, plugin.bounty().token(tokens));
            p.sendMessage(plugin.msg("big-event-tokens", "&d✦ &7You got &e{tokens} Bounty Tokens&7.", "tokens", tokens));
        }
        for (int i = 0; i < materials && plugin.bounty() != null; i++) {
            List<ItemManager.Entry> mats = plugin.bounty().enabledMaterials();
            if (!mats.isEmpty()) {
                ItemManager.Entry m = mats.get(ThreadLocalRandom.current().nextInt(mats.size()));
                plugin.bounty().give(p, m.item.clone());
                p.sendMessage(plugin.msg("big-event-material", "&d✦ &7You got {item}&7.", "item", ItemManager.nameOf(m.item)));
            }
        }
        if (mythic != null) {
            ItemManager.Entry entry = plugin.items().get("weapon:" + mythic);
            if (entry != null && entry.mythic && plugin.mythics() != null) {
                if (plugin.bounty() != null) {
                    plugin.bounty().give(p, plugin.mythics().create(entry));
                } else {
                    p.getInventory().addItem(plugin.mythics().create(entry));
                }
                if (plugin.top() != null) {
                    plugin.top().add(p, "champions");
                }
                Bukkit.broadcast(plugin.msg("big-event-mythic", "&5✦ {player} &7won the mythic {item}&7!",
                        "player", p.getName(), "item", ItemManager.nameOf(entry.item)));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                plugin.getLogger().info("[BigEvent] " + p.getName() + " got the mythic " + entry.ref());
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        String base = "pending." + p.getUniqueId();
        if (data.isConfigurationSection(base)) {
            List<String> mythics = data.getStringList(base + ".mythics");
            int tokens = data.getInt(base + ".tokens");
            int materials = data.getInt(base + ".materials");
            int totems = data.getInt(base + ".totems");
            data.set(base, null);
            plugin.saveYaml(data, file);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                give(p, tokens, null, materials);
                giveTotems(p, totems);
                for (String m : mythics) {
                    give(p, 0, m, 0);
                    plugin.award(p, "champion");
                }
            }, 40L);
        }
        if (running != null) {
            running.join(p);
        } else if (!planned.isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> p.sendMessage(plugin.msg("big-event-next",
                    "&d✦ Next big event: {event}", "event", ItemManager.color(nextText()))), 60L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (running != null) {
            running.death(event.getEntity(), event.getEntity().getKiller());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (running != null) {
            running.leave(event.getPlayer());
        }
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        if (running != null) {
            running.leave(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBossHurt(EntityDamageEvent event) {
        if (running instanceof BossRun boss) {
            boss.hurt(event);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (running instanceof BossRun boss && boss.isOurs(event.getEntity()) && event.getTarget() != null
                && !(event.getTarget() instanceof Player)) {
            event.setCancelled(true);       // the boss and its minions only go for players
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (running instanceof BossRun boss) {
            boss.entityDeath(event);
        }
    }

    // ================================================================ running events

    private abstract class Running {
        final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        final Map<UUID, Integer> kills = new HashMap<>();
        long started;

        abstract String type();

        /** Sets the event up; returns why it can't start, or null. */
        abstract String begin();

        /** Every second; false = the event is over. */
        abstract boolean tick();

        /** Ends the event (with or without prizes) and puts the world back. */
        abstract void end(boolean prizes);

        void join(Player p) {
            p.showBossBar(bar);
        }

        void leave(Player p) {
        }

        void death(Player dead, Player killer) {
        }

        void showBar(Iterable<? extends Player> to) {
            for (Player p : to) {
                p.showBossBar(bar);
            }
        }

        void hideBar() {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(bar);
            }
        }

        void tokensToAll(Set<UUID> who, int tokens) {
            if (tokens > 0) {
                for (UUID id : who) {
                    reward(id, tokens, null, 0, type());
                }
            }
        }
    }

    // ---------------------------------------------------------------- The Closing World

    private final class BorderRun extends Running {
        final World world;
        final double cx;
        final double cz;
        final Set<UUID> alive = new LinkedHashSet<>();
        final Set<UUID> everyone = new LinkedHashSet<>();
        UUID lastDead;
        long shrinkMs;
        long endMs;

        BorderRun(World world, double cx, double cz) {
            this.world = world;
            this.cx = cx;
            this.cz = cz;
        }

        @Override
        String type() {
            return "border";
        }

        @Override
        String begin() {
            ConfigurationSection c = cfg("border");
            for (Player p : world.getPlayers()) {
                if (playing(p)) {
                    alive.add(p.getUniqueId());
                }
            }
            int min = Math.max(2, plugin.getConfig().getInt("big-events.min-players", 2));
            if (alive.size() < min) {
                return "not enough players in survival in " + world.getName() + " (" + alive.size() + "/" + min + ")";
            }
            everyone.addAll(alive);
            WorldBorder b = world.getWorldBorder();
            data.set("backup.border.world", world.getName());
            data.set("backup.border.x", b.getCenter().getX());
            data.set("backup.border.z", b.getCenter().getZ());
            data.set("backup.border.size", b.getSize());
            data.set("backup.border.damage", b.getDamageAmount());
            data.set("backup.border.buffer", b.getDamageBuffer());
            plugin.saveYaml(data, file);
            double start = Math.min(b.getSize(), c.getDouble("start-size", 2000));
            b.setCenter(cx, cz);
            b.setSize(start);
            b.setDamageAmount(c.getDouble("damage-per-block", 1.0));
            b.setDamageBuffer(0);
            shrinkMs = (long) (c.getDouble("shrink-minutes", 25) * 60_000L);
            endMs = shrinkMs + (long) (c.getDouble("final-minutes", 5) * 60_000L);
            b.changeSize(Math.max(10, c.getDouble("final-size", 100)), shrinkMs / 50);
            started = System.currentTimeMillis();
            bar.color(BossBar.Color.RED);
            showBar(Bukkit.getOnlinePlayers());
            Bukkit.broadcast(plugin.msg("border-start", "&c☠ The Closing World has begun! &7The border closes in on &f{x} {z}"
                    + " &7- {players} players, the last one standing wins.", "x", (int) cx, "z", (int) cz, "players", alive.size()));
            for (UUID id : alive) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.2f);
                }
            }
            return null;
        }

        @Override
        boolean tick() {
            long t = System.currentTimeMillis() - started;
            WorldBorder b = world.getWorldBorder();
            bar.progress((float) Math.max(0, Math.min(1, 1 - t / (double) endMs)));
            bar.name(ItemManager.color("&c&lThe Closing World &7- border &f" + (int) b.getSize() + " &7- &f" + alive.size()
                    + " &7left - &f" + clock(endMs - t)));
            if (alive.size() <= 1 || t >= endMs) {
                end(true);
                return false;
            }
            return true;
        }

        @Override
        void leave(Player p) {
            if (alive.remove(p.getUniqueId())) {
                lastDead = p.getUniqueId();
                Bukkit.broadcast(plugin.msg("border-out", "&c☠ {player} &7left the arena - &f{left} &7left.",
                        "player", p.getName(), "left", alive.size()));
            }
        }

        @Override
        void death(Player dead, Player killer) {
            if (killer != null && alive.contains(killer.getUniqueId())) {
                kills.merge(killer.getUniqueId(), 1, Integer::sum);
            }
            if (alive.remove(dead.getUniqueId())) {
                lastDead = dead.getUniqueId();
                Bukkit.broadcast(plugin.msg("border-dead", "&c☠ {player} &7is out - &f{left} &7left.",
                        "player", dead.getName(), "left", alive.size()));
            }
        }

        @Override
        void end(boolean prizes) {
            hideBar();
            WorldBorder b = world.getWorldBorder();
            b.setCenter(data.getDouble("backup.border.x"), data.getDouble("backup.border.z"));
            b.setSize(data.getDouble("backup.border.size", b.getMaxSize()));
            b.setDamageAmount(data.getDouble("backup.border.damage", 0.2));
            b.setDamageBuffer(data.getDouble("backup.border.buffer", 5));
            data.set("backup.border", null);
            plugin.saveYaml(data, file);
            if (!prizes) {
                return;
            }
            ConfigurationSection c = cfg("border");
            UUID winner = alive.stream().max(Comparator.comparingInt(id -> kills.getOrDefault(id, 0))).orElse(lastDead);
            if (winner == null) {
                return;
            }
            Bukkit.broadcast(plugin.msg("border-won", "&c☠ The Closing World is over! &e{player} &7is the last one standing.",
                    "player", name(winner)));
            plugin.getLogger().info("[BigEvent] border won by " + name(winner) + " (" + everyone.size() + " players, kills "
                    + kills.getOrDefault(winner, 0) + ")");
            reward(winner, c.getInt("winner-tokens", 10), c.getString("mythic", "worldsplitter"), 1, type(),
                    c.getInt("winner-totems", 1));
            Set<UUID> rest = new LinkedHashSet<>(everyone);
            rest.remove(winner);
            tokensToAll(rest, c.getInt("player-tokens", 2));
        }
    }

    // ---------------------------------------------------------------- The Hunt

    private final class HuntRun extends Running {
        final Map<String, Long> recent = new HashMap<>();
        final Set<UUID> everyone = new LinkedHashSet<>();
        long endMs;

        @Override
        String type() {
            return "hunt";
        }

        @Override
        String begin() {
            int playing = (int) Bukkit.getOnlinePlayers().stream().filter(BigEventManager::playing).count();
            int min = Math.max(2, plugin.getConfig().getInt("big-events.min-players", 2));
            if (playing < min) {
                return "not enough players in survival (" + playing + "/" + min + ")";
            }
            for (World w : Bukkit.getWorlds()) {
                Boolean before = w.getGameRuleValue(GameRules.LOCATOR_BAR);
                data.set("backup.locator." + w.getName(), before == null || before);
                w.setGameRule(GameRules.LOCATOR_BAR, true);
            }
            plugin.saveYaml(data, file);
            endMs = (long) (cfg("hunt").getDouble("minutes", 30) * 60_000L);
            started = System.currentTimeMillis();
            bar.color(BossBar.Color.YELLOW);
            showBar(Bukkit.getOnlinePlayers());
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (playing(p)) {
                    everyone.add(p.getUniqueId());
                }
                p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1f);
            }
            Bukkit.broadcast(plugin.msg("hunt-start", "&6⚔ The Hunt has begun! &7Everyone is on the locator bar for &f{minutes} "
                    + "minutes&7. The most player kills wins.", "minutes", (int) (endMs / 60_000L)));
            return null;
        }

        @Override
        void join(Player p) {
            super.join(p);
            if (playing(p)) {
                everyone.add(p.getUniqueId());
            }
        }

        @Override
        boolean tick() {
            long t = System.currentTimeMillis() - started;
            Map.Entry<UUID, Integer> lead = kills.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);
            bar.progress((float) Math.max(0, Math.min(1, 1 - t / (double) endMs)));
            bar.name(ItemManager.color("&6&lThe Hunt &7- &f" + clock(endMs - t) + (lead == null ? " &7- no kills yet"
                    : " &7- leader &e" + name(lead.getKey()) + " &7(" + lead.getValue() + ")")));
            if (t >= endMs) {
                end(true);
                return false;
            }
            return true;
        }

        @Override
        void death(Player dead, Player killer) {
            if (killer == null || killer.equals(dead) || !playing(killer)
                    || (plugin.teams() != null && plugin.teams().allies(killer, dead))) {
                return;
            }
            long now = System.currentTimeMillis();
            String pair = killer.getUniqueId() + ";" + dead.getUniqueId();
            Long last = recent.get(pair);
            if (last != null && now - last < cfg("hunt").getLong("same-victim-minutes", 5) * 60_000L) {
                killer.sendActionBar(plugin.raw("hunt-same", "&7The same victim again doesn't count yet."));
                return;
            }
            recent.put(pair, now);
            everyone.add(killer.getUniqueId());
            int n = kills.merge(killer.getUniqueId(), 1, Integer::sum);
            killer.sendActionBar(plugin.raw("hunt-kill", "&6⚔ Hunt kills: &f{kills}", "kills", n));
        }

        @Override
        void end(boolean prizes) {
            hideBar();
            for (World w : Bukkit.getWorlds()) {
                w.setGameRule(GameRules.LOCATOR_BAR, data.getBoolean("backup.locator." + w.getName(), true));
            }
            data.set("backup.locator", null);
            plugin.saveYaml(data, file);
            if (!prizes) {
                return;
            }
            List<Map.Entry<UUID, Integer>> top = kills.entrySet().stream()
                    .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed()).toList();
            if (top.isEmpty()) {
                Bukkit.broadcast(plugin.msg("hunt-none", "&6⚔ The Hunt is over - nobody got a kill."));
                plugin.getLogger().info("[BigEvent] hunt ended without kills");
                return;
            }
            StringBuilder podium = new StringBuilder();
            List<Integer> tokens = cfg("hunt").getIntegerList("top-tokens");
            for (int i = 0; i < Math.min(3, top.size()); i++) {
                Map.Entry<UUID, Integer> e = top.get(i);
                podium.append(i == 0 ? "" : "&7, ").append("&f").append(i + 1).append(". &e").append(name(e.getKey()))
                        .append(" &7(").append(e.getValue()).append(")");
                List<Integer> totems = cfg("hunt").getIntegerList("top-totems");
                reward(e.getKey(), i < tokens.size() ? tokens.get(i) : 0, i == 0 ? cfg("hunt").getString("mythic", "bloodfang") : null,
                        i == 0 ? 1 : 0, type(), i < totems.size() ? totems.get(i) : 0);
            }
            Bukkit.broadcast(plugin.msg("hunt-won", "&6⚔ The Hunt is over! {podium}", "podium", ItemManager.color(podium.toString())));
            plugin.getLogger().info("[BigEvent] hunt won by " + name(top.getFirst().getKey()) + " (" + top.getFirst().getValue() + " kills)");
            Set<UUID> rest = new LinkedHashSet<>(everyone);
            for (int i = 0; i < Math.min(3, top.size()); i++) {
                rest.remove(top.get(i).getKey());
            }
            tokensToAll(rest, cfg("hunt").getInt("player-tokens", 1));
        }
    }

    // ---------------------------------------------------------------- The Forge Colossus

    private final class BossRun extends Running {
        final Location home;
        WitherSkeleton boss;
        final Set<UUID> minions = new HashSet<>();
        final Map<UUID, Double> damage = new HashMap<>();
        double virtualMax;
        double virtualHp;
        long endMs;
        int nextAbility = 5;
        int seconds;
        boolean enraged;

        BossRun(Location home) {
            this.home = home;
        }

        @Override
        String type() {
            return "boss";
        }

        boolean isOurs(Entity e) {
            return e.equals(boss) || minions.contains(e.getUniqueId());
        }

        @Override
        String begin() {
            World w = home.getWorld();
            int y = w.getHighestBlockYAt(home.getBlockX(), home.getBlockZ()) + 1;
            home.setY(y);
            w.addPluginChunkTicket(home.getBlockX() >> 4, home.getBlockZ() >> 4, plugin);
            ConfigurationSection c = cfg("boss");
            long near = Bukkit.getOnlinePlayers().stream().filter(BigEventManager::playing).count();
            virtualMax = c.getDouble("health", 800) + c.getDouble("health-per-player", 200) * Math.max(1, near);
            virtualHp = virtualMax;
            boss = w.spawn(home, WitherSkeleton.class, s -> {
                s.customName(ItemManager.color(c.getString("name", "&4&lThe Forge Colossus")));
                s.setCustomNameVisible(true);
                s.setPersistent(true);
                s.setRemoveWhenFarAway(false);
                s.setCanPickupItems(false);
                s.getPersistentDataContainer().set(bossKey, PersistentDataType.BYTE, (byte) 1);
                set(s, Attribute.MAX_HEALTH, 1000);
                s.setHealth(1000);
                set(s, Attribute.SCALE, c.getDouble("scale", 2.5));
                set(s, Attribute.ATTACK_DAMAGE, c.getDouble("damage", 14));
                set(s, Attribute.KNOCKBACK_RESISTANCE, 1);
                set(s, Attribute.ARMOR, 12);
                set(s, Attribute.MOVEMENT_SPEED, 0.28);
                set(s, Attribute.FOLLOW_RANGE, 48);
                set(s, Attribute.STEP_HEIGHT, 1.5);
                EntityEquipment eq = s.getEquipment();
                eq.setItemInMainHand(new ItemStack(Material.NETHERITE_AXE));
                eq.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
                eq.setItemInMainHandDropChance(0);
                eq.setHelmetDropChance(0);
            });
            endMs = (long) (c.getDouble("minutes", 30) * 60_000L);
            started = System.currentTimeMillis();
            bar.color(BossBar.Color.RED);
            bar.overlay(BossBar.Overlay.NOTCHED_20);
            w.strikeLightningEffect(home);
            w.spawnParticle(Particle.LAVA, home, 80, 2, 1, 2, 0);
            Bukkit.broadcast(plugin.msg("boss-start", "&4☠ {boss} &7has risen at &f{x} {y} {z}&7 ({world})! Most damage wins.",
                    "boss", ItemManager.color(c.getString("name", "&4&lThe Forge Colossus")), "x", home.getBlockX(), "y", y,
                    "z", home.getBlockZ(), "world", w.getName()));
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_EMERGE, 1f, 0.7f);
            }
            return null;
        }

        private void set(LivingEntity e, Attribute a, double v) {
            AttributeInstance i = e.getAttribute(a);
            if (i != null) {
                i.setBaseValue(v);
            }
        }

        @Override
        boolean tick() {
            long t = System.currentTimeMillis() - started;
            if (boss == null || boss.isDead()) {
                return false;          // handled in entityDeath
            }
            if (!boss.isValid()) {
                if (t >= endMs) {
                    end(false);
                    return false;
                }
                return true;           // its chunk isn't loaded right now
            }
            bar.progress((float) Math.max(0, Math.min(1, virtualHp / virtualMax)));
            bar.name(ItemManager.color(cfg("boss").getString("name", "&4&lThe Forge Colossus") + " &7- &f"
                    + (int) Math.ceil(virtualHp) + " &7HP - &f" + clock(endMs - t)));
            for (Player p : Bukkit.getOnlinePlayers()) {
                boolean near = p.getWorld().equals(home.getWorld()) && p.getLocation().distanceSquared(boss.getLocation()) < 100 * 100;
                if (near) {
                    p.showBossBar(bar);
                } else {
                    p.hideBossBar(bar);
                }
            }
            if (t >= endMs) {
                Bukkit.broadcast(plugin.msg("boss-escaped", "&4☠ The Forge Colossus sank back into the earth. Nobody won."));
                plugin.getLogger().info("[BigEvent] boss escaped (time ran out, " + (int) virtualHp + " HP left)");
                end(false);
                return false;
            }
            // leash: it doesn't wander off
            if (boss.getLocation().distanceSquared(home) > 30 * 30 || boss.getLocation().getY() < home.getY() - 15) {
                boss.teleport(home);
                home.getWorld().spawnParticle(Particle.REVERSE_PORTAL, home, 60, 1, 2, 1, 0.1);
            }
            if (!enraged && virtualHp < virtualMax / 2) {
                enraged = true;
                set(boss, Attribute.MOVEMENT_SPEED, 0.34);
                boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 2f, 0.6f);
                Bukkit.broadcast(plugin.msg("boss-enraged", "&4☠ The Forge Colossus is enraged!"));
            }
            if (++seconds >= nextAbility) {
                seconds = 0;
                nextAbility = enraged ? 4 + ThreadLocalRandom.current().nextInt(3) : 6 + ThreadLocalRandom.current().nextInt(4);
                ability();
            }
            return true;
        }

        private List<Player> targets(double range) {
            List<Player> list = new ArrayList<>();
            for (Player p : boss.getWorld().getPlayers()) {
                if (playing(p) && !p.isDead() && p.getLocation().distanceSquared(boss.getLocation()) <= range * range) {
                    list.add(p);
                }
            }
            return list;
        }

        private void ability() {
            List<Player> close = targets(7);
            List<Player> all = targets(32);
            if (all.isEmpty()) {
                return;
            }
            Location at = boss.getLocation();
            World w = at.getWorld();
            int pick = ThreadLocalRandom.current().nextInt(4);
            if (pick == 0 && !close.isEmpty()) {
                // Slam: everyone close takes damage and flies
                w.playSound(at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2f, 0.5f);
                w.spawnParticle(Particle.EXPLOSION, at, 6, 2, 0.3, 2, 0);
                w.spawnParticle(Particle.BLOCK, at, 120, 3, 0.2, 3, 0, Material.NETHERRACK.createBlockData());
                for (Player p : close) {
                    DamageGuard.run(() -> p.damage(cfg("boss").getDouble("slam-damage", 8), boss));
                    Vector away = p.getLocation().toVector().subtract(at.toVector()).setY(0);
                    p.setVelocity((away.lengthSquared() < 0.01 ? new Vector(0, 0, 0) : away.normalize().multiply(1.2)).setY(0.9));
                }
            } else if (pick == 1 && minions.size() < 8) {
                // Summon: burning skeletons
                w.playSound(at, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 2f, 0.7f);
                for (int i = 0; i < 3; i++) {
                    Location spot = at.clone().add(ThreadLocalRandom.current().nextDouble(-4, 4), 0, ThreadLocalRandom.current().nextDouble(-4, 4));
                    spot.setY(w.getHighestBlockYAt(spot) + 1);
                    WitherSkeleton m = w.spawn(spot, WitherSkeleton.class, s -> {
                        s.getPersistentDataContainer().set(bossKey, PersistentDataType.BYTE, (byte) 1);
                        s.setPersistent(false);
                        s.getEquipment().setItemInMainHand(new ItemStack(Material.STONE_SWORD));
                        s.getEquipment().setItemInMainHandDropChance(0);
                    });
                    minions.add(m.getUniqueId());
                    w.spawnParticle(Particle.FLAME, spot, 20, 0.3, 0.8, 0.3, 0.02);
                }
            } else if (pick == 2) {
                // Fireballs at up to three players
                w.playSound(at, Sound.ENTITY_BLAZE_SHOOT, 2f, 0.6f);
                List<Player> shuffled = new ArrayList<>(all);
                java.util.Collections.shuffle(shuffled);
                for (Player p : shuffled.subList(0, Math.min(3, shuffled.size()))) {
                    Location eye = boss.getEyeLocation();
                    Vector dir = p.getEyeLocation().toVector().subtract(eye.toVector()).normalize();
                    w.spawn(eye.add(dir.clone().multiply(1.5)), SmallFireball.class, f -> {
                        f.setShooter(boss);
                        f.setDirection(dir);
                        f.setVelocity(dir.multiply(1.2));
                    });
                }
            } else {
                // Chains: pulls the ones keeping their distance
                List<Player> far = all.stream().filter(p -> p.getLocation().distanceSquared(at) > 9 * 9).toList();
                if (far.isEmpty()) {
                    return;
                }
                w.playSound(at, Sound.BLOCK_CHAIN_BREAK, 2f, 0.5f);
                for (Player p : far) {
                    Vector to = at.toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.6).setY(0.6);
                    p.setVelocity(to);
                    p.sendActionBar(plugin.raw("boss-pull", "&4The Colossus drags you in!"));
                    w.spawnParticle(Particle.SMOKE, p.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.02);
                }
            }
        }

        /** The boss's health is a bigger virtual pool; the real entity only dies when the pool is empty. */
        void hurt(EntityDamageEvent event) {
            if (boss == null || !event.getEntity().equals(boss)) {
                return;
            }
            EntityDamageEvent.DamageCause cause = event.getCause();
            if (cause == EntityDamageEvent.DamageCause.FALL || cause == EntityDamageEvent.DamageCause.SUFFOCATION
                    || cause == EntityDamageEvent.DamageCause.DROWNING || cause == EntityDamageEvent.DamageCause.LAVA
                    || cause == EntityDamageEvent.DamageCause.FIRE || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                    || cause == EntityDamageEvent.DamageCause.CRAMMING) {
                event.setCancelled(true);
                return;
            }
            Player p = event instanceof EntityDamageByEntityEvent byEntity ? attacker(byEntity.getDamager()) : null;
            if (p == null) {
                if (cause != EntityDamageEvent.DamageCause.KILL && cause != EntityDamageEvent.DamageCause.VOID) {
                    event.setCancelled(true);       // only players can hurt it
                }
                return;
            }
            if (virtualHp <= 0) {
                event.setCancelled(true);
                return;
            }
            double dealt = Math.min(virtualHp, event.getFinalDamage());
            if (playing(p)) {
                damage.merge(p.getUniqueId(), dealt, Double::sum);      // creative / spectator hits don't win prizes
            }
            virtualHp -= dealt;
            event.setDamage(0.0001);
            if (virtualHp > 0) {
                boss.setHealth(Math.max(1, 1000 * virtualHp / virtualMax));
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (boss.isValid() && !boss.isDead()) {
                        boss.setKiller(p);
                        boss.setHealth(0);
                    }
                });
            }
        }

        void entityDeath(EntityDeathEvent event) {
            Entity e = event.getEntity();
            if (minions.remove(e.getUniqueId())) {
                event.getDrops().clear();
                return;
            }
            if (boss == null || !e.equals(boss)) {
                return;
            }
            event.getDrops().clear();
            event.setDroppedExp(cfg("boss").getInt("xp", 1500));
            end(true);
            running = null;
        }

        @Override
        void end(boolean prizes) {
            hideBar();
            home.getWorld().removePluginChunkTicket(home.getBlockX() >> 4, home.getBlockZ() >> 4, plugin);
            for (UUID id : minions) {
                Entity m = Bukkit.getEntity(id);
                if (m != null) {
                    m.remove();
                }
            }
            minions.clear();
            if (boss != null && boss.isValid() && !boss.isDead()) {
                boss.getWorld().spawnParticle(Particle.LARGE_SMOKE, boss.getLocation(), 80, 1, 2, 1, 0.05);
                boss.remove();
            }
            if (!prizes) {
                return;
            }
            List<Map.Entry<UUID, Double>> top = damage.entrySet().stream()
                    .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed()).toList();
            if (top.isEmpty()) {
                return;
            }
            double total = top.stream().mapToDouble(Map.Entry::getValue).sum();
            ConfigurationSection c = cfg("boss");
            List<Integer> tokens = c.getIntegerList("top-tokens");
            StringBuilder podium = new StringBuilder();
            for (int i = 0; i < top.size(); i++) {
                Map.Entry<UUID, Double> e = top.get(i);
                int share = (int) Math.round(e.getValue() * 100 / total);
                if (i < 3) {
                    podium.append(i == 0 ? "" : "&7, ").append("&f").append(i + 1).append(". &e").append(name(e.getKey()))
                            .append(" &7(").append(share).append("%)");
                    List<Integer> totems = c.getIntegerList("top-totems");
                    reward(e.getKey(), i < tokens.size() ? tokens.get(i) : 0, i == 0 ? c.getString("mythic", "titans_echo") : null,
                            1, type(), i < totems.size() ? totems.get(i) : 0);
                } else if (share >= 2) {
                    reward(e.getKey(), c.getInt("player-tokens", 3), null,
                            ThreadLocalRandom.current().nextInt(100) < c.getInt("material-chance", 30) ? 1 : 0, type());
                }
            }
            Bukkit.broadcast(plugin.msg("boss-dead", "&4☠ The Forge Colossus has fallen! {podium}", "podium",
                    ItemManager.color(podium.toString())));
            plugin.getLogger().info("[BigEvent] boss killed - top damage " + name(top.getFirst().getKey()) + " ("
                    + (int) Math.round(top.getFirst().getValue() * 100 / total) + "%, " + top.size() + " players)");
        }
    }
}
