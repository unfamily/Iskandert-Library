package net.unfamily.iskalib.integration.ftbultimine;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.unfamily.iskalib.IskaLibConfig;
import net.unfamily.iskalib.stage.StageRegistry;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

/**
 * Soft-dep Ultimine gate: when enabled in config, Ultimine requires a Library stage.
 * Uses FTB Ultimine {@code RestrictionHandler} via NeoForge 26 register event.
 */
public final class FtbUltimineIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();

    private FtbUltimineIntegration() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(FtbUltimineIntegration::onRegisterRestriction);
        LOGGER.info("FTB Ultimine stage gate listener registered");
    }

    private static void onRegisterRestriction(
            dev.ftb.mods.ftbultimine.api.neoforge.FTBUltimineEvent.RegisterRestrictionHandler event) {
        event.getEventData().register(FtbUltimineIntegration::canUltimine);
    }

    static boolean canUltimine(Player player) {
        if (!IskaLibConfig.ENABLE_ULTIMINE_STAGE_GATE.get()) {
            return true;
        }
        String required = IskaLibConfig.ULTIMINE_REQUIRED_STAGE.get();
        if (required == null || required.isBlank()) {
            return true;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        var server = serverPlayer.level().getServer();
        if (server == null) {
            return false;
        }
        StageRegistry registry = StageRegistry.getInstance(server);
        String scope = IskaLibConfig.ULTIMINE_STAGE_SCOPE.get();
        return switch (scope == null ? "player" : scope.toLowerCase()) {
            case "world" -> registry.hasWorldStage(required);
            case "team" -> registry.hasPlayerTeamStage(serverPlayer, required);
            default -> registry.hasPlayerStage(serverPlayer, required);
        };
    }
}
