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
import net.unfamily.iskalib.debug.HandItemDump;
import net.unfamily.iskalib.reload.UtilsReloadHooks;
import net.unfamily.iskalib.stage.StageReloadHooks;

/**
 * Library developer commands. Root {@code iska_lib_dev}: {@code hand}, {@code reload}, {@code wiki}.
 * 
 */
public final class IskaLibDebugCommand {

    private static final String WIKI_URL = "https://github.com/unfamily/iskandert_utilities/wiki";

    private IskaLibDebugCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("iska_lib_dev")
                .requires(source -> source.hasPermission(0))
                .then(Commands.literal("hand")
                        .executes(IskaLibDebugCommand::executeHand))
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(IskaLibDebugCommand::executeReload)
                        .then(Commands.literal("stages")
                                .executes(IskaLibDebugCommand::executeReloadStages)))
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
        CommandSourceStack source = context.getSource();
        UtilsReloadHooks.Listener listener = UtilsReloadHooks.getListener();
        if (listener == null) {
            source.sendFailure(Component.translatable("commands.iska_lib.reload.unavailable"));
            return 0;
        }
        return listener.reloadFromDatapacks(source);
    }

    private static int executeReloadStages(CommandContext<CommandSourceStack> context) {
        StageReloadHooks.Listener listener = StageReloadHooks.getListener();
        if (listener == null) {
            context.getSource().sendFailure(Component.translatable("commands.iska_lib.stage.reload.unavailable"));
            return 0;
        }
        int result = listener.reloadStageBlock(context.getSource());
        if (result > 0) {
            context.getSource().sendSuccess(
                    () -> Component.translatable("commands.iska_lib.stage.reload.success"),
                    true);
        }
        return result;
    }

    private static int executeWiki(CommandContext<CommandSourceStack> context) {
        MutableComponent link = Component.literal(WIKI_URL)
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, WIKI_URL))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("commands.iska_lib.debug.wiki.hover"))));
        context.getSource().sendSuccess(
                () -> Component.translatable("commands.iska_lib.debug.wiki", link),
                false);
        return 1;
    }
}
