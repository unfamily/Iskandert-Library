package net.unfamily.iskalib.integration.ftbquests;

import de.marhali.json5.Json5Object;
import dev.ftb.mods.ftblibrary.client.config.EditableConfigGroup;
import dev.ftb.mods.ftblibrary.json5.Json5Util;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.AbstractBooleanTask;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.unfamily.iskalib.team.ShopTeamManager;

public final class IskaCurrencyTask extends AbstractBooleanTask {
    static TaskType TYPE;

    private String currency = "null_coin";
    private double amount = 1.0;
    /** When true (default): click to submit and deduct currency. When false: auto-complete on balance. */
    private boolean consume = true;

    public IskaCurrencyTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return TYPE;
    }

    @Override
    public void writeData(Json5Object json, HolderLookup.Provider provider) {
        super.writeData(json, provider);
        json.addProperty("currency", currency);
        json.addProperty("amount", amount);
        if (!consume) {
            json.addProperty("consume", false);
        }
    }

    @Override
    public void readData(Json5Object json, HolderLookup.Provider provider) {
        super.readData(json, provider);
        currency = Json5Util.getString(json, "currency").orElse("null_coin");
        amount = Math.max(0.0, Json5Util.getDouble(json, "amount").orElse(1.0));
        consume = Json5Util.getBoolean(json, "consume").orElse(true);
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(currency, Short.MAX_VALUE);
        buffer.writeDouble(amount);
        buffer.writeBoolean(consume);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        currency = buffer.readUtf(Short.MAX_VALUE);
        amount = Math.max(0.0, buffer.readDouble());
        consume = buffer.readBoolean();
    }

    @Override
    public void fillConfigGroup(EditableConfigGroup config) {
        super.fillConfigGroup(config);
        IskaQuestsHelper.addCurrencySelector(config, currency, value -> {
                    currency = value;
                    clearCachedData();
                },
                "ftbquests.task.iska_lib.iska_currency.currency");
        config.addDouble("amount", amount, value -> {
                    amount = value;
                    clearCachedData();
                }, 1.0, 0.0, Double.MAX_VALUE)
                .setNameKey("ftbquests.task.iska_lib.iska_currency.amount");
        config.addBool("consume", consume, value -> consume = value, true)
                .setNameKey("ftbquests.task.iska_lib.iska_currency.consume");
    }

    @Override
    public void editedFromGUI() {
        clearCachedData();
        super.editedFromGUI();
    }

    @Override
    public boolean consumesResources() {
        return consume;
    }

    @Override
    public boolean checkOnLogin() {
        return !consumesResources();
    }

    @Override
    public int autoSubmitOnPlayerTick() {
        // 0 => click-to-submit (TaskClient); >0 => auto-complete when balance is reached
        return consumesResources() ? 0 : 20;
    }

    @Override
    public boolean canSubmit(TeamData teamData, ServerPlayer player) {
        if (!IskaQuestsHelper.isValidCurrencyGoal(currency, amount)) {
            return false;
        }
        ShopTeamManager manager = ShopTeamManager.getInstance((ServerLevel) player.level());
        String teamKey = manager.getPlayerTeamKey(player);
        return teamKey != null && manager.getTeamCurrencyBalance(teamKey, currency) >= amount;
    }

    @Override
    public void submitTask(TeamData teamData, ServerPlayer player, ItemStack craftedItem) {
        if (teamData.isCompleted(this) || !checkTaskSequence(teamData) || !canSubmit(teamData, player)) {
            return;
        }
        if (consumesResources()) {
            ShopTeamManager manager = ShopTeamManager.getInstance((ServerLevel) player.level());
            String teamKey = manager.getPlayerTeamKey(player);
            if (teamKey == null || !manager.removeTeamCurrency(teamKey, currency, amount)) {
                return;
            }
        }
        teamData.setProgress(this, 1L);
    }

    @Override
    public MutableComponent getAltTitle() {
        return IskaQuestsHelper.currencyPlayerTitle(currency, amount);
    }
}
