package net.unfamily.iskalib.stage;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Wires Library-owned stage action/item loaders into catalog, hooks, and reload.
 */
public final class StageBootstrap {
    private static final Logger LOGGER = LoggerFactory.getLogger(StageBootstrap.class);
    private static boolean installed;

    private StageBootstrap() {}

    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        StageCatalog.addContributor(StageBootstrap::collectKnownStages);
        StageReloadHooks.setListener(StageBootstrap::reloadStageBlock);
        StageActionHooks.setListener(new StageActionHooks.Listener() {
            @Override
            public List<String> listActionIds() {
                return StageActionsLoader.getActionIds();
            }

            @Override
            public int executeActionById(String actionId, List<ServerPlayer> players, boolean force) {
                return StageActionsManager.executeActionById(actionId, players, force);
            }
        });
        StageHooks.addListener(new StageHooks.Listener() {
            @Override
            public void onPlayerStageChanged(ServerPlayer player, String stage, boolean value) {
                StageActionsManager.onPlayerStageChanged(player, stage, value);
            }

            @Override
            public void onWorldStageChanged(MinecraftServer server, String stage, boolean value) {
                StageActionsManager.onWorldStageChanged(server, stage, value);
            }

            @Override
            public void onTeamStageChanged(MinecraftServer server, String teamName, String stage, boolean value) {
                StageActionsManager.onTeamStageChanged(server, teamName, stage, value);
            }
        });
        StageActionsLoader.scanConfigDirectory();
        StageItemEvents.initialize();
        LOGGER.info("Library stage loaders installed");
    }

    private static Collection<String> collectKnownStages() {
        Set<String> stages = new LinkedHashSet<>();
        for (StageActionDefinition def : StageActionsLoader.getLoadedActions()) {
            for (StageActionDefinition.StageCondition condition : def.getStages()) {
                if (condition != null && condition.stage != null && !condition.stage.isBlank()) {
                    stages.add(condition.stage.trim());
                }
            }
        }
        stages.addAll(StageItemHandler.collectReferencedStages());
        return stages;
    }

    private static int reloadStageBlock(CommandSourceStack source) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ResourceManager rm = server != null ? server.getResourceManager() : null;
        StageActionsLoader.loadAll(rm);
        StageItemHandler.loadAll(rm);
        LOGGER.info("Reloaded stage actions ({}) and stage items", StageActionsLoader.getActionIds().size());
        return 1;
    }
}
