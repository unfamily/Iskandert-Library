package net.unfamily.iskalib.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;


import net.unfamily.iskalib.team.ShopTeamManager;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Team management commands exposed by the shared library.
 *
 * <p>Root literal is {@code iska_lib_team} to avoid collisions with consuming mods.
 */
public final class ShopTeamCommand {
    private static final SimpleCommandExceptionType ERROR_PLAYER_NOT_FOUND = new SimpleCommandExceptionType(
            Component.literal("No player found from selector"));

    private ShopTeamCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("iska_lib_team")
                .requires(source -> source.hasPermission(0))
                .then(Commands.literal("create")
                        .then(Commands.argument("teamName", StringArgumentType.word())
                                .executes(ShopTeamCommand::createTeam)))
                .then(Commands.literal("delete")
                        .executes(ShopTeamCommand::deleteOwnTeam)
                        .then(Commands.argument("teamName", StringArgumentType.word())
                                .requires(source -> source.hasPermission(2))
                                .executes(ShopTeamCommand::deleteTeam)))
                .then(Commands.literal("rename")
                        .then(Commands.argument("newName", StringArgumentType.word())
                                .executes(ShopTeamCommand::renameOwnTeam)
                                .then(Commands.argument("teamName", StringArgumentType.word())
                                        .requires(source -> source.hasPermission(2))
                                        .executes(ShopTeamCommand::renameTeam))))
                .then(Commands.literal("leader")
                        .then(Commands.argument("newLeader", EntityArgument.players())
                                .executes(ShopTeamCommand::transferOwnTeamLeadership)
                                .then(Commands.argument("teamName", StringArgumentType.word())
                                        .requires(source -> source.hasPermission(2))
                                        .executes(ShopTeamCommand::transferTeamLeadership))))
                .then(Commands.literal("assistant")
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", EntityArgument.players())
                                        .executes(ShopTeamCommand::addAssistantToOwnTeam)
                                        .then(Commands.argument("teamName", StringArgumentType.word())
                                                .requires(source -> source.hasPermission(2))
                                                .executes(ShopTeamCommand::addTeamAssistant))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", EntityArgument.players())
                                        .executes(ShopTeamCommand::removeAssistantFromOwnTeam)
                                        .then(Commands.argument("teamName", StringArgumentType.word())
                                                .requires(source -> source.hasPermission(2))
                                                .executes(ShopTeamCommand::removeTeamAssistant)))))
                .then(Commands.literal("assistant_list")
                        .executes(ShopTeamCommand::listOwnAssistants)
                        .then(Commands.argument("teamName", StringArgumentType.word())
                                .executes(ShopTeamCommand::listTeamAssistants)))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.players())
                                .executes(ShopTeamCommand::inviteToOwnTeam)
                                .then(Commands.argument("teamName", StringArgumentType.word())
                                        .requires(source -> source.hasPermission(2))
                                        .executes(ShopTeamCommand::inviteToTeam))))
                .then(Commands.literal("cancelInvite")
                        .then(Commands.argument("player", EntityArgument.players())
                                .executes(ShopTeamCommand::cancelInviteFromOwnTeam)
                                .then(Commands.argument("teamName", StringArgumentType.word())
                                        .requires(source -> source.hasPermission(2))
                                        .executes(ShopTeamCommand::cancelInviteFromTeam))))
                .then(Commands.literal("accept")
                        .then(Commands.argument("teamName", StringArgumentType.word())
                                .executes(ShopTeamCommand::acceptInvitation)))
                .then(Commands.literal("leave")
                        .executes(ShopTeamCommand::leaveTeam))
                .then(Commands.literal("info")
                        .executes(ShopTeamCommand::ownTeamInfo)
                        .then(Commands.argument("teamName", StringArgumentType.word())
                                .executes(ShopTeamCommand::teamInfo)))
                .then(Commands.literal("members")
                        .executes(ShopTeamCommand::listOwnMembers)
                        .then(Commands.argument("teamName", StringArgumentType.word())
                                .executes(ShopTeamCommand::listTeamMembers)))
                .then(Commands.literal("list")
                        .executes(ShopTeamCommand::listTeams))
                .then(Commands.literal("invitations")
                        .executes(ShopTeamCommand::listInvitations))
        );
    }

    private static List<ServerPlayer> getTargetPlayers(CommandContext<CommandSourceStack> context, String argumentName) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, argumentName);
        return players.stream().collect(Collectors.toList());
    }

    private static ServerPlayer getSingleTargetPlayer(CommandContext<CommandSourceStack> context, String argumentName) throws CommandSyntaxException {
        List<ServerPlayer> players = getTargetPlayers(context, argumentName);
        if (players.isEmpty()) {
            throw ERROR_PLAYER_NOT_FOUND.create();
        }
        return players.getFirst();
    }

    private static ShopTeamManager mgr(ServerPlayer player) {
        return ShopTeamManager.getInstance((net.minecraft.server.level.ServerLevel) player.level());
    }

    private static String displayTeamName(ShopTeamManager teamManager, String teamKey) {
        if (teamKey == null) return null;
        String display = teamManager.getTeamDisplayName(teamKey);
        return display != null ? display : teamKey;
    }

    private static String getPlayerName(UUID playerId, MinecraftServer server) {
        try {
            ServerPlayer p = server.getPlayerList().getPlayer(playerId);
            if (p != null) {
                return p.getName().getString();
            }
        } catch (Exception ignored) {
        }
        return playerId.toString();
    }

    private static int createTeam(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;

        String teamName = StringArgumentType.getString(context, "teamName");
        ShopTeamManager teamManager = mgr(player);
        if (teamManager.createTeam(teamName, player)) {
            context.getSource().sendSuccess(() -> Component.literal("Team '" + teamName + "' created successfully!"), false);
            return 1;
        }
        context.getSource().sendFailure(Component.literal("Failed to create team. Team might already exist or you're already in a team."));
        return 0;
    }

    private static int deleteOwnTeam(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;

        ShopTeamManager teamManager = mgr(player);
        String teamKey = teamManager.getPlayerTeamKey(player);
        if (teamKey == null) {
            context.getSource().sendFailure(Component.literal("You are not in a team"));
            return 0;
        }
        String display = teamManager.getTeamDisplayName(teamKey);
        if (teamManager.deleteTeam(teamKey, player)) {
            context.getSource().sendSuccess(() -> Component.literal("Team '" + (display != null ? display : teamKey) + "' deleted successfully!"), false);
            return 1;
        }
        context.getSource().sendFailure(Component.literal("Failed to delete team. You might not be the leader."));
        return 0;
    }

    private static int deleteTeam(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;

        ShopTeamManager teamManager = mgr(player);
        String input = StringArgumentType.getString(context, "teamName");
        String teamKey = teamManager.resolveTeamKeyByDisplayName(input);
        if (teamManager.deleteTeam(teamKey, player)) {
            context.getSource().sendSuccess(() -> Component.literal("Team '" + input + "' deleted successfully!"), false);
            return 1;
        }
        context.getSource().sendFailure(Component.literal("Failed to delete team. You might not be the leader or the team doesn't exist."));
        return 0;
    }

    private static int renameOwnTeam(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;

        ShopTeamManager teamManager = mgr(player);
        String teamKey = teamManager.getPlayerTeamKey(player);
        if (teamKey == null) {
            context.getSource().sendFailure(Component.literal("You are not in a team"));
            return 0;
        }
        String newName = StringArgumentType.getString(context, "newName");
        String display = teamManager.getTeamDisplayName(teamKey);
        if (teamManager.renameTeam(teamKey, newName, player)) {
            context.getSource().sendSuccess(() -> Component.literal("Team '" + (display != null ? display : teamKey) + "' renamed to '" + newName + "' successfully!"), false);
            return 1;
        }
        context.getSource().sendFailure(Component.literal("Failed to rename team. Team might already exist or you're not the leader."));
        return 0;
    }

    private static int renameTeam(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;

        ShopTeamManager teamManager = mgr(player);
        String input = StringArgumentType.getString(context, "teamName");
        String teamKey = teamManager.resolveTeamKeyByDisplayName(input);
        String newName = StringArgumentType.getString(context, "newName");
        if (teamManager.renameTeam(teamKey, newName, player)) {
            context.getSource().sendSuccess(() -> Component.literal("Team '" + input + "' renamed to '" + newName + "' successfully!"), false);
            return 1;
        }
        context.getSource().sendFailure(Component.literal("Failed to rename team. Team might not exist or you're not the leader."));
        return 0;
    }

    private static int transferOwnTeamLeadership(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;

        ServerPlayer newLeader = getSingleTargetPlayer(context, "newLeader");
        ShopTeamManager teamManager = mgr(player);
        String teamKey = teamManager.getPlayerTeamKey(player);
        if (teamKey == null) {
            context.getSource().sendFailure(Component.literal("You are not in a team"));
            return 0;
        }
        if (teamManager.transferLeadership(teamKey, player, newLeader)) {
            context.getSource().sendSuccess(() -> Component.literal("Leadership transferred to " + newLeader.getName().getString() + "!"), false);
            return 1;
        }
        context.getSource().sendFailure(Component.literal("Failed to transfer leadership."));
        return 0;
    }

    private static int transferTeamLeadership(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;

        ShopTeamManager teamManager = mgr(player);
        String input = StringArgumentType.getString(context, "teamName");
        String teamKey = teamManager.resolveTeamKeyByDisplayName(input);
        ServerPlayer newLeader = getSingleTargetPlayer(context, "newLeader");
        if (teamManager.transferLeadership(teamKey, player, newLeader)) {
            context.getSource().sendSuccess(() -> Component.literal("Leadership of team '" + input + "' transferred to " + newLeader.getName().getString() + "!"), false);
            return 1;
        }
        context.getSource().sendFailure(Component.literal("Failed to transfer leadership."));
        return 0;
    }

    private static int addAssistantToOwnTeam(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;

        ShopTeamManager teamManager = mgr(player);
        String teamKey = teamManager.getPlayerTeamKey(player);
        if (teamKey == null) {
            context.getSource().sendFailure(Component.literal("You are not in a team"));
            return 0;
        }
        List<ServerPlayer> assistants = getTargetPlayers(context, "player");
        int count = 0;
        for (ServerPlayer assistant : assistants) {
            if (teamManager.addTeamAssistant(teamKey, player, assistant)) {
                count++;
            }
        }
        return count;
    }

    private static int addTeamAssistant(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;

        ShopTeamManager teamManager = mgr(player);
        String input = StringArgumentType.getString(context, "teamName");
        String teamKey = teamManager.resolveTeamKeyByDisplayName(input);
        List<ServerPlayer> assistants = getTargetPlayers(context, "player");
        int count = 0;
        for (ServerPlayer assistant : assistants) {
            if (teamManager.addTeamAssistant(teamKey, player, assistant)) {
                count++;
            }
        }
        return count;
    }

    private static int removeAssistantFromOwnTeam(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;

        ShopTeamManager teamManager = mgr(player);
        String teamKey = teamManager.getPlayerTeamKey(player);
        if (teamKey == null) {
            context.getSource().sendFailure(Component.literal("You are not in a team"));
            return 0;
        }
        List<ServerPlayer> assistants = getTargetPlayers(context, "player");
        int count = 0;
        for (ServerPlayer assistant : assistants) {
            if (teamManager.removeTeamAssistant(teamKey, player, assistant)) {
                count++;
            }
        }
        return count;
    }

    private static int removeTeamAssistant(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;

        ShopTeamManager teamManager = mgr(player);
        String input = StringArgumentType.getString(context, "teamName");
        String teamKey = teamManager.resolveTeamKeyByDisplayName(input);
        List<ServerPlayer> assistants = getTargetPlayers(context, "player");
        int count = 0;
        for (ServerPlayer assistant : assistants) {
            if (teamManager.removeTeamAssistant(teamKey, player, assistant)) {
                count++;
            }
        }
        return count;
    }

    private static int inviteToOwnTeam(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;

        ShopTeamManager teamManager = mgr(player);
        String teamKey = teamManager.getPlayerTeamKey(player);
        if (teamKey == null) {
            context.getSource().sendFailure(Component.literal("You are not in a team"));
            return 0;
        }
        List<ServerPlayer> targets = getTargetPlayers(context, "player");
        int count = 0;
        for (ServerPlayer target : targets) {
            if (teamManager.invitePlayerToTeam(teamKey, player, target)) {
                count++;
            }
        }
        return count;
    }

    private static int inviteToTeam(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;

        ShopTeamManager teamManager = mgr(player);
        String input = StringArgumentType.getString(context, "teamName");
        String teamKey = teamManager.resolveTeamKeyByDisplayName(input);
        List<ServerPlayer> targets = getTargetPlayers(context, "player");
        int count = 0;
        for (ServerPlayer target : targets) {
            if (teamManager.invitePlayerToTeam(teamKey, player, target)) {
                count++;
            }
        }
        return count;
    }

    private static int acceptInvitation(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;

        ShopTeamManager teamManager = mgr(player);
        String input = StringArgumentType.getString(context, "teamName");
        String teamKey = teamManager.resolveTeamKeyByDisplayName(input);
        if (teamManager.acceptTeamInvitation(player, teamKey)) {
            context.getSource().sendSuccess(() -> Component.literal("Successfully joined team '" + input + "'!"), false);
            return 1;
        }
        context.getSource().sendFailure(Component.literal("Failed to join team. You might not have an invitation or already be in a team."));
        return 0;
    }

    private static int leaveTeam(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;

        ShopTeamManager teamManager = mgr(player);
        if (teamManager.leaveTeam(player)) {
            context.getSource().sendSuccess(() -> Component.literal("Successfully left your team!"), false);
            return 1;
        }
        context.getSource().sendFailure(Component.literal("Failed to leave team. You might be the leader (use delete instead) or not be in a team."));
        return 0;
    }

    private static int ownTeamInfo(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;
        ShopTeamManager teamManager = mgr(player);
        String teamKey = teamManager.getPlayerTeamKey(player);
        if (teamKey == null) {
            context.getSource().sendFailure(Component.literal("You are not in a team"));
            return 0;
        }
        return showTeamInfo(context.getSource(), teamManager, teamKey);
    }

    private static int teamInfo(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;
        ShopTeamManager teamManager = mgr(player);
        String input = StringArgumentType.getString(context, "teamName");
        String teamKey = teamManager.resolveTeamKeyByDisplayName(input);
        return showTeamInfo(context.getSource(), teamManager, teamKey);
    }

    private static int showTeamInfo(CommandSourceStack source, ShopTeamManager teamManager, String teamKey) {
        UUID leader = teamManager.getTeamLeader(teamKey);
        if (leader == null) {
            source.sendFailure(Component.literal("Team '" + displayTeamName(teamManager, teamKey) + "' does not exist"));
            return 0;
        }

        List<UUID> assistants = teamManager.getTeamAssistants(teamKey);
        List<UUID> members = teamManager.getTeamMembers(teamKey);
        String teamDisplay = displayTeamName(teamManager, teamKey);

        source.sendSuccess(() -> Component.literal("=== Team: " + teamDisplay + " ==="), false);
        source.sendSuccess(() -> Component.literal("Leader: " + getPlayerName(leader, source.getServer())), false);
        source.sendSuccess(() -> Component.literal("Assistants: " + assistants.size()), false);
        source.sendSuccess(() -> Component.literal("Members: " + members.size()), false);
        return 1;
    }

    private static int listOwnMembers(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;
        ShopTeamManager teamManager = mgr(player);
        String teamKey = teamManager.getPlayerTeamKey(player);
        if (teamKey == null) {
            context.getSource().sendFailure(Component.literal("You are not in a team"));
            return 0;
        }
        return showTeamMembers(context.getSource(), teamManager, teamKey);
    }

    private static int listTeamMembers(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;
        ShopTeamManager teamManager = mgr(player);
        String input = StringArgumentType.getString(context, "teamName");
        String teamKey = teamManager.resolveTeamKeyByDisplayName(input);
        return showTeamMembers(context.getSource(), teamManager, teamKey);
    }

    private static int showTeamMembers(CommandSourceStack source, ShopTeamManager teamManager, String teamKey) {
        UUID leader = teamManager.getTeamLeader(teamKey);
        if (leader == null) {
            source.sendFailure(Component.literal("Team '" + displayTeamName(teamManager, teamKey) + "' does not exist"));
            return 0;
        }

        String teamDisplay = displayTeamName(teamManager, teamKey);
        List<UUID> assistants = teamManager.getTeamAssistants(teamKey);
        List<UUID> members = teamManager.getTeamMembers(teamKey);

        source.sendSuccess(() -> Component.literal("=== Team: " + teamDisplay + " Members ==="), false);
        source.sendSuccess(() -> Component.literal("Leader: " + getPlayerName(leader, source.getServer())), false);
        if (!assistants.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Assistants (" + assistants.size() + "):"), false);
            for (UUID a : assistants) {
                source.sendSuccess(() -> Component.literal("- " + getPlayerName(a, source.getServer())), false);
            }
        }
        source.sendSuccess(() -> Component.literal("Members (" + members.size() + "):"), false);
        for (UUID m : members) {
            if (m.equals(leader) || assistants.contains(m)) continue;
            source.sendSuccess(() -> Component.literal("- " + getPlayerName(m, source.getServer())), false);
        }
        return 1;
    }

    private static int listTeams(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;
        ShopTeamManager teamManager = mgr(player);
        var teams = teamManager.getAllTeams();
        if (teams.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No teams exist"), false);
            return 1;
        }
        context.getSource().sendSuccess(() -> Component.literal("=== All Teams ==="), false);
        for (String teamKey : teams) {
            String display = displayTeamName(teamManager, teamKey);
            int memberCount = teamManager.getTeamMembers(teamKey).size();
            context.getSource().sendSuccess(() -> Component.literal(display + " (" + memberCount + " members)"), false);
        }
        return 1;
    }

    private static int listInvitations(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;
        ShopTeamManager teamManager = mgr(player);
        List<String> invitations = teamManager.getPlayerInvitations(player);
        if (invitations.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("You have no pending team invitations"), false);
            return 1;
        }
        context.getSource().sendSuccess(() -> Component.literal("=== Your Team Invitations ==="), false);
        for (String teamKey : invitations) {
            String display = displayTeamName(teamManager, teamKey);
            context.getSource().sendSuccess(() -> Component.literal("- " + display + " (use /iska_lib_team accept " + display + " to join)"), false);
        }
        return 1;
    }

    private static int listOwnAssistants(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;
        ShopTeamManager teamManager = mgr(player);
        String teamKey = teamManager.getPlayerTeamKey(player);
        if (teamKey == null) {
            context.getSource().sendFailure(Component.literal("You are not in a team"));
            return 0;
        }
        return showTeamAssistants(context.getSource(), teamManager, teamKey);
    }

    private static int listTeamAssistants(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;
        ShopTeamManager teamManager = mgr(player);
        String input = StringArgumentType.getString(context, "teamName");
        String teamKey = teamManager.resolveTeamKeyByDisplayName(input);
        return showTeamAssistants(context.getSource(), teamManager, teamKey);
    }

    private static int showTeamAssistants(CommandSourceStack source, ShopTeamManager teamManager, String teamKey) {
        UUID leader = teamManager.getTeamLeader(teamKey);
        if (leader == null) {
            source.sendFailure(Component.literal("Team '" + displayTeamName(teamManager, teamKey) + "' does not exist"));
            return 0;
        }
        List<UUID> assistants = teamManager.getTeamAssistants(teamKey);
        String teamDisplay = displayTeamName(teamManager, teamKey);
        if (assistants.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Team '" + teamDisplay + "' has no assistants"), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("=== Team '" + teamDisplay + "' Assistants ==="), false);
        for (UUID a : assistants) {
            source.sendSuccess(() -> Component.literal("- " + getPlayerName(a, source.getServer())), false);
        }
        return 1;
    }

    private static int cancelInviteFromOwnTeam(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;
        ShopTeamManager teamManager = mgr(player);
        String teamKey = teamManager.getPlayerTeamKey(player);
        if (teamKey == null) {
            context.getSource().sendFailure(Component.literal("You are not in a team"));
            return 0;
        }
        List<ServerPlayer> invitees = getTargetPlayers(context, "player");
        int count = 0;
        for (ServerPlayer invitee : invitees) {
            if (teamManager.cancelTeamInvitation(teamKey, player, invitee)) {
                count++;
            }
        }
        return count;
    }

    private static int cancelInviteFromTeam(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;
        ShopTeamManager teamManager = mgr(player);
        String input = StringArgumentType.getString(context, "teamName");
        String teamKey = teamManager.resolveTeamKeyByDisplayName(input);
        List<ServerPlayer> invitees = getTargetPlayers(context, "player");
        int count = 0;
        for (ServerPlayer invitee : invitees) {
            if (teamManager.cancelTeamInvitation(teamKey, player, invitee)) {
                count++;
            }
        }
        return count;
    }
}

