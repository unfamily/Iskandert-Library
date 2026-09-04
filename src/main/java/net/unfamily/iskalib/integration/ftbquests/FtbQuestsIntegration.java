package net.unfamily.iskalib.integration.ftbquests;

import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import net.minecraft.resources.ResourceLocation;
import net.unfamily.iskalib.stage.StageHooks;
import net.unfamily.iskalib.team.ShopTeamManager;

/**
 * Registers Iska Library task and reward types when FTB Quests is installed.
 */
public final class FtbQuestsIntegration {
    private static boolean initialized;

    private FtbQuestsIntegration() {}

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        IskaStageTask.TYPE = TaskTypes.register(id("iska_stage"), IskaStageTask::new, () -> Icons.CONTROLLER);
        IskaStageReward.TYPE = RewardTypes.register(id("iska_stage"), IskaStageReward::new, () -> Icons.CONTROLLER);
        IskaCurrencyTask.TYPE = TaskTypes.register(id("iska_currency"), IskaCurrencyTask::new,
                () -> Icon.getIcon("iska_lib:textures/gui/null_coin.png"));
        IskaCurrencyReward.TYPE = RewardTypes.register(id("iska_currency"), IskaCurrencyReward::new,
                () -> Icon.getIcon("iska_lib:textures/gui/null_coin.png"));

        StageHooks.addListener(new StageHooks.Listener() {
            @Override
            public void onPlayerStageChanged(net.minecraft.server.level.ServerPlayer player, String stage, boolean value) {
                IskaStageTask.checkStages(player, stage);
            }

            @Override
            public void onWorldStageChanged(net.minecraft.server.MinecraftServer server, String stage, boolean value) {
                for (var player : server.getPlayerList().getPlayers()) {
                    IskaStageTask.checkStages(player, stage);
                }
            }

            @Override
            public void onTeamStageChanged(net.minecraft.server.MinecraftServer server, String teamName,
                                           String stage, boolean value) {
                for (var player : server.getPlayerList().getPlayers()) {
                    String playerTeam = ShopTeamManager.getInstance(player.serverLevel()).getPlayerTeamKey(player);
                    if (teamName.equals(playerTeam)) {
                        IskaStageTask.checkStages(player, stage);
                    }
                }
            }
        });
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("iska_lib", path);
    }
}
