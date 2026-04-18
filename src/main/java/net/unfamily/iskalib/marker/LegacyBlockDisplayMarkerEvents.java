package net.unfamily.iskalib.marker;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.unfamily.iskalib.IskaLib;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Server lifecycle and tick hooks for legacy scanner markers implemented with
 * {@code block_display} entities (command-spawned, {@code temp_scan} tag, session tags).
 * <p>
 * Client-side world markers ({@link net.unfamily.iskalib.client.marker.MarkRenderer}) are separate.
 */
@EventBusSubscriber(modid = IskaLib.MOD_ID)
public final class LegacyBlockDisplayMarkerEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    private LegacyBlockDisplayMarkerEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        runCleanupIfPlayer(event.getEntity().level(), event.getEntity());
    }

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
     * Optional entry point for mods that need to trigger cleanup from their own tick paths.
     */
    public static void runCleanupIfPlayer(LevelAccessor world, @Nullable Entity entity) {
        if (entity == null || world == null || !(world instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer)) {
            return;
        }
        try {
            ScannerMarkerCleanup.cleanupOrphanedMarkers(serverLevel);
            if (serverLevel.getGameTime() % 1200 == 0) {
                LOGGER.debug("Checked orphaned legacy block_display scanner markers");
            }
        } catch (Exception e) {
            LOGGER.error("Error cleaning legacy scanner markers: {}", e.getMessage());
        }
    }
}
