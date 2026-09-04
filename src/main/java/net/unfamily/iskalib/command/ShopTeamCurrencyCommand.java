package net.unfamily.iskalib.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.Entity;
import net.unfamily.iskalib.shop.ShopCurrencyHooks;
import net.unfamily.iskalib.team.ShopTeamManager;

import java.util.Collection;
import java.util.List;

/**
 * Currency management under {@code /iska_lib_team currency}.
 */
public final class ShopTeamCurrencyCommand {
    private ShopTeamCurrencyCommand() {}

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_CURRENCIES = (context, builder) ->
            SharedSuggestionProvider.suggest(ShopCurrencyHooks.getListener().listCurrencyIds(), builder);

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_TEAMS = (context, builder) ->
            SharedSuggestionProvider.suggest(manager(context.getSource()).getAllTeamNames(), builder);

    public static ArgumentBuilder<CommandSourceStack, ?> currencyLiteral() {
        return Commands.literal("currency")
                .then(Commands.literal("list")
                        .executes(ShopTeamCurrencyCommand::listCurrencies))
                .then(Commands.literal("add")
                        .requires(ShopTeamCurrencyCommand::isAdmin)
                        .then(mutationArguments(true)))
                .then(Commands.literal("remove")
                        .requires(ShopTeamCurrencyCommand::isAdmin)
                        .then(mutationArguments(false)))
                .then(Commands.literal("set")
                        .requires(ShopTeamCurrencyCommand::isAdmin)
                        .then(Commands.argument("currencyId", StringArgumentType.word())
                                .suggests(SUGGEST_CURRENCIES)
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0))
                                        .executes(ShopTeamCurrencyCommand::setForOwnTeam)
                                        .then(Commands.literal("team")
                                                .then(Commands.argument("teamName", StringArgumentType.word())
                                                        .suggests(SUGGEST_TEAMS)
                                                        .executes(ShopTeamCurrencyCommand::setForTeam)))
                                        .then(Commands.literal("player")
                                                .then(Commands.argument("player", EntityArgument.entities())
                                                        .executes(ShopTeamCurrencyCommand::setForPlayerTeams))))))
                .then(Commands.literal("move")
                        .requires(ShopTeamCurrencyCommand::isAdmin)
                        .then(Commands.argument("currencyId", StringArgumentType.word())
                                .suggests(SUGGEST_CURRENCIES)
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0))
                                        .then(Commands.argument("toTeam", StringArgumentType.word())
                                                .suggests(SUGGEST_TEAMS)
                                                .executes(ShopTeamCurrencyCommand::moveFromOwnTeam)
                                                .then(Commands.argument("fromTeam", StringArgumentType.word())
                                                        .suggests(SUGGEST_TEAMS)
                                                        .executes(ShopTeamCurrencyCommand::moveBetweenTeams))))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> mutationArguments(boolean add) {
        return Commands.argument("currencyId", StringArgumentType.word())
                .suggests(SUGGEST_CURRENCIES)
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0))
                        .executes(add ? ShopTeamCurrencyCommand::addToOwnTeam : ShopTeamCurrencyCommand::removeFromOwnTeam)
                        .then(Commands.literal("team")
                                .then(Commands.argument("teamName", StringArgumentType.word())
                                        .suggests(SUGGEST_TEAMS)
                                        .executes(add ? ShopTeamCurrencyCommand::addToTeam : ShopTeamCurrencyCommand::removeFromTeam)))
                        .then(Commands.literal("player")
                                .then(Commands.argument("player", EntityArgument.entities())
                                        .executes(add ? ShopTeamCurrencyCommand::addToPlayerTeams : ShopTeamCurrencyCommand::removeFromPlayerTeams))));
    }

    private static boolean isAdmin(CommandSourceStack source) {
        return source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(2)));
    }

    private static ShopTeamManager manager(CommandSourceStack source) {
        return ShopTeamManager.getInstance(source.getLevel());
    }

    private static int listCurrencies(CommandContext<CommandSourceStack> context) {
        List<String> ids = ShopCurrencyHooks.getListener().listCurrencyIds();
        if (ids.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.iska_lib.team.currency.empty"), false);
            return 1;
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.iska_lib.team.currency.header"), false);
        for (String id : ids) {
            context.getSource().sendSuccess(() -> Component.literal("- " + id + ": " + currencyDisplay(id)), false);
        }
        return ids.size();
    }

    private static int addToOwnTeam(CommandContext<CommandSourceStack> context) {
        return mutateOwn(context, true);
    }

    private static int removeFromOwnTeam(CommandContext<CommandSourceStack> context) {
        return mutateOwn(context, false);
    }

    private static int mutateOwn(CommandContext<CommandSourceStack> context, boolean add) {
        ServerPlayer player = requirePlayer(context.getSource());
        if (player == null) return 0;
        ShopTeamManager manager = manager(context.getSource());
        String teamKey = manager.getPlayerTeamKey(player);
        if (teamKey == null) {
            context.getSource().sendFailure(Component.translatable("commands.iska_lib.team.currency.no_team"));
            return 0;
        }
        return mutate(context.getSource(), manager, teamKey, currency(context), amount(context), add);
    }

    private static int addToTeam(CommandContext<CommandSourceStack> context) {
        return mutateNamed(context, true);
    }

    private static int removeFromTeam(CommandContext<CommandSourceStack> context) {
        return mutateNamed(context, false);
    }

    private static int mutateNamed(CommandContext<CommandSourceStack> context, boolean add) {
        ShopTeamManager manager = manager(context.getSource());
        String teamKey = manager.resolveTeamKeyByDisplayName(StringArgumentType.getString(context, "teamName"));
        return mutate(context.getSource(), manager, teamKey, currency(context), amount(context), add);
    }

    private static int addToPlayerTeams(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return mutatePlayerTeams(context, true);
    }

    private static int removeFromPlayerTeams(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return mutatePlayerTeams(context, false);
    }

    private static int mutatePlayerTeams(CommandContext<CommandSourceStack> context, boolean add) throws CommandSyntaxException {
        int changed = 0;
        ShopTeamManager manager = manager(context.getSource());
        for (ServerPlayer target : targetPlayers(context)) {
            String teamKey = manager.getPlayerTeamKey(target);
            if (teamKey == null) {
                context.getSource().sendFailure(Component.translatable(
                        "commands.iska_lib.team.currency.player_no_team", target.getName()));
            } else {
                changed += mutate(context.getSource(), manager, teamKey, currency(context), amount(context), add);
            }
        }
        return changed;
    }

    private static int setForOwnTeam(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requirePlayer(context.getSource());
        if (player == null) return 0;
        ShopTeamManager manager = manager(context.getSource());
        String teamKey = manager.getPlayerTeamKey(player);
        if (teamKey == null) {
            context.getSource().sendFailure(Component.translatable("commands.iska_lib.team.currency.no_team"));
            return 0;
        }
        return set(context.getSource(), manager, teamKey, currency(context), amount(context));
    }

    private static int setForTeam(CommandContext<CommandSourceStack> context) {
        ShopTeamManager manager = manager(context.getSource());
        String teamKey = manager.resolveTeamKeyByDisplayName(StringArgumentType.getString(context, "teamName"));
        return set(context.getSource(), manager, teamKey, currency(context), amount(context));
    }

    private static int setForPlayerTeams(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int changed = 0;
        ShopTeamManager manager = manager(context.getSource());
        for (ServerPlayer target : targetPlayers(context)) {
            String teamKey = manager.getPlayerTeamKey(target);
            if (teamKey == null) {
                context.getSource().sendFailure(Component.translatable(
                        "commands.iska_lib.team.currency.player_no_team", target.getName()));
            } else {
                changed += set(context.getSource(), manager, teamKey, currency(context), amount(context));
            }
        }
        return changed;
    }

    private static int set(CommandSourceStack source, ShopTeamManager manager, String teamKey,
                           String currencyId, double target) {
        if (manager.getTeamLeader(teamKey) == null) {
            source.sendFailure(Component.translatable("commands.iska_lib.team.currency.missing_team", displayTeam(manager, teamKey)));
            return 0;
        }
        double current = manager.getTeamCurrencyBalance(teamKey, currencyId);
        boolean success = target >= current
                ? manager.addTeamCurrency(teamKey, currencyId, target - current)
                : manager.removeTeamCurrency(teamKey, currencyId, current - target);
        if (!success) {
            source.sendFailure(Component.translatable("commands.iska_lib.team.currency.failed"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.iska_lib.team.currency.set",
                currencyDisplay(currencyId), displayTeam(manager, teamKey), target), false);
        return 1;
    }

    private static int moveFromOwnTeam(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requirePlayer(context.getSource());
        if (player == null) return 0;
        ShopTeamManager manager = manager(context.getSource());
        String from = manager.getPlayerTeamKey(player);
        if (from == null) {
            context.getSource().sendFailure(Component.translatable("commands.iska_lib.team.currency.no_team"));
            return 0;
        }
        String to = manager.resolveTeamKeyByDisplayName(StringArgumentType.getString(context, "toTeam"));
        return move(context.getSource(), manager, from, to, currency(context), amount(context));
    }

    private static int moveBetweenTeams(CommandContext<CommandSourceStack> context) {
        ShopTeamManager manager = manager(context.getSource());
        String from = manager.resolveTeamKeyByDisplayName(StringArgumentType.getString(context, "fromTeam"));
        String to = manager.resolveTeamKeyByDisplayName(StringArgumentType.getString(context, "toTeam"));
        return move(context.getSource(), manager, from, to, currency(context), amount(context));
    }

    private static int move(CommandSourceStack source, ShopTeamManager manager, String from, String to,
                            String currencyId, double amount) {
        if (manager.getTeamLeader(from) == null || manager.getTeamLeader(to) == null) {
            source.sendFailure(Component.translatable("commands.iska_lib.team.currency.missing_team",
                    manager.getTeamLeader(from) == null ? displayTeam(manager, from) : displayTeam(manager, to)));
            return 0;
        }
        if (!manager.removeTeamCurrency(from, currencyId, amount)) {
            source.sendFailure(Component.translatable("commands.iska_lib.team.currency.insufficient",
                    displayTeam(manager, from), manager.getTeamCurrencyBalance(from, currencyId)));
            return 0;
        }
        if (!manager.addTeamCurrency(to, currencyId, amount)) {
            manager.addTeamCurrency(from, currencyId, amount);
            source.sendFailure(Component.translatable("commands.iska_lib.team.currency.failed"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.iska_lib.team.currency.moved",
                amount, currencyDisplay(currencyId), displayTeam(manager, from), displayTeam(manager, to)), false);
        return 1;
    }

    private static int mutate(CommandSourceStack source, ShopTeamManager manager, String teamKey,
                              String currencyId, double amount, boolean add) {
        boolean success = add
                ? manager.addTeamCurrency(teamKey, currencyId, amount)
                : manager.removeTeamCurrency(teamKey, currencyId, amount);
        if (!success) {
            source.sendFailure(Component.translatable("commands.iska_lib.team.currency.failed"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                add ? "commands.iska_lib.team.currency.added" : "commands.iska_lib.team.currency.removed",
                amount, currencyDisplay(currencyId), displayTeam(manager, teamKey)), false);
        return 1;
    }

    private static ServerPlayer requirePlayer(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("commands.iska_lib.team.currency.player_only"));
        }
        return player;
    }

    private static List<ServerPlayer> targetPlayers(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        Collection<? extends Entity> entities = EntityArgument.getEntities(context, "player");
        return entities.stream().filter(ServerPlayer.class::isInstance).map(ServerPlayer.class::cast).toList();
    }

    private static String currency(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "currencyId");
    }

    private static double amount(CommandContext<CommandSourceStack> context) {
        return DoubleArgumentType.getDouble(context, "amount");
    }

    private static String currencyDisplay(String currencyId) {
        return ShopCurrencyHooks.getListener().getCurrencyInfo(currencyId)
                .map(info -> Component.translatable(info.translationKey()).getString() + " " + info.symbol())
                .orElse(currencyId);
    }

    private static String displayTeam(ShopTeamManager manager, String teamKey) {
        if (teamKey == null) return "null";
        String display = manager.getTeamDisplayName(teamKey);
        return display == null ? teamKey : display;
    }
}
