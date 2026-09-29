package net.unfamily.iskalib.integration.ftbultimine;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.unfamily.iskalib.IskaLibConfig;
import net.unfamily.iskalib.stage.StageRegistry;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.ftb.mods.ftbultimine.api.restriction.RegisterRestrictionHandlerEvent;

/**
 * Soft-dep Ultimine gate: when enabled in config, Ultimine requires a Library stage.
 * Uses Architectury {@link RegisterRestrictionHandlerEvent#REGISTER} (FTB Ultimine 2101.x).
 */
public final class FtbUltimineIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();

    private FtbUltimineIntegration() {}

    public static void init() {
        RegisterRestrictionHandlerEvent.REGISTER.register(registry ->
                registry.register(FtbUltimineIntegration::canUltimine));
        LOGGER.info("FTB Ultimine stage gate listener registered");
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
