package cz.forgebound;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /forge                               - tells players to use a crafting table (the menu only opens there)
 * /forge server reload                        - reloads the config (admin)
 * /forge legend give <player> <ref> [amount]  - gives a weapon / relic, ignores uniqueness (admin)
 * /forge legend status                        - which weapons exist / are being forged (admin)
 * /forge legend release <id>                  - makes a weapon/relic forgeable again, stops its ritual (admin)
 * /forge legend finish <weapon>               - finishes a running ritual right now (admin)
 */
public final class RecipesCommand {

    private final Forgebound plugin;
    private final ItemManager items;
    private final RitualManager rituals;
    private final ForgeData data;

    public RecipesCommand(Forgebound plugin, ItemManager items, RitualManager rituals, ForgeData data) {
        this.plugin = plugin;
        this.items = items;
        this.rituals = rituals;
        this.data = data;
    }

    public boolean onCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.msg("forge-open-hint", "&7Shift + right-click a &fcrafting table &7with an empty hand to open the forge."));
            if (sender.hasPermission("forgebound.admin")) {
                sender.sendMessage(plugin.msg("usage", "&7Usage: &e/forge"));
            }
            return true;
        }
        if (!sender.hasPermission("forgebound.admin")) {
            sender.sendMessage(plugin.msg("no-permission", "&cYou don't have permission to do that."));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(plugin.msg("reloaded", "&aConfig reloaded.",
                        "weapons", items.all(ItemManager.Kind.WEAPON).size(),
                        "relics", items.all(ItemManager.Kind.RELIC).size()));
            }
            case "give" -> give(sender, args);
            case "status" -> status(sender);
            case "release" -> {
                String id = weaponId(sender, args);
                if (id != null && sender instanceof Player p && Dialogs.on(plugin, "release-confirm")
                        && data.claim(id) != null && !(args.length > 2 && args[2].equalsIgnoreCase("confirm"))) {
                    ForgeData.Claim claim = data.claim(id);
                    ItemManager.Entry entry = items.get(id);
                    List<io.papermc.paper.registry.data.dialog.body.DialogBody> body = new ArrayList<>();
                    if (entry != null) {
                        body.add(Dialogs.item(entry.item));
                    }
                    body.add(Dialogs.text("&7Owner: &f" + claim.name() + " &8(" + claim.source() + ")"));
                    body.add(Dialogs.text("&7It can be forged again and any running ritual stops. "
                            + "&cThe existing item is not taken away from its owner."));
                    Dialogs.confirm(plugin, p, "&c&lRelease " + id + "?", body, "&c&lRelease", "&7Cancel",
                            () -> onCommand(p, new String[] {"release", id, "confirm"}));
                    return true;
                }
                if (id != null) {
                    boolean had = rituals.release(id);
                    if (had && plugin.legends() != null) {
                        plugin.legends().revoke(id);
                    }
                    sender.sendMessage(plugin.msg(had ? "weapon-released" : "weapon-not-claimed",
                            had ? "&a{weapon} can be forged again." : "&7{weapon} wasn't claimed.", "weapon", id));
                }
            }
            case "finish" -> {
                String id = weaponId(sender, args);
                if (id != null) {
                    boolean ok = rituals.finishNow(id);
                    sender.sendMessage(plugin.msg(ok ? "ritual-finishing" : "ritual-none",
                            ok ? "&aFinishing the ritual for {weapon}..." : "&7No ritual is running for {weapon}.",
                            "weapon", id));
                }
            }
            case "event" -> {
                if (plugin.events() == null || args.length < 2) {
                    sender.sendMessage(ItemManager.color("&7Usage: &e/forge event <" + String.join("|", EventManager.names()) + ">"));
                } else if (args[1].equalsIgnoreCase("stop")) {
                    plugin.events().stopAll();
                    sender.sendMessage(ItemManager.color("&7All events stopped."));
                } else {
                    boolean ok = plugin.events().start(args[1], sender instanceof org.bukkit.entity.Player p ? p : null);
                    sender.sendMessage(ItemManager.color(ok ? "&aStarted " + args[1] + "."
                            : "&7Couldn't start " + args[1] + " (already running, turned off, or no good spot)."));
                }
            }
            case "season" -> {
                String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
                org.bukkit.entity.Player host = sender instanceof org.bukkit.entity.Player p ? p : null;
                switch (sub) {
                    case "start", "preview" -> {
                        if (!plugin.season().start(host, sub.equals("start"))) {
                            sender.sendMessage(ItemManager.color("&7A season ceremony is already running. &e/forge season cancel"));
                        }
                    }
                    case "cancel" -> {
                        plugin.season().cancel();
                        sender.sendMessage(ItemManager.color("&7Ceremony cancelled."));
                    }
                    default -> {
                        if (host != null && Dialogs.on(plugin, "season-panel")) {
                            seasonPanel(host);
                        } else {
                            sender.sendMessage(ItemManager.color(
                                    "&7Usage: &e/forge season <start|preview|cancel> &8(preview = everything but the teleport)"));
                        }
                    }
                }
            }
            case "ghost" -> {
                if (plugin.ghost() == null) {
                    return true;
                }
                if (args.length > 1 && args[1].equalsIgnoreCase("stop")) {
                    plugin.ghost().leave(true);
                    sender.sendMessage(ItemManager.color("&7The Doppelganger is gone."));
                    return true;
                }
                org.bukkit.entity.Player victim = args.length > 1 ? Bukkit.getPlayerExact(args[1])
                        : sender instanceof org.bukkit.entity.Player p ? p : null;
                if (victim == null) {
                    sender.sendMessage(ItemManager.color("&7Usage: &e/forge event ghost [player|stop]"));
                } else if (!plugin.ghost().forceSpawn(victim)) {
                    sender.sendMessage(ItemManager.color("&7The Doppelganger is already here."));
                }
            }
            case "meteor" -> {
                boolean ok = plugin.meteor() != null && plugin.meteor().launch();
                sender.sendMessage(ok ? ItemManager.color("&6☄ Meteor launched.")
                        : ItemManager.color("&7A meteor is already falling (or no landing spot was found)."));
            }
            case "warden" -> {
                if (!(sender instanceof org.bukkit.entity.Player player)) {
                    sender.sendMessage(ItemManager.color("&cOnly players can spawn the Warden."));
                } else if (plugin.warden() == null || plugin.warden().alive()) {
                    sender.sendMessage(ItemManager.color("&7The Forge Warden is already awake."));
                } else {
                    plugin.warden().spawnAt(player);
                }
            }
            case "recon" -> {
                if (sender instanceof org.bukkit.entity.Player player && plugin.recon() != null) {
                    plugin.bounty().give(player, plugin.recon().item());
                }
            }
            case "totem" -> {
                if (sender instanceof org.bukkit.entity.Player player && plugin.totems() != null) {
                    plugin.bounty().give(player, plugin.totems().item());
                }
            }
            case "token", "seal", "tracker" -> {
                if (!(sender instanceof org.bukkit.entity.Player player) || plugin.bounty() == null) {
                    sender.sendMessage(ItemManager.color("&cOnly players can do that."));
                } else {
                    String what = args[0].toLowerCase(Locale.ROOT);
                    int n = args.length > 1 ? Math.max(1, Math.min(64, parseInt(args[1]))) : 1;
                    plugin.bounty().give(player, what.equals("token") ? plugin.bounty().token(n)
                            : what.equals("seal") ? plugin.warden().seal() : plugin.bounty().tracker());
                }
            }
            default -> sender.sendMessage(plugin.msg("usage", "&7Usage: &e/forge"));
        }
        return true;
    }

    /** /forge season without arguments: a window with the theme and the start / preview / cancel buttons. */
    private void seasonPanel(Player player) {
        String current = plugin.getConfig().getString("season.theme", "forge").toLowerCase(Locale.ROOT);
        var themes = List.of(
                io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry.create(
                        "forge", ItemManager.color("&6Forge &7(fire, orange)"), !current.equals("soul")),
                io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry.create(
                        "soul", ItemManager.color("&bSoul &7(soul fire, blue)"), current.equals("soul")));
        var theme = io.papermc.paper.registry.data.dialog.input.DialogInput
                .singleOption("theme", ItemManager.color("&7Theme"), themes).width(220).build();
        java.util.function.BiConsumer<Player, String> run = (p, sub) -> onCommand(p, new String[] {"season", sub});
        var start = Dialogs.button(ItemManager.color("&6&lStart season"),
                ItemManager.color("&7The whole ceremony + everyone is teleported to a safe spot"),
                Dialogs.action(plugin, (p, view) -> {
                    useTheme(view.getText("theme"));
                    run.accept(p, "start");
                }));
        var preview = Dialogs.button(ItemManager.color("&ePreview"),
                ItemManager.color("&7Everything but the teleport"),
                Dialogs.action(plugin, (p, view) -> {
                    useTheme(view.getText("theme"));
                    run.accept(p, "preview");
                }));
        var cancel = Dialogs.button(ItemManager.color("&cCancel ceremony"), null,
                Dialogs.action(plugin, (p, view) -> run.accept(p, "cancel")));
        Dialogs.form(player, "&6&lSeason ceremony",
                List.of(Dialogs.text("&7Starts in the Overworld only. Players in the Nether or the End stay where they are.")),
                List.of(theme), List.of(start, preview, cancel), "&7Close", 1);
    }

    /** Theme picked in the panel - kept until the next reload (the config file isn't rewritten). */
    private void useTheme(String theme) {
        if (theme != null && (theme.equals("forge") || theme.equals("soul"))) {
            plugin.getConfig().set("season.theme", theme);
        }
    }

    private String weaponId(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.msg("usage", "&7Usage: &e/forge"));
            return null;
        }
        String raw = args[1].toLowerCase(Locale.ROOT);
        for (String ref : List.of(raw, "weapon:" + raw, "relic:" + raw)) {
            if (items.get(ref) != null || data.claim(ref) != null) {
                return ref;
            }
        }
        sender.sendMessage(plugin.msg("unknown-item", "&cUnknown item '{item}'.", "item", args[1]));
        return null;
    }

    private void status(CommandSender sender) {
        Map<String, Long> running = rituals.runningSnapshot();
        List<ItemManager.Entry> all = new ArrayList<>(items.all(ItemManager.Kind.WEAPON));
        all.addAll(items.all(ItemManager.Kind.RELIC));
        for (ItemManager.Entry weapon : all) {
            ForgeData.Claim claim = data.claim(weapon.ref());
            String state;
            if (running.containsKey(weapon.ref())) {
                state = "&5forging by " + (claim == null ? "?" : claim.name()) + " &8(" + RitualManager.format(running.get(weapon.ref())) + " left)";
            } else if (claim != null) {
                state = "&cexists &8- &7" + claim.name() + " &8(" + claim.source() + ")";
            } else {
                state = "&aavailable";
            }
            sender.sendMessage(ItemManager.color("&8- &f" + weapon.ref() + " &8: " + state));
        }
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.msg("usage", "&7Usage: &e/forge"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.msg("unknown-player", "&cPlayer '{player}' is not online.", "player", args[1]));
            return;
        }
        ItemManager.Entry entry = items.get(args[2]);
        if (entry == null) {
            sender.sendMessage(plugin.msg("unknown-item", "&cUnknown item '{item}'.", "item", args[2]));
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException ignored) {
                // keep 1
            }
        }

        ItemStack stack = entry.item.clone();
        stack.setAmount(amount);
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(stack);
        for (ItemStack rest : leftover.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), rest);
        }
        sender.sendMessage(plugin.msg("given", "&aGave &e{amount}x &a{item} &ato &e{player}&a.",
                "amount", amount, "item", ItemManager.nameOf(entry.item), "player", target.getName()));
    }


    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return 1;
        }
    }
}
