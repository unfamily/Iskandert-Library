package net.unfamily.iskalib.marker;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Server-side helpers for legacy temporary scanner markers spawned as {@code block_display}
 * entities (e.g. tag {@code temp_scan}). Wiring is registered in {@link LegacyBlockDisplayMarkerEvents}.
 * <p>
 * Orphan cleanup is intentionally disabled (no periodic scan / kill).
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
     * No-op. Orphaned marker cleanup is intentionally disabled.
     */
    public static void cleanupOrphanedMarkers(ServerLevel level) {
        // Disabled.
    }
}
