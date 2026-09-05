package net.unfamily.iskalib.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.unfamily.iskalib.debug.HandItemDump;
import net.unfamily.iskalib.reload.UtilsReloadHooks;

import java.net.URI;

/**
 * Library debug commands. Root {@code iska_lib_debug}: {@code hand}, {@code reload}, {@code wiki}.
 */
public final class IskaLibDebugCommand {

    private static final String WIKI_URL = "https://github.com/unfamily/iskandert_utilities/wiki";

    private IskaLibDebugCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("iska_lib_debug")
                .requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(0))))
                .then(Commands.literal("hand")
                        .executes(IskaLibDebugCommand::executeHand))
                .then(Commands.literal("reload")
                        .requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(2))))
                        .executes(IskaLibDebugCommand::executeReload))
                .then(Commands.literal("wiki")
                        .executes(IskaLibDebugCommand::executeWiki)));
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

    private static int executeWiki(CommandContext<CommandSourceStack> context) {
        MutableComponent link = Component.literal(WIKI_URL)
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.OpenUrl(URI.create(WIKI_URL)))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.translatable("commands.iska_lib.debug.wiki.hover"))));
        context.getSource().sendSuccess(
                () -> Component.translatable("commands.iska_lib.debug.wiki", link),
                false);
        return 1;
    }
}
