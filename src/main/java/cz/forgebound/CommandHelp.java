package cz.forgebound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Clickable command help: /help for players (everything a player can use), /forge help [category] for admins.
 * Clicking a line puts the command into the chat box; hovering shows what it does.
 */
final class CommandHelp {

    private CommandHelp() {
    }

    private record Line(String command, String description) {
    }

    private record Section(String title, String hover, List<Line> lines) {
    }

    // ---------------------------------------------------------------- players

    private static List<Section> playerSections() {
        List<Section> list = new ArrayList<>();
        list.add(new Section("&6⚒ Legends", "Forging, the codex and who holds what", List.of(
                new Line("/codex", "How everything works: weapons, relics, materials, rituals"),
                new Line("/legends", "All legends on the server and your stats with them"),
                new Line("/forge", "Shift + right-click a crafting table with an empty hand opens the forge"))));
        list.add(new Section("&e☠ Bounty & tokens", "Bounties, the shop and paying", List.of(
                new Line("/bounty", "Your bounty, the most wanted players and the token exchange"),
                new Line("/shop", "The token shop (fixed offers + a weekly one)"),
                new Line("/pay <player> <amount>", "Send Bounty Tokens to another player"))));
        list.add(new Section("&b✦ Events & quests", "What's going on and what to do", List.of(
                new Line("/events", "Events running right now"),
                new Line("/quests", "Your daily and weekly quests"),
                new Line("/top [category]", "Leaderboards"))));
        if (Bukkit.getPluginManager().isPluginEnabled("ForgeboundTeams")) {
            list.add(new Section("&a⚑ Teams", "Teams, allies and wars", List.of(
                    new Line("/team", "The team menu (create, invite, allies, wars, settings)"),
                    new Line("/team stash", "The shared chest for Bounty Tokens"),
                    new Line("/team top", "The team leaderboard"),
                    new Line("/tc [message]", "Team chat (or start a message with !)"),
                    new Line("/ac <message>", "Team + allies chat (or start a message with @)"))));
        }
        list.add(new Section("&7⚙ Other", "Small things", List.of(
                new Line("/hud", "Hide or show the sidebar"),
                new Line("/help", "This page"))));
        return list;
    }

    static void player(CommandSender to) {
        to.sendMessage(ItemManager.color("&8&m          &r &6&lFORGEBOUND &7- commands &8&m          "));
        for (Section s : playerSections()) {
            print(to, s);
        }
        if (to.hasPermission("forgebound.admin")) {
            to.sendMessage(ItemManager.color("&c» Admin commands: ")
                    .append(link("&f/forge help", "/forge help", "All admin commands by category")));
        }
        to.sendMessage(ItemManager.color("&8Click a command to put it into the chat, hover for what it does."));
    }

    // ---------------------------------------------------------------- admins

    private static Map<String, Section> adminSections() {
        Map<String, Section> map = new LinkedHashMap<>();
        map.put("legend", new Section("&6⚒ Legends", "Giving, releasing, stars and clean-up", List.of(
                new Line("/forge legend status", "Who holds which legend, what is being forged"),
                new Line("/forge legend give <player> <item> [amount]", "Give a weapon, relic or material (ignores uniqueness)"),
                new Line("/forge legend release <id>", "Make a legend forgeable again (its copies crumble)"),
                new Line("/forge legend release all-admin", "Release every legend given by an admin"),
                new Line("/forge legend finish <id>", "Finish a running ritual now"),
                new Line("/forge legend stars <player> <1-5>", "Set the stars of the weapon in their hand"),
                new Line("/forge legend purge <name>", "Wipe a player's leaderboard data (all their UUIDs)"))));
        map.put("item", new Section("&e✉ Items", "Special items for yourself", List.of(
                new Line("/forge item token [amount]", "Bounty Tokens"),
                new Line("/forge item seal", "A Warden seal"),
                new Line("/forge item tracker", "A Legend Tracker compass"),
                new Line("/forge item recon", "A Recon Spyglass"),
                new Line("/forge item totem", "A Totem of the Forge"))));
        map.put("world", new Section("&2⛰ World", "Biomes, forges and the spawn", List.of(
                new Line("/forge world locate <biome>", "Nearest Ember Crags / Glassed Plains / Ashen Forest / Shattered Peaks"),
                new Line("/forge world scorched", "Nearest Scorched Lands"),
                new Line("/forge world ancient", "Ancient Forges (list, tp)"),
                new Line("/forge world ancient here|end|finish|cool", "Build one here / raise the End forge / finish / reset cooldowns"),
                new Line("/forge world spawnbuild [confirm|undo|cancel]", "Build the spawn citadel"),
                new Line("/forge world npc set|remove", "The Questmaster"))));
        map.put("event", new Section("&b✦ Events", "Small and big events, the ghost", List.of(
                new Line("/forge event <name>", "Start blood_moon, rift, legend_hunt, happy_hour, forge_market, golden_shipment"),
                new Line("/forge event stop", "Stop all small events"),
                new Line("/forge event meteor", "Launch a meteor"),
                new Line("/forge event warden", "Wake the Forge Warden where you stand"),
                new Line("/forge event ghost [player|stop]", "Send the Doppelganger / stop it"),
                new Line("/forge event big [list|plan|start|cancel|stop]", "Big events"))));
        map.put("season", new Section("&d☀ Season", "The season ceremony", List.of(
                new Line("/forge season start", "Start the season (ceremony)"),
                new Line("/forge season preview", "Watch the ceremony without starting anything"),
                new Line("/forge season cancel", "Stop a running ceremony"))));
        map.put("server", new Section("&7⚙ Server", "Config, updates and enchantments", List.of(
                new Line("/forge server reload", "Reload the config"),
                new Line("/forge server update [now|on|off|restart|cancel]", "Updates and the nightly restart"),
                new Line("/forge server enchants [status|unlock|lock|reset]", "Custom enchantments"))));
        return map;
    }

    static List<String> adminCategories() {
        return new ArrayList<>(adminSections().keySet());
    }

    static void admin(CommandSender to, String category) {
        Map<String, Section> map = adminSections();
        if (category != null && map.containsKey(category.toLowerCase(java.util.Locale.ROOT))) {
            to.sendMessage(ItemManager.color("&8&m        &r &c&lFORGEBOUND ADMIN &8&m        "));
            print(to, map.get(category.toLowerCase(java.util.Locale.ROOT)));
            to.sendMessage(link("&8« all categories", "/forge help", "Back to the categories", true));
            return;
        }
        to.sendMessage(ItemManager.color("&8&m        &r &c&lFORGEBOUND ADMIN &8&m        "));
        Component row = Component.empty();
        boolean first = true;
        for (Map.Entry<String, Section> e : map.entrySet()) {
            if (!first) {
                row = row.append(ItemManager.color("&8  |  "));
            }
            first = false;
            row = row.append(link(e.getValue().title(), "/forge help " + e.getKey(), e.getValue().hover(), true));
        }
        to.sendMessage(row);
        to.sendMessage(ItemManager.color("&8Click a category. The old short forms (/forge give, /forge locate...) still work."));
    }

    // ---------------------------------------------------------------- printing

    private static void print(CommandSender to, Section s) {
        to.sendMessage(ItemManager.color(s.title()));
        for (Line l : s.lines()) {
            String typed = l.command().contains("<") || l.command().contains("[") || l.command().contains("|")
                    ? l.command().replaceAll(" [<\\[].*$", " ").replaceAll(" [a-z]+\\|.*$", " ")
                    : l.command();
            to.sendMessage(ItemManager.color(" &8• ").append(link("&f" + l.command(), typed, l.description()))
                    .append(ItemManager.color(" &8- &7" + l.description())));
        }
    }

    private static Component link(String text, String command, String hover) {
        return link(text, command, hover, false);
    }

    /** run = true runs the command on click (help pages), else it's put into the chat box. */
    private static Component link(String text, String command, String hover, boolean run) {
        return ItemManager.color(text)
                .hoverEvent(HoverEvent.showText(ItemManager.color("&7" + hover + "\n&8" + (run ? "Click to open" : "Click to type it"))))
                .clickEvent(run ? ClickEvent.runCommand(command) : ClickEvent.suggestCommand(command));
    }
}
