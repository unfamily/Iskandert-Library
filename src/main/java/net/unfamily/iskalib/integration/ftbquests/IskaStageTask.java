package net.unfamily.iskalib.integration.ftbquests;

import de.marhali.json5.Json5Object;
import dev.ftb.mods.ftblibrary.client.config.EditableConfigGroup;
import dev.ftb.mods.ftblibrary.json5.Json5Util;
import dev.ftb.mods.ftblibrary.platform.Platform;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.AbstractBooleanTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.unfamily.iskalib.stage.StageRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class IskaStageTask extends AbstractBooleanTask {
    static TaskType TYPE;

    private String stage = "";
    private String scope = "player";

    public IskaStageTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return TYPE;
    }

    @Override
    public void writeData(Json5Object json, HolderLookup.Provider provider) {
        super.writeData(json, provider);
        json.addProperty("stage", stage);
        if (!scope.equals("player")) json.addProperty("scope", scope);
    }

    @Override
    public void readData(Json5Object json, HolderLookup.Provider provider) {
        super.readData(json, provider);
        stage = Json5Util.getString(json, "stage").orElse("");
        scope = IskaQuestsHelper.normalizeScope(Json5Util.getString(json, "scope").orElse("player"));
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(stage, Short.MAX_VALUE);
        buffer.writeUtf(scope, 16);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        stage = buffer.readUtf(Short.MAX_VALUE);
        scope = IskaQuestsHelper.normalizeScope(buffer.readUtf(16));
    }

    @Override
    public void fillConfigGroup(EditableConfigGroup config) {
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
    }

    @Override
    public MutableComponent getAltTitle() {
        return IskaQuestsHelper.stagePlayerTitle(stage);
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
        StageRegistry registry = StageRegistry.getInstance(player.level().getServer());
        return switch (scope) {
            case "world" -> registry.hasWorldStage(stage);
            case "team" -> registry.hasPlayerTeamStage(player, stage);
            default -> registry.hasPlayerStage(player, stage);
        };
    }

    public static void checkStages(ServerPlayer player) {
        checkStages(player, null);
    }

    public static void checkStages(ServerPlayer player, @Nullable String changedStage) {
        if (player == null || Platform.get().misc().isFakePlayer(player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        UUID playerId = player.getUUID();
        // Defer so stage persistence and other listeners finish before quest evaluation.
        server.execute(() -> {
            ServerPlayer online = server.getPlayerList().getPlayer(playerId);
            if (online == null || Platform.get().misc().isFakePlayer(online)) {
                return;
            }
            ServerQuestFile.ifExists(file -> file.getTeamData(online).ifPresent(data -> {
                if (data.isLocked()) {
                    return;
                }
                file.withPlayerContext(online, () -> {
                    for (Task task : file.getAllTasks()) {
                        if (task instanceof IskaStageTask stageTask
                                && data.canStartTasks(stageTask.getQuest())
                                && (changedStage == null || changedStage.equals(stageTask.stage))) {
                            stageTask.submitTask(data, online);
                        }
                    }
                });
            }));
        });
    }
}
