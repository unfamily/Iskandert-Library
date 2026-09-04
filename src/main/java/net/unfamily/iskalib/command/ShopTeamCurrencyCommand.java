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
import net.minecraft.world.entity.Entity;
import net.unfamily.iskalib.shop.ShopCurrencyHooks;
import net.unfamily.iskalib.team.ShopTeamManager;

import java.util.Collection;
import java.util.List;

/**
 * Currency administration commands nested under {@code /iska_lib_team currency}.
 */
public final class ShopTeamCurrencyCommand {
    private ShopTeamCurrencyCommand() {}

    public static ArgumentBuilder<CommandSourceStack, ?> currencyLiteral() {
        return Commands.literal("currency")
                .then(Commands.literal("list").executes(ShopTeamCurrencyCommand::listCurrencies))
                .then(Commands.literal("add").requires(source -> source.hasPermission(2))
                        .then(currencyOperation(context -> mutateOwn(context, true),
                                context -> mutateNamed(context, true),
                                context -> mutatePlayers(context, true))))
                .then(Commands.literal("remove").requires(source -> source.hasPermission(2))
                        .then(currencyOperation(context -> mutateOwn(context, false),
                                context -> mutateNamed(context, false),
                                context -> mutatePlayers(context, false))))
                .then(Commands.literal("set").requires(source -> source.hasPermission(2))
                        .then(currencyOperation(ShopTeamCurrencyCommand::setOwn,
                                ShopTeamCurrencyCommand::setNamed,
                                ShopTeamCurrencyCommand::setPlayers)))
                .then(Commands.literal("move").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("currencyId", StringArgumentType.word()).suggests(SUGGEST_CURRENCIES)
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0))
                                        .then(Commands.argument("toTeam", StringArgumentType.word()).suggests(SUGGEST_TEAMS)
                                                .executes(ShopTeamCurrencyCommand::moveFromOwn)
                                                .then(Commands.argument("fromTeam", StringArgumentType.word()).suggests(SUGGEST_TEAMS)
                                                        .executes(ShopTeamCurrencyCommand::moveNamed))))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> currencyOperation(
            com.mojang.brigadier.Command<CommandSourceStack> ownCommand,
            com.mojang.brigadier.Command<CommandSourceStack> namedCommand,
            com.mojang.brigadier.Command<CommandSourceStack> playerCommand) {
        return Commands.argument("currencyId", StringArgumentType.word()).suggests(SUGGEST_CURRENCIES)
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0))
                        .executes(ownCommand)
                        .then(namedTeamArgument(namedCommand))
                        .then(playerArgument(playerCommand)));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> namedTeamArgument(
            com.mojang.brigadier.Command<CommandSourceStack> command) {
        return Commands.literal("team")
                .then(Commands.argument("teamName", StringArgumentType.word()).suggests(SUGGEST_TEAMS).executes(command));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> playerArgument(
            com.mojang.brigadier.Command<CommandSourceStack> command) {
        return Commands.literal("player")
                .then(Commands.argument("player", EntityArgument.entities()).executes(command));
    }

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_CURRENCIES = (context, builder) ->
            SharedSuggestionProvider.suggest(ShopCurrencyHooks.getListener().listCurrencyIds(), builder);

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_TEAMS = (context, builder) ->
            SharedSuggestionProvider.suggest(manager(context.getSource()).getAllTeamNames(), builder);

    private static ShopTeamManager manager(CommandSourceStack source) {
        return ShopTeamManager.getInstance(source.getLevel());
    }

    private static int listCurrencies(CommandContext<CommandSourceStack> context) {
        List<String> ids = ShopCurrencyHooks.getListener().listCurrencyIds();
        if (ids == null || ids.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("The currency catalog is empty."), false);
            return 1;
        }
        context.getSource().sendSuccess(() -> Component.literal("=== Available Currencies ==="), false);
        ids.stream().sorted().forEach(id -> context.getSource().sendSuccess(
                () -> Component.literal("- " + id + ": " + currencyDisplay(id)), false));
        return ids.size();
    }

    private static int mutateOwn(CommandContext<CommandSourceStack> context, boolean add) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }
        ShopTeamManager manager = manager(context.getSource());
        String team = manager.getPlayerTeamKey(player);
        if (team == null) {
            context.getSource().sendFailure(Component.literal("You are not in a team"));
            return 0;
        }
        return mutate(context, manager, team, add);
    }

    private static int mutateNamed(CommandContext<CommandSourceStack> context, boolean add) {
        ShopTeamManager manager = manager(context.getSource());
        return mutate(context, manager, resolveTeam(context, manager, "teamName"), add);
    }

    private static int mutate(CommandContext<CommandSourceStack> context, ShopTeamManager manager,
                              String team, boolean add) {
        String currency = StringArgumentType.getString(context, "currencyId");
        double amount = DoubleArgumentType.getDouble(context, "amount");
        boolean success = add ? manager.addTeamCurrency(team, currency, amount)
                : manager.removeTeamCurrency(team, currency, amount);
        if (!success) {
            context.getSource().sendFailure(Component.literal(add
                    ? "Failed to add currency. Team might not exist."
                    : "Failed to remove currency. Balance may be insufficient or team missing."));
            return 0;
        }
        String verb = add ? "Added " : "Removed ";
        String prep = add ? " to " : " from ";
        String displayTeam = displayTeam(manager, team);
        context.getSource().sendSuccess(() -> Component.literal(
                verb + amount + " " + currencyDisplay(currency) + prep + "team '" + displayTeam + "'!"), false);
        return 1;
    }

    private static int mutatePlayers(CommandContext<CommandSourceStack> context, boolean add)
            throws CommandSyntaxException {
        int changed = 0;
        for (ServerPlayer player : getPlayers(context)) {
            ShopTeamManager manager = ShopTeamManager.getInstance(player.serverLevel());
            String team = manager.getPlayerTeamKey(player);
            if (team == null) {
                context.getSource().sendFailure(Component.literal(player.getName().getString() + " is not in a team"));
            } else {
                changed += mutate(context, manager, team, add);
            }
        }
        return changed;
    }

    private static int setOwn(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }
        ShopTeamManager manager = manager(context.getSource());
        String team = manager.getPlayerTeamKey(player);
        if (team == null) {
            context.getSource().sendFailure(Component.literal("You are not in a team"));
            return 0;
        }
        return set(context, manager, team);
    }

    private static int setNamed(CommandContext<CommandSourceStack> context) {
        ShopTeamManager manager = manager(context.getSource());
        return set(context, manager, resolveTeam(context, manager, "teamName"));
    }

    private static int setPlayers(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int changed = 0;
        for (ServerPlayer player : getPlayers(context)) {
            ShopTeamManager manager = ShopTeamManager.getInstance(player.serverLevel());
            String team = manager.getPlayerTeamKey(player);
            if (team == null) {
                context.getSource().sendFailure(Component.literal(player.getName().getString() + " is not in a team"));
            } else {
                changed += set(context, manager, team);
            }
        }
        return changed;
    }

    private static int set(CommandContext<CommandSourceStack> context, ShopTeamManager manager, String team) {
        String currency = StringArgumentType.getString(context, "currencyId");
        double target = DoubleArgumentType.getDouble(context, "amount");
        double difference = target - manager.getTeamCurrencyBalance(team, currency);
        boolean success = difference >= 0
                ? manager.addTeamCurrency(team, currency, difference)
                : manager.removeTeamCurrency(team, currency, -difference);
        if (!success) {
            context.getSource().sendFailure(Component.literal("Failed to set currency. Team might not exist."));
            return 0;
        }
        String displayTeam = displayTeam(manager, team);
        context.getSource().sendSuccess(() -> Component.literal(
                "Set " + currencyDisplay(currency) + " balance for team '" + displayTeam + "' to " + target + "!"), false);
        return 1;
    }

    private static int moveFromOwn(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }
        ShopTeamManager manager = manager(context.getSource());
        String from = manager.getPlayerTeamKey(player);
        if (from == null) {
            context.getSource().sendFailure(Component.literal("You are not in a team"));
            return 0;
        }
        return move(context, manager, from, resolveTeam(context, manager, "toTeam"));
    }

    private static int moveNamed(CommandContext<CommandSourceStack> context) {
        ShopTeamManager manager = manager(context.getSource());
        return move(context, manager, resolveTeam(context, manager, "fromTeam"),
                resolveTeam(context, manager, "toTeam"));
    }

    private static int move(CommandContext<CommandSourceStack> context, ShopTeamManager manager,
                            String from, String to) {
        String currency = StringArgumentType.getString(context, "currencyId");
        double amount = DoubleArgumentType.getDouble(context, "amount");
        if (manager.getTeamLeader(from) == null || manager.getTeamLeader(to) == null) {
            context.getSource().sendFailure(Component.literal("Source or destination team does not exist"));
            return 0;
        }
        if (!manager.removeTeamCurrency(from, currency, amount)) {
            context.getSource().sendFailure(Component.literal("Source team does not have enough currency"));
            return 0;
        }
        if (!manager.addTeamCurrency(to, currency, amount)) {
            manager.addTeamCurrency(from, currency, amount);
            context.getSource().sendFailure(Component.literal("Transfer failed and was rolled back"));
            return 0;
        }
        String fromDisplay = displayTeam(manager, from);
        String toDisplay = displayTeam(manager, to);
        context.getSource().sendSuccess(() -> Component.literal("Moved " + amount + " " + currencyDisplay(currency)
                + " from '" + fromDisplay + "' to '" + toDisplay + "'!"), false);
        return 1;
    }

    private static String resolveTeam(CommandContext<CommandSourceStack> context, ShopTeamManager manager,
                                      String argument) {
        return manager.resolveTeamKeyByDisplayName(StringArgumentType.getString(context, argument));
    }

    private static String displayTeam(ShopTeamManager manager, String team) {
        String display = manager.getTeamDisplayName(team);
        return display == null ? String.valueOf(team) : display;
    }

    private static String currencyDisplay(String currencyId) {
        return ShopCurrencyHooks.getListener().getCurrencyInfo(currencyId)
                .map(info -> Component.translatable(info.translationKey()).getString()
                        + (info.symbol().isBlank() ? "" : " " + info.symbol()))
                .orElse(currencyId);
    }

    private static List<ServerPlayer> getPlayers(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        Collection<? extends Entity> entities = EntityArgument.getEntities(context, "player");
        return entities.stream().filter(ServerPlayer.class::isInstance).map(ServerPlayer.class::cast).toList();
    }
}
