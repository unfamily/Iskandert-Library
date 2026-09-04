package net.unfamily.iskalib.integration.ftbquests;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.unfamily.iskalib.stage.StageRegistry;

public class IskaStageReward extends Reward {
    public static RewardType TYPE;

    private String stage = "";
    private String scope = "player";
    /** {@code add} grants the stage; {@code remove} clears it. */
    private String mode = "add";

    public IskaStageReward(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public RewardType getType() {
        return TYPE;
    }

    @Override
    public void writeData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.writeData(nbt, provider);
        nbt.putString("stage", stage);
        nbt.putString("scope", scope);
        nbt.putString("mode", mode);
        nbt.putBoolean("remove", IskaQuestsHelper.isRemoveStageMode(mode));
    }

    @Override
    public void readData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.readData(nbt, provider);
        stage = nbt.getString("stage");
        scope = IskaQuestsHelper.normalizeScope(nbt.getString("scope"));
        if (nbt.contains("mode")) {
            mode = IskaQuestsHelper.normalizeStageMode(nbt.getString("mode"));
        } else {
            mode = nbt.getBoolean("remove") ? "remove" : "add";
        }
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(stage, Short.MAX_VALUE);
        buffer.writeUtf(scope, 16);
        buffer.writeUtf(mode, 16);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        stage = buffer.readUtf(Short.MAX_VALUE);
        scope = IskaQuestsHelper.normalizeScope(buffer.readUtf(16));
        mode = IskaQuestsHelper.normalizeStageMode(buffer.readUtf(16));
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addString("stage", stage, value -> {
                    stage = value;
                    clearCachedData();
                }, "")
                .setNameKey("ftbquests.reward.iska_lib.iska_stage.stage");
        IskaQuestsHelper.addScopeSelector(config, scope, value -> {
            scope = value;
            clearCachedData();
        }, "ftbquests.reward.iska_lib.iska_stage.scope");
        IskaQuestsHelper.addStageModeSelector(config, mode, value -> {
            mode = value;
            clearCachedData();
        }, "ftbquests.reward.iska_lib.iska_stage.mode");
    }

    @Override
    public void editedFromGUI() {
        clearCachedData();
        super.editedFromGUI();
    }

    @Override
    public void claim(ServerPlayer player, boolean notify) {
        boolean remove = IskaQuestsHelper.isRemoveStageMode(mode);
        boolean success = false;
        if (IskaQuestsHelper.isValidStageId(stage)) {
            StageRegistry registry = StageRegistry.getInstance(player.getServer());
            boolean enable = !remove;
            success = switch (scope) {
                case "world" -> registry.setWorldStage(stage, enable);
                case "team" -> registry.setPlayerTeamStage(player, stage, enable);
                default -> registry.setPlayerStage(player, stage, enable);
            };
        }
        if (notify && success) {
            player.sendSystemMessage(Component.translatable(
                    remove ? "ftbquests.reward.iska_lib.iska_stage.removed"
                            : "ftbquests.reward.iska_lib.iska_stage.added",
                    stage, scope), true);
        }
    }

    @Override
    public MutableComponent getAltTitle() {
        return IskaQuestsHelper.stagePlayerTitle(stage, IskaQuestsHelper.isRemoveStageMode(mode));
    }
}
