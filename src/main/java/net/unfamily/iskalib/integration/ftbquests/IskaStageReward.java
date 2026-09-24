package net.unfamily.iskalib.integration.ftbquests;

import de.marhali.json5.Json5Object;
import dev.ftb.mods.ftblibrary.client.config.EditableConfigGroup;
import dev.ftb.mods.ftblibrary.json5.Json5Util;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.unfamily.iskalib.stage.StageRegistry;

public final class IskaStageReward extends Reward {
    static RewardType TYPE;

    private String stage = "";
    private String scope = "player";
    /** {@code add} grants the stage; {@code remove} clears it. */
    private String mode = "add";
    /**
     * When scope is "team", whether to distribute this reward to the entire team.
     * Hidden in the config UI when scope != "team"; defaults to true when scope == "team".
     */
    private boolean teamReward = true;

    public IskaStageReward(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public RewardType getType() {
        return TYPE;
    }

    @Override
    public void writeData(Json5Object json, HolderLookup.Provider provider) {
        super.writeData(json, provider);
        json.addProperty("stage", stage);
        if (!scope.equals("player")) json.addProperty("scope", scope);
        if (IskaQuestsHelper.isRemoveStageMode(mode)) {
            json.addProperty("mode", "remove");
            json.addProperty("remove", true); // backwards compatible with older quest files
        }
        if ("team".equals(scope)) json.addProperty("team_reward", teamReward);
    }

    @Override
    public void readData(Json5Object json, HolderLookup.Provider provider) {
        super.readData(json, provider);
        stage = Json5Util.getString(json, "stage").orElse("");
        scope = IskaQuestsHelper.normalizeScope(Json5Util.getString(json, "scope").orElse("player"));
        mode = Json5Util.getString(json, "mode")
                .map(IskaQuestsHelper::normalizeStageMode)
                .orElseGet(() -> Json5Util.getBoolean(json, "remove").orElse(false) ? "remove" : "add");
        teamReward = "team".equals(scope) && Json5Util.getBoolean(json, "team_reward").orElse(true);
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(stage, Short.MAX_VALUE);
        buffer.writeUtf(scope, 16);
        buffer.writeUtf(mode, 16);
        buffer.writeBoolean(teamReward);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        stage = buffer.readUtf(Short.MAX_VALUE);
        scope = IskaQuestsHelper.normalizeScope(buffer.readUtf(16));
        mode = IskaQuestsHelper.normalizeStageMode(buffer.readUtf(16));
        teamReward = buffer.readBoolean();
    }

    @Override
    public void fillConfigGroup(EditableConfigGroup config) {
        super.fillConfigGroup(config);
        config.addString("stage", stage, value -> {
                    stage = value;
                    clearCachedData();
                }, "")
                .setNameKey("ftbquests.reward.iska_lib.iska_stage.stage");
        IskaQuestsHelper.addScopeSelector(config, scope, value -> {
            String previous = scope;
            scope = value;
            // B5: default teamReward = true when switching to team scope
            if ("team".equals(scope) && !"team".equals(previous)) {
                teamReward = true;
            }
            clearCachedData();
        }, "ftbquests.reward.iska_lib.iska_stage.scope");
        IskaQuestsHelper.addStageModeSelector(config, mode, value -> {
            mode = value;
            clearCachedData();
        }, "ftbquests.reward.iska_lib.iska_stage.mode");
        // B5: team_reward toggle — only visible when scope == "team"
        if ("team".equals(scope)) {
            config.addBool("team_reward", teamReward, value -> {
                teamReward = value;
                clearCachedData();
            }, true).setNameKey("ftbquests.reward.iska_lib.iska_stage.team_reward");
        }
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
            StageRegistry registry = StageRegistry.getInstance(player.level().getServer());
            boolean enable = !remove;
            success = switch (scope) {
                case "world" -> registry.setWorldStage(stage, enable);
                case "team" -> registry.setPlayerTeamStage(player, stage, enable);
                default -> registry.setPlayerStage(player, stage, enable);
            };
        }
        // B2: actionbar feedback removed — silent claim.
    }

    @Override
    public MutableComponent getAltTitle() {
        // Team-only rewards keep the default (non-blue) title — FTB paints team rewards aqua.
        return IskaQuestsHelper.stagePlayerTitle(stage, IskaQuestsHelper.isRemoveStageMode(mode));
    }
}
