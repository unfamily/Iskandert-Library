package net.unfamily.iskalib.integration.ftbquests;

import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.unfamily.iskalib.stage.StageHooks;
import net.unfamily.iskalib.team.ShopTeamManager;

public final class FtbQuestsIntegration {
    private static boolean initialized;

    private static final StageHooks.Listener STAGE_LISTENER = new StageHooks.Listener() {
        @Override
        public void onPlayerStageChanged(ServerPlayer player, String stage, boolean value) {
            IskaStageTask.checkStages(player, stage);
        }

        @Override
        public void onWorldStageChanged(MinecraftServer server, String stage, boolean value) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                IskaStageTask.checkStages(player, stage);
            }
        }

        @Override
        public void onTeamStageChanged(MinecraftServer server, String teamName, String stage, boolean value) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ShopTeamManager manager = ShopTeamManager.getInstance((ServerLevel) player.level());
                if (teamName.equals(manager.getPlayerTeamKey(player))) {
                    IskaStageTask.checkStages(player, stage);
                }
            }
        }
    };

    private FtbQuestsIntegration() {}

    public static synchronized void init() {
        if (initialized) return;

        Identifier stageId = Identifier.fromNamespaceAndPath("iska_lib", "iska_stage");
        Identifier currencyId = Identifier.fromNamespaceAndPath("iska_lib", "iska_currency");

        IskaStageTask.TYPE = TaskTypes.register(stageId, IskaStageTask::new, () -> Icons.CONTROLLER);
        IskaStageReward.TYPE = RewardTypes.register(stageId, IskaStageReward::new, () -> Icons.CONTROLLER);
        IskaCurrencyTask.TYPE = TaskTypes.register(currencyId, IskaCurrencyTask::new,
                () -> Icon.getIcon("iska_lib:textures/gui/null_coin.png"));
        IskaCurrencyReward.TYPE = RewardTypes.register(currencyId, IskaCurrencyReward::new,
                () -> Icon.getIcon("iska_lib:textures/gui/null_coin.png"));

        StageHooks.addListener(STAGE_LISTENER);
        initialized = true;
    }
}
