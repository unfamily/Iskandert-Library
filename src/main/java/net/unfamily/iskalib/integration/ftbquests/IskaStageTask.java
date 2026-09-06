package net.unfamily.iskalib.integration.ftbquests;

import dev.architectury.hooks.level.entity.PlayerHooks;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.AbstractBooleanTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.unfamily.iskalib.stage.StageRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class IskaStageTask extends AbstractBooleanTask {
    public static TaskType TYPE;

    private String stage = "";
    private String scope = "player";
    private boolean is = true;

    public IskaStageTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return TYPE;
    }

    @Override
    public void writeData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.writeData(nbt, provider);
        nbt.putString("stage", stage);
        nbt.putString("scope", scope);
        nbt.putBoolean("is", is);
    }

    @Override
    public void readData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.readData(nbt, provider);
        stage = nbt.getString("stage");
        scope = IskaQuestsHelper.normalizeScope(nbt.getString("scope"));
        is = !nbt.contains("is") || nbt.getBoolean("is");
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(stage, Short.MAX_VALUE);
        buffer.writeUtf(scope, 16);
        buffer.writeBoolean(is);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        stage = buffer.readUtf(Short.MAX_VALUE);
        scope = IskaQuestsHelper.normalizeScope(buffer.readUtf(16));
        is = buffer.readBoolean();
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addString("stage", stage, value -> {
                    stage = value;
                    clearCachedData();
                }, "")
                .setNameKey("ftbquests.task.iska_lib.iska_stage.stage");
        IskaQuestsHelper.addScopeSelector(config, scope, value -> {
            scope = value;
            clearCachedData();
        }, "ftbquests.task.iska_lib.iska_stage.scope");
        IskaQuestsHelper.addStageRequirementSelector(config, is, value -> {
            is = value;
            clearCachedData();
        });
    }

    @Override
    public MutableComponent getAltTitle() {
        return IskaQuestsHelper.stageRequirementTitle(stage, is);
    }

    @Override
    public int autoSubmitOnPlayerTick() {
        // Fallback poll once per second; StageHooks still triggers immediate checks on change.
        return 20;
    }

    @Override
    public boolean canSubmit(TeamData teamData, ServerPlayer player) {
        if (!IskaQuestsHelper.isValidStageId(stage)) {
            return false;
        }
        StageRegistry registry = StageRegistry.getInstance(player.getServer());
        boolean hasStage = switch (scope) {
            case "world" -> registry.hasWorldStage(stage);
            case "team" -> registry.hasPlayerTeamStage(player, stage);
            default -> registry.hasPlayerStage(player, stage);
        };
        return is == hasStage;
    }

    public static void checkStages(ServerPlayer player) {
        checkStages(player, null);
    }

    public static void checkStages(ServerPlayer player, @Nullable String changedStage) {
        if (player == null || ServerQuestFile.INSTANCE == null || PlayerHooks.isFake(player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        UUID playerId = player.getUUID();
        server.execute(() -> {
            ServerPlayer online = server.getPlayerList().getPlayer(playerId);
            if (online == null || PlayerHooks.isFake(online) || ServerQuestFile.INSTANCE == null) {
                return;
            }
            ServerQuestFile.INSTANCE.getTeamData(online).ifPresent(data -> {
                if (data.isLocked()) {
                    return;
                }
                ServerQuestFile.INSTANCE.withPlayerContext(online, () -> {
                    for (Task task : ServerQuestFile.INSTANCE.getAllTasks()) {
                        if (task instanceof IskaStageTask stageTask
                                && data.canStartTasks(stageTask.getQuest())
                                && (changedStage == null || changedStage.equals(stageTask.stage))) {
                            stageTask.submitTask(data, online);
                        }
                    }
                });
            });
        });
    }
}
