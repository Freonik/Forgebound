package cz.forgebound;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * The nightly update restart: at auto-update.restart.time every Forgebound plugin (Forgebound, Guard, Teams) checks
 * for a new version at once. If any of them downloaded one, the players get a countdown (default 10 min) and the
 * server restarts once for all of them; the host / start script starts it again with the new jars.
 */
public final class AutoRestart {

    private static final List<String> FAMILY = List.of("ForgeboundGuard", "ForgeboundTeams");

    private final Forgebound plugin;
    private BukkitTask clock;
    private BukkitTask countdown;
    private BossBar bar;
    private LocalDate lastRun;
    private volatile boolean checking;

    public AutoRestart(Forgebound plugin) {
        this.plugin = plugin;
    }

    private String cfg(String key, String def) {
        return plugin.getConfig().getString("auto-update.restart." + key, def);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("auto-update.restart.enabled", false);
    }

    private ZoneId zone() {
        try {
            String z = cfg("timezone", "Europe/Prague");
            return z.isBlank() ? ZoneId.systemDefault() : ZoneId.of(z);
        } catch (Exception ex) {
            return ZoneId.systemDefault();
        }
    }

    private LocalTime time() {
        try {
            return LocalTime.parse(cfg("time", "04:00"));
        } catch (Exception ex) {
            return LocalTime.of(4, 0);
        }
    }

    public void start() {
        stopClock();
        // once a minute: is it the restart time yet? (the server may lag, so "the minute has come and today didn't run")
        clock = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!enabled() || countdown != null || checking) {
                return;
            }
            ZonedDateTime now = ZonedDateTime.now(zone());
            LocalTime at = time();
            boolean due = !now.toLocalTime().isBefore(at) && now.toLocalTime().isBefore(at.plusMinutes(30));
            if (due && !now.toLocalDate().equals(lastRun)) {
                lastRun = now.toLocalDate();
                run(null, false);
            }
        }, 20L * 30, 20L * 60);
    }

    private void stopClock() {
        if (clock != null) {
            clock.cancel();
            clock = null;
        }
    }

    public void stop() {
        stopClock();
        cancelCountdown();
    }

    /**
     * Asks all three plugins for a new version (off the main thread), then starts the countdown if anything is waiting.
     * force = restart even without a new version (the admin test command).
     */
    private void run(CommandSender sender, boolean force) {
        checking = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String> found = new ArrayList<>();
            try {
                String own = plugin.updater().checkNow();
                if (own != null) {
                    found.add("Forgebound " + own);
                }
                for (RegisteredServiceProvider<Callable> reg : Bukkit.getServicesManager().getRegistrations(Callable.class)) {
                    String name = reg.getPlugin().getName();
                    if (!FAMILY.contains(name)) {
                        continue;
                    }
                    try {
                        Object v = reg.getProvider().call();
                        if (v instanceof String s && !s.isBlank()) {
                            found.add(name.replace("Forgebound", "Forgebound ") + " " + s);
                        }
                    } catch (Exception ex) {
                        plugin.getLogger().info("[Update] " + name + " couldn't check: " + ex.getClass().getSimpleName());
                    }
                }
            } finally {
                checking = false;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (found.isEmpty() && !force) {
                    plugin.getLogger().info("[Update] Nightly check: everything is up to date, no restart.");
                    if (sender != null) {
                        sender.sendMessage(ItemManager.color("&7Everything is up to date &8- no restart needed."));
                    }
                    return;
                }
                String what = found.isEmpty() ? "a test restart" : String.join(", ", found);
                plugin.getLogger().info("[Update] Restarting for: " + what);
                begin(what);
            });
        });
    }

    /** /forge update restart|cancel */
    public void command(CommandSender sender, String what) {
        if (what.equals("cancel")) {
            if (countdown == null) {
                sender.sendMessage(ItemManager.color("&7No restart is planned."));
                return;
            }
            cancelCountdown();
            Bukkit.broadcast(ItemManager.color("&8[&6Update&8] &aThe restart was cancelled."));
            plugin.getLogger().info("[Update] Restart cancelled by " + sender.getName());
            return;
        }
        if (countdown != null || checking) {
            sender.sendMessage(ItemManager.color("&7A restart is already on its way &8(/forge update cancel)"));
            return;
        }
        sender.sendMessage(ItemManager.color("&7Checking all Forgebound plugins for a new version..."));
        run(sender, true);
    }

    public String status() {
        return enabled() ? "&aon &7at &f" + cfg("time", "04:00") + " &8(" + zone().getId() + ")" : "&coff";
    }

    // ---------------------------------------------------------------- the countdown

    private void begin(String what) {
        int minutes = Math.max(1, plugin.getConfig().getInt("auto-update.restart.warn-minutes", 10));
        long total = minutes * 60L;
        long[] left = {total};
        long[] waited = {0};
        bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        Bukkit.broadcast(ItemManager.color("&8[&6Update&8] &eNew version (&f" + what + "&e) - the server restarts in &f"
                + minutes + " min&e. &7It'll be back in a minute."));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(bar);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);
        }
        countdown = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // a big event shouldn't be cut off: hold at one minute until it ends (at most an hour)
            if (left[0] == 60 && waited[0] < 3600 && plugin.bigEvents() != null && plugin.bigEvents().busy()) {
                waited[0]++;
                bar.name(ItemManager.color("&eRestart for an update &7- waiting for the big event to end"));
                return;
            }
            left[0]--;
            long s = Math.max(0, left[0]);
            bar.name(ItemManager.color("&eRestart for an update in &f" + (s >= 60 ? (s / 60) + ":" + String.format("%02d", s % 60) : s + " s")));
            bar.progress(Math.max(0f, Math.min(1f, s / (float) total)));
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showBossBar(bar);       // also for players who joined during the countdown
            }
            if (s == 300 || s == 120 || s == 60 || s == 30) {
                Bukkit.broadcast(ItemManager.color("&8[&6Update&8] &eRestart in &f" + (s >= 60 ? s / 60 + " min" : s + " s")));
            }
            if (s <= 10 && s > 0) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.showTitle(Title.title(ItemManager.color("&e" + s), ItemManager.color("&7Restarting for an update"),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO)));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.4f);
                }
            }
            if (s == 0) {
                restartNow();
            }
        }, 20L, 20L);
    }

    private void cancelCountdown() {
        if (countdown != null) {
            countdown.cancel();
            countdown = null;
        }
        if (bar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(bar);
            }
            bar = null;
        }
    }

    private void restartNow() {
        cancelCountdown();
        Component reason = ItemManager.color("&6Forgebound\n\n&eThe server is restarting for a new version.\n&7It'll be back in a minute.");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.kick(reason);
        }
        Bukkit.savePlayers();
        // spigot.yml restart-script (a start.sh / the host's restart); without one it just stops and the host starts it again
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            String mode = cfg("method", "restart");
            if (mode.equalsIgnoreCase("stop")) {
                Bukkit.shutdown();
            } else {
                Bukkit.spigot().restart();
            }
        }, 20L);
    }
}
