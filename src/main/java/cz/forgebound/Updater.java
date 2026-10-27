package cz.forgebound;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auto-update from the GitHub releases:
 * - a newer Forgebound.jar is downloaded into plugins/update/ (Paper swaps it in on the next restart)
 * - server.properties gets the resource pack link of that exact release and its SHA1, so the new plugin and
 *   the new pack switch over together at the restart (only if the pack line already points to this repository)
 * Nothing is replaced while the server runs. Admins (forgebound.admin) are told what happened.
 */
public final class Updater implements Listener {

    private static final Pattern SHA1_HEX = Pattern.compile("[0-9a-fA-F]{40}");

    private final Forgebound plugin;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private BukkitTask task;
    /** what admins should hear about on join (e.g. "restart to update") */
    private volatile String pending;
    private volatile String downloaded;
    /** why the last check didn't work, or null */
    private volatile String lastError;

    public Updater(Forgebound plugin) {
        this.plugin = plugin;
    }

    private String cfg(String key, String def) {
        return plugin.getConfig().getString("auto-update." + key, def);
    }

    private boolean on(String key, boolean def) {
        return plugin.getConfig().getBoolean("auto-update." + key, def);
    }

    public void start() {
        stop();
        if (!on("enabled", true)) {
            return;
        }
        long every = Math.max(1, plugin.getConfig().getInt("auto-update.check-hours", 6)) * 3600L * 20L;
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::check, 20L * 60, every);
    }

    /** /forge update on|off|now */
    public void command(org.bukkit.command.CommandSender sender, String what) {
        switch (what) {
            case "on", "off" -> {
                plugin.getConfig().set("auto-update.enabled", what.equals("on"));
                plugin.saveConfig();
                start();
                sender.sendMessage(ItemManager.color(what.equals("on")
                        ? "&aAuto-update on &7(checks every " + plugin.getConfig().getInt("auto-update.check-hours", 6) + " h)."
                        : "&7Auto-update off. &8Nothing will be downloaded."));
            }
            default -> {
                sender.sendMessage(ItemManager.color("&7Checking for a new version..."));
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    String before = pending;
                    check();
                    String after = pending;
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(ItemManager.color(
                            after != null && !after.equals(before) ? "&8[&6Update&8] " + after
                                    : downloaded != null ? "&aVersion &f" + downloaded + " &ais already downloaded, restart to use it."
                                    : lastError != null ? "&cCouldn't check for updates: &7" + lastError
                                    : "&7You have the newest version &8(" + plugin.getPluginMeta().getVersion() + ")")));
                });
            }
        }
    }

    public boolean enabled() {
        return on("enabled", true);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** For the nightly restart: checks right now (blocking, off the main thread); the version waiting in plugins/update or null. */
    public String checkNow() {
        if (on("enabled", true)) {
            check();
        }
        return downloaded;
    }

    /** Where the releases are published (the code repository is private); old configs still name the code repo. */
    static final String RELEASES = "Freonik/Forgebound-releases";
    private static final List<String> OLD_REPOS = List.of("Freonik/Forgebound");

    private String releasesRepo() {
        String repo = cfg("repo", RELEASES);
        return OLD_REPOS.stream().anyMatch(repo::equalsIgnoreCase) ? RELEASES : repo;
    }

    /**
     * The newest versions of all Forgebound plugins: latest.json in the releases repository (raw.githubusercontent.com,
     * no API rate limit). Null if it can't be read.
     */
    static JsonObject manifest(HttpClient http, String repo, String agent) throws IOException, InterruptedException {
        HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(
                        "https://raw.githubusercontent.com/" + repo + "/main/latest.json?t=" + System.currentTimeMillis() / 60_000))
                .timeout(Duration.ofSeconds(20)).header("User-Agent", agent).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IOException("latest.json: HTTP " + res.statusCode());
        }
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    /** Off the main thread. */
    private void check() {
        try {
            JsonObject all = manifest(http, releasesRepo(), "Forgebound");
            if (!all.has("Forgebound")) {
                fail("latest.json has no Forgebound entry");
                return;
            }
            lastError = null;
            JsonObject me = all.getAsJsonObject("Forgebound");
            String latest = version(str(me, "version"));
            if (latest == null) {
                return;
            }
            int cmp = compare(latest, plugin.getPluginMeta().getVersion());
            if (cmp > 0) {
                update(latest, me);
            } else if (cmp == 0 && on("resource-pack", true) && pinPack(me)) {
                tell("&7server.properties now points to the resource pack of &f" + str(me, "tag")
                        + "&7 with its SHA1 &8(used after the next restart)");
            }
        } catch (Exception ex) {
            fail(ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : ": " + ex.getMessage()));
        }
    }

    /** Logs a failed check once (not every hour the same thing). */
    private void fail(String why) {
        if (!why.equals(lastError)) {
            plugin.getLogger().info("[Update] Couldn't check for a new version: " + why);
        }
        lastError = why;
    }

    private void update(String version, JsonObject me) throws IOException, InterruptedException {
        if (version.equals(downloaded)) {
            return;
        }
        if (!on("download", true)) {
            tell("&eForgebound &f" + version + " &eis out &8(auto-update.download is off)");
            return;
        }
        String url = str(me, "jar");
        if (url == null) {
            return;
        }
        File folder = Bukkit.getUpdateFolderFile();
        Files.createDirectories(folder.toPath());
        // the update folder replaces the jar with the same file name
        File target = new File(folder, plugin.jarFile().getName());
        Path tmp = target.toPath().resolveSibling(target.getName() + ".part");
        HttpResponse<Path> res = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2))
                .header("User-Agent", "Forgebound").GET().build(), HttpResponse.BodyHandlers.ofFile(tmp));
        if (res.statusCode() != 200 || Files.size(tmp) < 10_000) {
            Files.deleteIfExists(tmp);
            return;
        }
        Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        downloaded = version;
        boolean pack = on("resource-pack", true) && pinPack(me);
        tell("&aForgebound &f" + version + " &ais downloaded" + (pack ? " &7(+ the new resource pack)" : "")
                + "&a. &7It's used after the next &frestart&7.");
    }

    /**
     * Puts the pack link of this release and its SHA1 into server.properties. Only touches a pack line that already
     * points to a Forgebound repository (a custom pack stays). Returns true if the file was changed.
     */
    private boolean pinPack(JsonObject me) throws IOException {
        String packUrl = str(me, "pack");
        String sha1 = str(me, "pack_sha1");
        if (packUrl == null || sha1 == null || !SHA1_HEX.matcher(sha1).matches()) {
            return false;
        }
        Path file = Path.of("server.properties");
        if (!Files.isRegularFile(file)) {
            return false;
        }
        String result = rewrite(Files.readString(file, StandardCharsets.ISO_8859_1), releasesRepo(), packUrl, sha1.toLowerCase());
        if (result == null) {
            return false;
        }
        Path tmp = file.resolveSibling("server.properties.tmp");
        Files.writeString(tmp, result, StandardCharsets.ISO_8859_1);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    /** New server.properties text, or null if nothing needs to change / the pack isn't ours. */
    static String rewrite(String properties, String repo, String url, String sha1) {
        List<String> out = new ArrayList<>();
        boolean ours = false;
        boolean changed = false;
        boolean hasSha = false;
        for (String line : properties.split("\r?\n", -1)) {
            if (line.startsWith("resource-pack=")) {
                String value = line.substring("resource-pack=".length()).replace("\\:", ":");
                String v = value.toLowerCase();
                ours = v.contains("github.com/" + repo.toLowerCase() + "/releases/")
                        || OLD_REPOS.stream().anyMatch(r -> v.contains("github.com/" + r.toLowerCase() + "/releases/"));
                if (ours && !value.equals(url)) {
                    line = "resource-pack=" + url.replace(":", "\\:");
                    changed = true;
                }
            }
            out.add(line);
        }
        if (!ours) {
            return null;
        }
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i).startsWith("resource-pack-sha1=")) {
                hasSha = true;
                if (!out.get(i).equalsIgnoreCase("resource-pack-sha1=" + sha1)) {
                    out.set(i, "resource-pack-sha1=" + sha1);
                    changed = true;
                }
            }
        }
        if (!hasSha) {
            int at = out.size() > 0 && out.getLast().isEmpty() ? out.size() - 1 : out.size();
            out.add(at, "resource-pack-sha1=" + sha1);
            changed = true;
        }
        return changed ? String.join("\n", out) : null;
    }

    /** "v2.7.0-build.65" -> "2.7.0" */
    static String version(String tag) {
        Matcher m = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)").matcher(tag);
        return m.find() ? m.group() : null;
    }

    /** semantic compare of "x.y.z" (anything after the numbers is ignored) */
    static int compare(String a, String b) {
        String va = version(a);
        String vb = version(b);
        if (va == null || vb == null) {
            return 0;
        }
        String[] x = va.split("\\.");
        String[] y = vb.split("\\.");
        for (int i = 0; i < 3; i++) {
            int c = Integer.compare(Integer.parseInt(x[i]), Integer.parseInt(y[i]));
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    private void tell(String legacy) {
        pending = legacy;
        plugin.getLogger().info(PlainText.of(legacy));
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("forgebound.admin")) {
                    p.sendMessage(ItemManager.color("&8[&6Update&8] " + legacy));
                }
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        String msg = pending;
        if (msg != null && event.getPlayer().hasPermission("forgebound.admin")) {
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    event.getPlayer().sendMessage(ItemManager.color("&8[&6Update&8] " + msg)), 60L);
        }
    }

    /** "&aText" -> "Text" for the console */
    private static final class PlainText {
        static String of(String legacy) {
            return legacy.replaceAll("&[0-9a-fk-orA-FK-OR]", "");
        }
    }
}
