package net.unfamily.iskalib.stage;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
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
        StageCatalog.addContributor(new StageCatalog.Contributor() {
            @Override
            public Collection<String> knownStages() {
                return collectKnownStages();
            }

            @Override
            public Collection<String> knownStagesForType(String stageType) {
                return collectKnownStages(stageType);
            }
        });
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
        KnownStagesLoader.loadAll(null);
        // Another Stages owns item gating when present (avoids double-filter).
        if (!isAnotherStagesLoaded()) {
            StageItemEvents.initialize();
        }
        LOGGER.info("Library stage loaders installed");
    }

    private static boolean isAnotherStagesLoaded() {
        return ModList.get().isLoaded("another_stages");
    }

    private static Collection<String> collectKnownStages() {
        return collectKnownStages(null);
    }

    private static Collection<String> collectKnownStages(@Nullable String stageType) {
        String want = stageType == null || stageType.isBlank()
                ? null
                : stageType.trim().toLowerCase(java.util.Locale.ROOT);
        if ("team_player".equals(want)) {
            want = "team";
        }
        Set<String> stages = new LinkedHashSet<>();
        for (StageActionDefinition def : StageActionsLoader.getLoadedActions()) {
            for (StageActionDefinition.StageCondition condition : def.getStages()) {
                if (condition == null || condition.stage == null || condition.stage.isBlank()) {
                    continue;
                }
                String type = condition.stageType == null || condition.stageType.isBlank()
                        ? "player"
                        : condition.stageType.trim().toLowerCase(java.util.Locale.ROOT);
                if (want != null && !want.equals(type)) {
                    continue;
                }
                stages.add(condition.stage.trim());
            }
        }
        if (!isAnotherStagesLoaded() && want == null) {
            stages.addAll(StageItemHandler.collectReferencedStages());
        }
        return stages;
    }

    private static int reloadStageBlock(CommandSourceStack source) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        // Prefer the ResourceManager from datapack /reload apply (ThreadLocal); server RM can be stale mid-reload.
        ResourceManager pending = StageReloadHooks.currentResourceManagerOrNull();
        ResourceManager rm = pending != null
                ? pending
                : (server != null ? server.getResourceManager() : null);
        StageActionsLoader.loadAll(rm);
        KnownStagesLoader.loadAll(rm);
        if (!isAnotherStagesLoaded()) {
            StageItemHandler.loadAll(rm);
            LOGGER.info("Reloaded stage actions ({}), known stages, and stage items",
                    StageActionsLoader.getActionIds().size());
        } else {
            LOGGER.info("Reloaded stage actions ({}) and known stages; item restrictions owned by Another Stages",
                    StageActionsLoader.getActionIds().size());
        }
        return 1;
    }
}
