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
import java.util.Map;
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
            commands.register(Commands.literal("help").executes(ctx -> {
                        CommandHelp.player(ctx.getSource().getSender());
                        return Command.SINGLE_SUCCESS;
                    }).build(),
                    "All the commands of this server, clickable", List.of("pomoc", "commands", "prikazy"));
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

    /** Old flat /forge subcommands -> where they live now (they keep working, just hidden from the suggestions). */
    static final Map<String, String> LEGACY = Map.ofEntries(
            Map.entry("give", "legend give"), Map.entry("release", "legend release"), Map.entry("finish", "legend finish"),
            Map.entry("purge", "legend purge"), Map.entry("stars", "legend stars"), Map.entry("status", "legend status"),
            Map.entry("token", "item token"), Map.entry("seal", "item seal"), Map.entry("tracker", "item tracker"),
            Map.entry("recon", "item recon"), Map.entry("totem", "item totem"),
            Map.entry("locate", "world locate"), Map.entry("scorched", "world scorched"), Map.entry("ancient", "world ancient"),
            Map.entry("spawnbuild", "world spawnbuild"), Map.entry("npc", "world npc"),
            Map.entry("ghost", "event ghost"), Map.entry("meteor", "event meteor"), Map.entry("warden", "event warden"),
            Map.entry("bigevent", "event big"),
            Map.entry("reload", "server reload"), Map.entry("update", "server update"), Map.entry("enchants", "server enchants"));

    private static int ok() {
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<CommandSourceStack> forgeTree() {
        var root = Commands.literal("forge").executes(ctx -> {
            forge(ctx);
            if (admin(ctx.getSource())) {
                CommandHelp.admin(ctx.getSource().getSender(), null);
            }
            return ok();
        });
        root.then(Commands.literal("help").requires(ForgeCommands::admin)
                .executes(ctx -> {
                    CommandHelp.admin(ctx.getSource().getSender(), null);
                    return ok();
                })
                .then(Commands.argument("category", StringArgumentType.word()).suggests(suggest(CommandHelp::adminCategories))
                        .executes(ctx -> {
                            CommandHelp.admin(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "category"));
                            return ok();
                        })));
        root.then(legendTree());
        root.then(itemTree());
        root.then(worldTree());
        root.then(eventTree());
        var season = Commands.literal("season").requires(ForgeCommands::admin).executes(ctx -> forge(ctx, "season"));
        for (String sub : List.of("start", "preview", "cancel")) {
            season.then(Commands.literal(sub).executes(ctx -> forge(ctx, "season", sub)));
        }
        root.then(season);
        root.then(serverTree());
        // the old flat commands (/forge give, /forge locate...): not suggested any more, but still understood
        root.then(Commands.argument("old", StringArgumentType.greedyString()).requires(ForgeCommands::admin)
                .executes(ctx -> {
                    String line = StringArgumentType.getString(ctx, "old").trim();
                    String first = line.split(" ", 2)[0].toLowerCase(Locale.ROOT);
                    String rest = line.length() > first.length() ? line.substring(first.length()) : "";
                    String now = LEGACY.get(first);
                    if (now == null) {
                        ctx.getSource().getSender().sendMessage(ItemManager.color("&cUnknown command. &7/forge help"));
                    } else {
                        org.bukkit.Bukkit.dispatchCommand(ctx.getSource().getSender(), "forge " + now + rest);
                    }
                    return ok();
                }));
        return root;
    }

    // ---------------------------------------------------------------- /forge legend

    private LiteralArgumentBuilder<CommandSourceStack> legendTree() {
        var legend = Commands.literal("legend").requires(ForgeCommands::admin).executes(ctx -> {
            CommandHelp.admin(ctx.getSource().getSender(), "legend");
            return ok();
        });
        legend.then(Commands.literal("status").executes(ctx -> forge(ctx, "status")));
        legend.then(Commands.literal("give")
                .then(Commands.argument("player", ArgumentTypes.player())
                        .then(Commands.argument("item", ArgumentTypes.key()).suggests(suggest(() -> plugin.items().refs()))
                                .executes(ctx -> forge(ctx, "give", onePlayer(ctx, "player").getName(),
                                        ref(ctx.getArgument("item", Key.class))))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> forge(ctx, "give", onePlayer(ctx, "player").getName(),
                                                ref(ctx.getArgument("item", Key.class)),
                                                String.valueOf(IntegerArgumentType.getInteger(ctx, "amount"))))))));
        legend.then(Commands.literal("release")
                .then(Commands.literal("all-admin")
                        .executes(ctx -> {
                            Purge.releaseAdminGiven(plugin, ctx.getSource().getSender(), false);
                            return ok();
                        })
                        .then(Commands.literal("confirm").executes(ctx -> {
                            Purge.releaseAdminGiven(plugin, ctx.getSource().getSender(), true);
                            return ok();
                        })))
                .then(Commands.argument("legend", ArgumentTypes.key()).suggests(suggest(this::legendIds))
                        .executes(ctx -> forge(ctx, "release", ref(ctx.getArgument("legend", Key.class))))
                        .then(Commands.literal("confirm")
                                .executes(ctx -> forge(ctx, "release", ref(ctx.getArgument("legend", Key.class)), "confirm")))));
        legend.then(Commands.literal("finish")
                .then(Commands.argument("legend", ArgumentTypes.key()).suggests(suggest(this::legendIds))
                        .executes(ctx -> forge(ctx, "finish", ref(ctx.getArgument("legend", Key.class))))));
        legend.then(Commands.literal("purge")
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> {
                            Purge.purge(plugin, ctx.getSource().getSender(), StringArgumentType.getString(ctx, "name"), false);
                            return ok();
                        })
                        .then(Commands.literal("confirm").executes(ctx -> {
                            Purge.purge(plugin, ctx.getSource().getSender(), StringArgumentType.getString(ctx, "name"), true);
                            return ok();
                        }))));
        legend.then(Commands.literal("stars")
                .then(Commands.argument("player", ArgumentTypes.player())
                        .then(Commands.argument("stars", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(1, 5))
                                .executes(ctx -> {
                                    plugin.stars().setCommand(ctx.getSource().getSender(), onePlayer(ctx, "player"),
                                            com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "stars"));
                                    return ok();
                                }))));
        return legend;
    }

    // ---------------------------------------------------------------- /forge item

    private LiteralArgumentBuilder<CommandSourceStack> itemTree() {
        var item = Commands.literal("item").requires(ForgeCommands::admin).executes(ctx -> {
            CommandHelp.admin(ctx.getSource().getSender(), "item");
            return ok();
        });
        item.then(Commands.literal("token").executes(ctx -> forge(ctx, "token"))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                        .executes(ctx -> forge(ctx, "token", String.valueOf(IntegerArgumentType.getInteger(ctx, "amount"))))));
        for (String plain : List.of("seal", "tracker", "recon", "totem")) {
            item.then(Commands.literal(plain).executes(ctx -> forge(ctx, plain)));
        }
        return item;
    }

    // ---------------------------------------------------------------- /forge world

    private LiteralArgumentBuilder<CommandSourceStack> worldTree() {
        var world = Commands.literal("world").requires(ForgeCommands::admin).executes(ctx -> {
            CommandHelp.admin(ctx.getSource().getSender(), "world");
            return ok();
        });
        world.then(Commands.literal("locate")
                .then(Commands.argument("biome", StringArgumentType.word())
                        .suggests((ctx, b) -> {
                            BiomeLocator.NAMES.forEach(b::suggest);
                            return b.buildFuture();
                        })
                        .executes(ctx -> {
                            BiomeLocator.locate(plugin, ctx.getSource().getSender(),
                                    StringArgumentType.getString(ctx, "biome").toLowerCase(Locale.ROOT));
                            return ok();
                        })));
        world.then(Commands.literal("scorched").executes(ctx -> {
            plugin.scorched().locate(ctx.getSource().getSender());
            return ok();
        }));
        world.then(Commands.literal("ancient")
                .executes(ctx -> {
                    plugin.stars().list(ctx.getSource().getSender());
                    return ok();
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
                    return ok();
                }))
                .then(Commands.literal("finish").executes(ctx -> {
                    plugin.stars().finishAll(ctx.getSource().getSender());
                    return ok();
                }))
                .then(Commands.literal("cool").executes(ctx -> {
                    plugin.stars().resetCooldowns(ctx.getSource().getSender());
                    return ok();
                })));
        var spawnBuild = Commands.literal("spawnbuild").executes(ctx -> {
            plugin.spawnBuilder().command(ctx.getSource().getSender(), "");
            return ok();
        });
        for (String sub : List.of("confirm", "undo", "cancel")) {
            spawnBuild.then(Commands.literal(sub).executes(ctx -> {
                plugin.spawnBuilder().command(ctx.getSource().getSender(), sub);
                return ok();
            }));
        }
        world.then(spawnBuild);
        world.then(Commands.literal("npc")
                .then(Commands.literal("set").executes(ctx -> player(ctx, p -> plugin.quests().setNpc(p))))
                .then(Commands.literal("remove").executes(ctx -> {
                    plugin.quests().removeNpc(ctx.getSource().getSender());
                    return ok();
                })));
        return world;
    }

    // ---------------------------------------------------------------- /forge event

    private LiteralArgumentBuilder<CommandSourceStack> eventTree() {
        var event = Commands.literal("event").requires(ForgeCommands::admin).executes(ctx -> {
            CommandHelp.admin(ctx.getSource().getSender(), "event");
            return ok();
        });
        event.then(Commands.literal("ghost").executes(ctx -> forge(ctx, "ghost"))
                .then(Commands.literal("stop").executes(ctx -> forge(ctx, "ghost", "stop")))
                .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(ctx -> forge(ctx, "ghost", onePlayer(ctx, "player").getName()))));
        event.then(Commands.literal("meteor").executes(ctx -> forge(ctx, "meteor")));
        event.then(Commands.literal("warden").executes(ctx -> forge(ctx, "warden")));
        var big = Commands.literal("big").executes(ctx -> {
            plugin.bigEvents().list(ctx.getSource().getSender());
            return ok();
        });
        big.then(Commands.literal("plan")
                .then(Commands.argument("type", StringArgumentType.word()).suggests(suggest(() -> BigEventManager.TYPES))
                        .then(Commands.argument("time", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    plugin.bigEvents().plan(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "type"),
                                            StringArgumentType.getString(ctx, "time"));
                                    return ok();
                                }))));
        big.then(Commands.literal("start")
                .then(Commands.argument("type", StringArgumentType.word()).suggests(suggest(() -> BigEventManager.TYPES))
                        .executes(ctx -> {
                            plugin.bigEvents().startNow(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "type"));
                            return ok();
                        })));
        big.then(Commands.literal("cancel")
                .then(Commands.argument("id", StringArgumentType.word()).suggests(suggest(() -> plugin.bigEvents().ids()))
                        .executes(ctx -> {
                            plugin.bigEvents().cancel(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "id"));
                            return ok();
                        })));
        big.then(Commands.literal("stop").executes(ctx -> {
            plugin.bigEvents().stop(ctx.getSource().getSender());
            return ok();
        }));
        big.then(Commands.literal("list").executes(ctx -> {
            plugin.bigEvents().list(ctx.getSource().getSender());
            return ok();
        }));
        event.then(big);
        // small events: blood_moon, rift, legend_hunt, happy_hour... and stop
        event.then(Commands.argument("event", StringArgumentType.word()).suggests(suggest(EventManager::names))
                .executes(ctx -> forge(ctx, "event", StringArgumentType.getString(ctx, "event"))));
        return event;
    }

    // ---------------------------------------------------------------- /forge server

    private LiteralArgumentBuilder<CommandSourceStack> serverTree() {
        var server = Commands.literal("server").requires(ForgeCommands::admin).executes(ctx -> {
            CommandHelp.admin(ctx.getSource().getSender(), "server");
            return ok();
        });
        server.then(Commands.literal("reload").executes(ctx -> forge(ctx, "reload")));
        var update = Commands.literal("update").executes(ctx -> update(ctx, "now"));
        for (String sub : List.of("on", "off", "now", "restart", "cancel")) {
            update.then(Commands.literal(sub).executes(ctx -> update(ctx, sub)));
        }
        server.then(update);
        var enchants = Commands.literal("enchants").executes(ctx -> {
            plugin.customEnchants().command(ctx.getSource().getSender(), "status");
            return ok();
        });
        for (String sub : List.of("status", "unlock", "lock", "reset")) {
            enchants.then(Commands.literal(sub).executes(ctx -> {
                plugin.customEnchants().command(ctx.getSource().getSender(), sub);
                return ok();
            }));
        }
        server.then(enchants);
        return server;
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
