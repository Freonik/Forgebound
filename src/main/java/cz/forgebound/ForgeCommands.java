package cz.forgebound;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.key.Key;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * All commands, registered the modern way (Brigadier): the chat suggests players, items, events and
 * categories while typing and marks wrong arguments red before the command is sent.
 * The actual work stays in the managers; this class only describes the command shapes.
 */
final class ForgeCommands {

    private static final String ADMIN = "forgebound.admin";

    private final Forgebound plugin;
    private final RecipesCommand forge;

    ForgeCommands(Forgebound plugin, RecipesCommand forge) {
        this.plugin = plugin;
        this.forge = forge;
    }

    void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(forgeTree().build(), "Forge info and admin tools (the menu opens from a crafting table)",
                    List.of("recipes", "recepty"));
            commands.register(simple("legends", (s, a) -> plugin.legends().onCommand(s, a)).build(),
                    "All legends on the server and your own stats with them", List.of("legend"));
            commands.register(simple("bounty", (s, a) -> plugin.bounty().onCommand(s, a)).build(),
                    "Your bounty, the most wanted legend holders and the Bounty Token exchange",
                    List.of("bounties", "wanted"));
            commands.register(Commands.literal("shop").executes(ctx -> player(ctx, p -> plugin.shop().open(p))).build(),
                    "The token shop (fixed offers + a weekly rotating offer)", List.of("store"));
            commands.register(payTree().build(), "Send Bounty Tokens to another player", List.of());
            commands.register(simple("events", (s, a) -> plugin.events().onCommand(s, a)).build(),
                    "Which events are running right now", List.of("event"));
            commands.register(topTree().build(), "Leaderboards", List.of("leaderboard", "leaderboards"));
            commands.register(simple("codex", (s, a) -> plugin.codex().onCommand(s, a)).build(),
                    "Opens the Forge Codex (how everything works)", List.of("guide"));
            commands.register(Commands.literal("quests").executes(ctx -> player(ctx, p -> plugin.quests().open(p, false))).build(),
                    "Your daily and weekly quests (rewards are claimed at the Questmaster)", List.of("quest"));
            commands.register(simple("hud", (s, a) -> plugin.hud().onCommand(s, a)).build(),
                    "Hide or show your sidebar", List.of("sidebar"));
        });
    }

    // ================================================================ helpers

    /** A command without arguments that hands over to an old-style handler. */
    private static LiteralArgumentBuilder<CommandSourceStack> simple(String name, BiConsumer<CommandSender, String[]> handler) {
        return Commands.literal(name).executes(ctx -> {
            handler.accept(ctx.getSource().getSender(), new String[0]);
            return Command.SINGLE_SUCCESS;
        });
    }

    private int player(CommandContext<CommandSourceStack> ctx, java.util.function.Consumer<Player> action) {
        if (ctx.getSource().getSender() instanceof Player p) {
            action.accept(p);
        } else {
            ctx.getSource().getSender().sendMessage(plugin.msg("player-only", "&cOnly players can use this command."));
        }
        return Command.SINGLE_SUCCESS;
    }

    /** Runs "/forge args..." through the forge handler. */
    private int forge(CommandContext<CommandSourceStack> ctx, String... args) {
        forge.onCommand(ctx.getSource().getSender(), args);
        return Command.SINGLE_SUCCESS;
    }

    private static boolean admin(CommandSourceStack source) {
        return source.getSender().hasPermission(ADMIN);
    }

    private static Player onePlayer(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        return ctx.getArgument(name, PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst();
    }

    /** Suggests from a list, filtered by what's typed so far. */
    private static SuggestionProvider<CommandSourceStack> suggest(Supplier<Collection<String>> values) {
        return (ctx, builder) -> {
            String typed = builder.getRemainingLowerCase();
            for (String v : values.get()) {
                if (v.toLowerCase(Locale.ROOT).startsWith(typed) || v.toLowerCase(Locale.ROOT).contains(":" + typed)) {
                    builder.suggest(v);
                }
            }
            return builder.buildFuture();
        };
    }

    /** "weapon:echo_blade" stays as it is, a bare "echo_blade" (read as minecraft:echo_blade) loses the namespace. */
    static String ref(Key key) {
        return key.namespace().equals(Key.MINECRAFT_NAMESPACE) ? key.value() : key.asString();
    }

    private Collection<String> legendIds() {
        List<String> ids = new ArrayList<>();
        plugin.items().all(ItemManager.Kind.WEAPON).forEach(e -> ids.add(e.id));
        plugin.items().all(ItemManager.Kind.RELIC).forEach(e -> ids.add(e.id));
        return ids;
    }

    // ================================================================ /forge

    private LiteralArgumentBuilder<CommandSourceStack> forgeTree() {
        var root = Commands.literal("forge").executes(ctx -> forge(ctx));
        for (String plain : List.of("reload", "status", "meteor", "warden", "recon", "seal", "tracker", "totem")) {
            root.then(Commands.literal(plain).requires(ForgeCommands::admin).executes(ctx -> forge(ctx, plain)));
        }
        root.then(Commands.literal("give").requires(ForgeCommands::admin)
                .then(Commands.argument("player", ArgumentTypes.player())
                        .then(Commands.argument("item", ArgumentTypes.key()).suggests(suggest(() -> plugin.items().refs()))
                                .executes(ctx -> forge(ctx, "give", onePlayer(ctx, "player").getName(),
                                        ref(ctx.getArgument("item", Key.class))))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> forge(ctx, "give", onePlayer(ctx, "player").getName(),
                                                ref(ctx.getArgument("item", Key.class)),
                                                String.valueOf(IntegerArgumentType.getInteger(ctx, "amount"))))))));
        for (String sub : List.of("release", "finish")) {
            root.then(Commands.literal(sub).requires(ForgeCommands::admin)
                    .then(Commands.argument("legend", ArgumentTypes.key()).suggests(suggest(this::legendIds))
                            .executes(ctx -> forge(ctx, sub, ref(ctx.getArgument("legend", Key.class))))));
        }
        root.then(Commands.literal("event").requires(ForgeCommands::admin)
                .then(Commands.argument("event", StringArgumentType.word()).suggests(suggest(EventManager::names))
                        .executes(ctx -> forge(ctx, "event", StringArgumentType.getString(ctx, "event")))));
        var big = Commands.literal("bigevent").requires(ForgeCommands::admin).executes(ctx -> {
            plugin.bigEvents().list(ctx.getSource().getSender());
            return Command.SINGLE_SUCCESS;
        });
        big.then(Commands.literal("plan")
                .then(Commands.argument("type", StringArgumentType.word()).suggests(suggest(() -> BigEventManager.TYPES))
                        .then(Commands.argument("time", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    plugin.bigEvents().plan(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "type"),
                                            StringArgumentType.getString(ctx, "time"));
                                    return Command.SINGLE_SUCCESS;
                                }))));
        big.then(Commands.literal("start")
                .then(Commands.argument("type", StringArgumentType.word()).suggests(suggest(() -> BigEventManager.TYPES))
                        .executes(ctx -> {
                            plugin.bigEvents().startNow(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "type"));
                            return Command.SINGLE_SUCCESS;
                        })));
        big.then(Commands.literal("cancel")
                .then(Commands.argument("id", StringArgumentType.word()).suggests(suggest(() -> plugin.bigEvents().ids()))
                        .executes(ctx -> {
                            plugin.bigEvents().cancel(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "id"));
                            return Command.SINGLE_SUCCESS;
                        })));
        big.then(Commands.literal("stop").executes(ctx -> {
            plugin.bigEvents().stop(ctx.getSource().getSender());
            return Command.SINGLE_SUCCESS;
        }));
        big.then(Commands.literal("list").executes(ctx -> {
            plugin.bigEvents().list(ctx.getSource().getSender());
            return Command.SINGLE_SUCCESS;
        }));
        root.then(big);
        root.then(Commands.literal("npc").requires(ForgeCommands::admin)
                .then(Commands.literal("set").executes(ctx -> player(ctx, p -> plugin.quests().setNpc(p))))
                .then(Commands.literal("remove").executes(ctx -> {
                    plugin.quests().removeNpc(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })));
        var season = Commands.literal("season").requires(ForgeCommands::admin).executes(ctx -> forge(ctx, "season"));
        for (String sub : List.of("start", "preview", "cancel")) {
            season.then(Commands.literal(sub).executes(ctx -> forge(ctx, "season", sub)));
        }
        root.then(season);
        root.then(Commands.literal("ghost").requires(ForgeCommands::admin).executes(ctx -> forge(ctx, "ghost"))
                .then(Commands.literal("stop").executes(ctx -> forge(ctx, "ghost", "stop")))
                .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(ctx -> forge(ctx, "ghost", onePlayer(ctx, "player").getName()))));
        root.then(Commands.literal("scorched").requires(ForgeCommands::admin).executes(ctx -> {
            plugin.scorched().locate(ctx.getSource().getSender());
            return Command.SINGLE_SUCCESS;
        }));
        root.then(Commands.literal("ancient").requires(ForgeCommands::admin)
                .executes(ctx -> {
                    plugin.stars().list(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("here").executes(ctx -> player(ctx, p -> plugin.stars().buildHere(p))))
                .then(Commands.literal("end").executes(ctx -> {
                    org.bukkit.World end = org.bukkit.Bukkit.getWorlds().stream()
                            .filter(w -> w.getEnvironment() == org.bukkit.World.Environment.THE_END).findFirst().orElse(null);
                    if (end == null) {
                        ctx.getSource().getSender().sendMessage(ItemManager.color("&cThere is no End world."));
                    } else {
                        plugin.stars().raiseEnd(end, ctx.getSource().getSender());
                    }
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("finish").executes(ctx -> {
                    plugin.stars().finishAll(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("cool").executes(ctx -> {
                    plugin.stars().resetCooldowns(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })));
        root.then(Commands.literal("stars").requires(ForgeCommands::admin)
                .then(Commands.argument("player", ArgumentTypes.player())
                        .then(Commands.argument("stars", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(1, 5))
                                .executes(ctx -> {
                                    plugin.stars().setCommand(ctx.getSource().getSender(), onePlayer(ctx, "player"),
                                            com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "stars"));
                                    return Command.SINGLE_SUCCESS;
                                }))));
        var update = Commands.literal("update").requires(ForgeCommands::admin)
                .executes(ctx -> update(ctx, "now"));
        for (String sub : List.of("on", "off", "now", "restart", "cancel")) {
            update.then(Commands.literal(sub).executes(ctx -> update(ctx, sub)));
        }
        root.then(update);
        root.then(Commands.literal("token").requires(ForgeCommands::admin).executes(ctx -> forge(ctx, "token"))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                        .executes(ctx -> forge(ctx, "token", String.valueOf(IntegerArgumentType.getInteger(ctx, "amount"))))));
        return root;
    }

    private int update(CommandContext<CommandSourceStack> ctx, String what) {
        if (what.equals("restart") || what.equals("cancel")) {
            plugin.autoRestart().command(ctx.getSource().getSender(), what);
            return Command.SINGLE_SUCCESS;
        }
        plugin.updater().command(ctx.getSource().getSender(), what);
        return Command.SINGLE_SUCCESS;
    }

    // ================================================================ /pay, /top

    private LiteralArgumentBuilder<CommandSourceStack> payTree() {
        return Commands.literal("pay")
                .executes(ctx -> player(ctx, p -> plugin.shop().payDialog(p)))
                .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(ctx -> player(ctx, p -> p.sendMessage(plugin.msg("pay-usage", "&7Usage: &e/pay <player> <amount>"))))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    Player target = onePlayer(ctx, "player");
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                    return player(ctx, p -> plugin.shop().pay(p, target, amount, false));
                                })));
    }

    private LiteralArgumentBuilder<CommandSourceStack> topTree() {
        return Commands.literal("top")
                .executes(ctx -> {
                    plugin.top().onCommand(ctx.getSource().getSender(), new String[0]);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("category", StringArgumentType.word())
                        .suggests(suggest(TopManager.CATEGORIES::keySet))
                        .executes(ctx -> {
                            plugin.top().onCommand(ctx.getSource().getSender(),
                                    new String[] {StringArgumentType.getString(ctx, "category")});
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}
