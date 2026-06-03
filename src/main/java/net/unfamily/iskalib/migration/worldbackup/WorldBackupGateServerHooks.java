package net.unfamily.iskalib.migration.worldbackup;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Syncs per-world backup ack from disk into SavedData after the integrated/dedicated server starts.
 */
public final class WorldBackupGateServerHooks {
    private WorldBackupGateServerHooks() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!WorldBackupGate.isEnabled()) {
            return;
        }
        for (WorldBackupGateConfig config : WorldBackupGate.configs()) {
            WorldBackupGate.autoAcknowledgeFreshWorlds(event.getServer(), config);
            if (event.getServer().overworld() != null) {
                WorldBackupGate.syncAckFromDisk(event.getServer().overworld(), config);
            }
        }
    }
}
