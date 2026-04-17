package net.unfamily.iskalib.marker;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * Server-side cleanup utilities for temporary scanner markers.
 */
public final class ScannerMarkerCleanup {
    private ScannerMarkerCleanup() {}

    public static void ensureDisplayTeams(MinecraftServer server) {
        try {
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(),
                    "team add blue"
            );
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(),
                    "team modify blue color blue"
            );

            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(),
                    "team add red"
            );
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(),
                    "team modify red color red"
            );
        } catch (Exception ignored) {
            // Teams may already exist; ignore.
        }
    }

    /**
     * Removes orphaned marker entities from previous sessions.
     */
    public static void cleanupOrphanedMarkers(ServerLevel level) {
        UUID sessionId = MarkerSession.getScannerSessionId();
        String sessionTag = "session_" + sessionId;

        String listCommand = String.format(
                "execute as @e[type=block_display,tag=temp_scan] unless entity @s[tag=%s] run tag @s add scan_cleanup",
                sessionTag
        );

        level.getServer().getCommands().performPrefixedCommand(
                level.getServer().createCommandSourceStack().withSuppressedOutput(),
                listCommand
        );

        level.getServer().getCommands().performPrefixedCommand(
                level.getServer().createCommandSourceStack().withSuppressedOutput(),
                "team leave @e[type=block_display,tag=scan_cleanup]"
        );

        level.getServer().getCommands().performPrefixedCommand(
                level.getServer().createCommandSourceStack().withSuppressedOutput(),
                "kill @e[type=block_display,tag=scan_cleanup]"
        );
    }
}

