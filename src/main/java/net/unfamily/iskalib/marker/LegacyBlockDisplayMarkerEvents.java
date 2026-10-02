package net.unfamily.iskalib.marker;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.unfamily.iskalib.IskaLib;
import org.jetbrains.annotations.Nullable;

/**
 * Server lifecycle hooks for legacy scanner markers implemented with
 * {@code block_display} entities (command-spawned, {@code temp_scan} tag, session tags).
 * <p>
 * Periodic orphan cleanup is disabled. Client-side world markers
 * ({@link net.unfamily.iskalib.client.marker.MarkRenderer}) are separate.
 */
@EventBusSubscriber(modid = IskaLib.MOD_ID)
public final class LegacyBlockDisplayMarkerEvents {
    private LegacyBlockDisplayMarkerEvents() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        MarkerSession.resetScannerSessionId();
        ScannerMarkerCleanup.ensureDisplayTeams(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MarkerSession.resetScannerSessionId();
    }

    /**
     * No-op. Orphaned legacy marker cleanup is intentionally disabled.
     */
    public static void runCleanupIfPlayer(LevelAccessor world, @Nullable Entity entity) {
        // Disabled: do not scan or kill orphaned block_display markers.
    }
}
