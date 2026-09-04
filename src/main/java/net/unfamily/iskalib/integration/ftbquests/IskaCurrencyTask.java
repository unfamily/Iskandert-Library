package net.unfamily.iskalib.integration.ftbquests;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.AbstractBooleanTask;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.unfamily.iskalib.team.ShopTeamManager;

public class IskaCurrencyTask extends AbstractBooleanTask {
    public static TaskType TYPE;

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
    public void writeData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.writeData(nbt, provider);
        nbt.putString("currency", currency);
        nbt.putDouble("amount", amount);
        nbt.putBoolean("consume", consume);
    }

    @Override
    public void readData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.readData(nbt, provider);
        currency = nbt.contains("currency") ? nbt.getString("currency") : "null_coin";
        amount = Math.max(0.0, nbt.contains("amount") ? nbt.getDouble("amount") : 1.0);
        consume = !nbt.contains("consume") || nbt.getBoolean("consume");
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
    public void fillConfigGroup(ConfigGroup config) {
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
        ShopTeamManager manager = ShopTeamManager.getInstance(player.serverLevel());
        String team = manager.getPlayerTeamKey(player);
        return team != null && manager.getTeamCurrencyBalance(team, currency) >= amount;
    }

    @Override
    public void submitTask(TeamData teamData, ServerPlayer player, ItemStack craftedItem) {
        if (teamData.isCompleted(this) || !checkTaskSequence(teamData) || !canSubmit(teamData, player)) {
            return;
        }
        if (consumesResources()) {
            ShopTeamManager manager = ShopTeamManager.getInstance(player.serverLevel());
            String team = manager.getPlayerTeamKey(player);
            if (team == null || !manager.removeTeamCurrency(team, currency, amount)) {
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
