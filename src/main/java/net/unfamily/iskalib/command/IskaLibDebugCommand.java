package net.unfamily.iskalib.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.unfamily.iskalib.debug.HandItemDump;
import net.unfamily.iskalib.reload.UtilsReloadHooks;

/**
 * Library debug commands. Root {@code iska_lib_debug}, subcommand {@code hand} — same dump as mod delegation.
 */
public final class IskaLibDebugCommand {

    private IskaLibDebugCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("iska_lib_debug")
                .requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(0))))
                .then(Commands.literal("hand")
                        .executes(IskaLibDebugCommand::executeHand))
                .then(Commands.literal("reload")
                        .requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(2))))
                        .executes(IskaLibDebugCommand::executeReload)));
    }

    private static int executeHand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }
        return HandItemDump.dumpHands(player, source);
    }

    private static int executeReload(CommandContext<CommandSourceStack> context) {
        UtilsReloadHooks.Listener listener = UtilsReloadHooks.getListener();
        if (listener == null) {
            context.getSource().sendFailure(Component.translatable("commands.iska_lib.debug.reload.unavailable"));
            return 0;
        }
        return listener.reloadFromDatapacks(context.getSource());
    }
}
