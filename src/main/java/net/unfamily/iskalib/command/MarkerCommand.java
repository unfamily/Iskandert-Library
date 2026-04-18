package net.unfamily.iskalib.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.unfamily.iskalib.client.marker.MarkRenderer;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side world marker debug commands for the shared library. Root literal is {@code iska_lib_marker} so it does
 * not collide with game-side {@code iska_utils_*} commands.
 * <p>
 * Registration is invoked by consuming mods (e.g. {@code RegisterCommandsEvent}).
 */
public final class MarkerCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String DEFAULT_COLOR = "33FF0000";
    private static final int DEFAULT_DURATION = 1200;

    private static final Map<String, String> COMMAND_USAGE = new HashMap<>();

    static {
        COMMAND_USAGE.put("create_marker", "/iska_lib_marker create <x> <y> <z> [color] [duration] [text]");
        COMMAND_USAGE.put("create_marker_looking", "/iska_lib_marker create_looking [color] [duration] [text]");
        COMMAND_USAGE.put("create_billboard", "/iska_lib_marker billboard <x> <y> <z> [color] [duration] [text]");
        COMMAND_USAGE.put("create_billboard_looking", "/iska_lib_marker billboard_looking [color] [duration] [text]");
        COMMAND_USAGE.put("clear_markers", "/iska_lib_marker clear");
    }

    private MarkerCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LOGGER.info("Registering iska_lib_marker command");
        dispatcher.register(
                Commands.literal("iska_lib_marker")
                        .requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(0))))
                        .then(Commands.literal("create")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(context -> createMarker(context, DEFAULT_COLOR, DEFAULT_DURATION, null))
                                        .then(Commands.argument("color", StringArgumentType.word())
                                                .executes(context -> createMarker(context,
                                                        StringArgumentType.getString(context, "color"),
                                                        DEFAULT_DURATION, null))
                                                .then(Commands.argument("duration", IntegerArgumentType.integer(1))
                                                        .executes(context -> createMarker(context,
                                                                StringArgumentType.getString(context, "color"),
                                                                IntegerArgumentType.getInteger(context, "duration"), null))
                                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                                .executes(context -> createMarker(context,
                                                                        StringArgumentType.getString(context, "color"),
                                                                        IntegerArgumentType.getInteger(context, "duration"),
                                                                        StringArgumentType.getString(context, "text"))))))))
                        .then(Commands.literal("create_looking")
                                .executes(context -> createMarkerLooking(context, DEFAULT_COLOR, DEFAULT_DURATION, null))
                                .then(Commands.argument("color", StringArgumentType.word())
                                        .executes(context -> createMarkerLooking(context,
                                                StringArgumentType.getString(context, "color"),
                                                DEFAULT_DURATION, null))
                                        .then(Commands.argument("duration", IntegerArgumentType.integer(1))
                                                .executes(context -> createMarkerLooking(context,
                                                        StringArgumentType.getString(context, "color"),
                                                        IntegerArgumentType.getInteger(context, "duration"), null))
                                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                                        .executes(context -> createMarkerLooking(context,
                                                                StringArgumentType.getString(context, "color"),
                                                                IntegerArgumentType.getInteger(context, "duration"),
                                                                StringArgumentType.getString(context, "text")))))))
                        .then(Commands.literal("billboard")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(context -> createBillboard(context, DEFAULT_COLOR, DEFAULT_DURATION, null))
                                        .then(Commands.argument("color", StringArgumentType.word())
                                                .executes(context -> createBillboard(context,
                                                        StringArgumentType.getString(context, "color"),
                                                        DEFAULT_DURATION, null))
                                                .then(Commands.argument("duration", IntegerArgumentType.integer(1))
                                                        .executes(context -> createBillboard(context,
                                                                StringArgumentType.getString(context, "color"),
                                                                IntegerArgumentType.getInteger(context, "duration"), null))
                                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                                .executes(context -> createBillboard(context,
                                                                        StringArgumentType.getString(context, "color"),
                                                                        IntegerArgumentType.getInteger(context, "duration"),
                                                                        StringArgumentType.getString(context, "text"))))))))
                        .then(Commands.literal("billboard_looking")
                                .executes(context -> createBillboardLooking(context, DEFAULT_COLOR, DEFAULT_DURATION, null))
                                .then(Commands.argument("color", StringArgumentType.word())
                                        .executes(context -> createBillboardLooking(context,
                                                StringArgumentType.getString(context, "color"),
                                                DEFAULT_DURATION, null))
                                        .then(Commands.argument("duration", IntegerArgumentType.integer(1))
                                                .executes(context -> createBillboardLooking(context,
                                                        StringArgumentType.getString(context, "color"),
                                                        IntegerArgumentType.getInteger(context, "duration"), null))
                                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                                        .executes(context -> createBillboardLooking(context,
                                                                StringArgumentType.getString(context, "color"),
                                                                IntegerArgumentType.getInteger(context, "duration"),
                                                                StringArgumentType.getString(context, "text")))))))
                        .then(Commands.literal("clear")
                                .executes(MarkerCommand::clearMarkers)));
    }

    public static void sendUsage(CommandSourceStack source, String commandKey) {
        String usage = COMMAND_USAGE.getOrDefault(commandKey, "/iska_lib_marker " + commandKey.replace('_', ' '));
        source.sendFailure(Component.literal("Usage: " + usage));
    }

    public static String getCommandUsage(String commandKey) {
        return COMMAND_USAGE.getOrDefault(commandKey, "/iska_lib_marker " + commandKey.replace('_', ' '));
    }

    private static int parseHexColor(String hexColor) {
        if (hexColor.startsWith("0x") || hexColor.startsWith("0X")) {
            hexColor = hexColor.substring(2);
        } else if (hexColor.startsWith("#")) {
            hexColor = hexColor.substring(1);
        }

        if (hexColor.length() == 6) {
            hexColor = "33" + hexColor;
        }

        if (hexColor.length() != 8) {
            LOGGER.warn("Invalid hex color format: {}. Using default color.", hexColor);
            return 0x33FF0000;
        }

        try {
            return (int) Long.parseLong(hexColor, 16);
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid hex color format: {}. Using default color.", hexColor);
            return 0x33FF0000;
        }
    }

    private static int createMarker(CommandContext<CommandSourceStack> context, String colorHex, int duration, String text) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        int color = parseHexColor(colorHex);

        if (text != null) {
            MarkRenderer.getInstance().addHighlightedBlock(pos, color, duration, text);
        } else {
            MarkRenderer.getInstance().addHighlightedBlock(pos, color, duration);
        }

        String textInfo = text != null ? " with text \"" + text + "\"" : "";
        source.sendSuccess(() -> Component.literal(
                String.format("Marker Block created at %s with color 0x%08X for %d tick%s",
                        pos.toShortString(), color, duration, textInfo)
        ), true);

        return 1;
    }

    private static int createMarkerLooking(CommandContext<CommandSourceStack> context, String colorHex, int duration, String text) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayerOrException();

        HitResult hitResult = player.pick(20.0, 0.0F, false);

        if (!(hitResult instanceof BlockHitResult) || hitResult.getType() == HitResult.Type.MISS) {
            source.sendFailure(Component.literal("No block found. You must look at a block."));
            return 0;
        }

        BlockPos pos = ((BlockHitResult) hitResult).getBlockPos();
        int color = parseHexColor(colorHex);

        if (text != null) {
            MarkRenderer.getInstance().addHighlightedBlock(pos, color, duration, text);
        } else {
            MarkRenderer.getInstance().addHighlightedBlock(pos, color, duration);
        }

        String textInfo = text != null ? " with text \"" + text + "\"" : "";
        source.sendSuccess(() -> Component.literal(
                String.format("Marker Block created at %s with color 0x%08X for %d tick%s",
                        pos.toShortString(), color, duration, textInfo)
        ), true);

        return 1;
    }

    private static int createBillboard(CommandContext<CommandSourceStack> context, String colorHex, int duration, String text) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        int color = parseHexColor(colorHex);

        if (text != null) {
            MarkRenderer.getInstance().addBillboardMarker(pos, color, duration, text);
        } else {
            MarkRenderer.getInstance().addBillboardMarker(pos, color, duration);
        }

        String textInfo = text != null ? " with text \"" + text + "\"" : "";
        source.sendSuccess(() -> Component.literal(
                String.format("Billboard Marker created at %s with color 0x%08X for %d tick%s",
                        pos.toShortString(), color, duration, textInfo)
        ), true);

        return 1;
    }

    private static int createBillboardLooking(CommandContext<CommandSourceStack> context, String colorHex, int duration, String text) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayerOrException();

        HitResult hitResult = player.pick(20.0, 0.0F, false);

        if (!(hitResult instanceof BlockHitResult) || hitResult.getType() == HitResult.Type.MISS) {
            source.sendFailure(Component.literal("No block found. You must look at a block."));
            return 0;
        }

        BlockPos pos = ((BlockHitResult) hitResult).getBlockPos();
        int color = parseHexColor(colorHex);

        if (text != null) {
            MarkRenderer.getInstance().addBillboardMarker(pos, color, duration, text);
        } else {
            MarkRenderer.getInstance().addBillboardMarker(pos, color, duration);
        }

        String textInfo = text != null ? " with text \"" + text + "\"" : "";
        source.sendSuccess(() -> Component.literal(
                String.format("Billboard Marker created at %s with color 0x%08X for %d tick%s",
                        pos.toShortString(), color, duration, textInfo)
        ), true);

        return 1;
    }

    private static int clearMarkers(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MarkRenderer.getInstance().clearHighlightedBlocks();
        source.sendSuccess(() -> Component.literal("All markers have been removed"), true);
        return 1;
    }
}
