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
import net.unfamily.iskalib.team.ShopTeamManager;

public class IskaCurrencyReward extends Reward {
    public static RewardType TYPE;

    private String currency = "null_coin";
    private double amount = 1.0;
    private boolean remove;

    public IskaCurrencyReward(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public RewardType getType() {
        return TYPE;
    }

    @Override
    public void writeData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.writeData(nbt, provider);
        nbt.putString("currency", currency);
        nbt.putDouble("amount", amount);
        nbt.putBoolean("remove", remove);
    }

    @Override
    public void readData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.readData(nbt, provider);
        currency = nbt.contains("currency") ? nbt.getString("currency") : "null_coin";
        amount = Math.max(0.0, nbt.contains("amount") ? nbt.getDouble("amount") : 1.0);
        remove = nbt.getBoolean("remove");
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(currency, Short.MAX_VALUE);
        buffer.writeDouble(amount);
        buffer.writeBoolean(remove);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        currency = buffer.readUtf(Short.MAX_VALUE);
        amount = Math.max(0.0, buffer.readDouble());
        remove = buffer.readBoolean();
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        IskaQuestsHelper.addCurrencySelector(config, currency, value -> {
                    currency = value;
                    clearCachedData();
                },
                "ftbquests.reward.iska_lib.iska_currency.currency");
        config.addDouble("amount", amount, value -> {
                    amount = value;
                    clearCachedData();
                }, 1.0, 0.0, Double.MAX_VALUE)
                .setNameKey("ftbquests.reward.iska_lib.iska_currency.amount");
        config.addBool("remove", remove, value -> remove = value, false);
    }

    @Override
    public void editedFromGUI() {
        clearCachedData();
        super.editedFromGUI();
    }

    @Override
    public void claim(ServerPlayer player, boolean notify) {
        boolean success = false;
        if (IskaQuestsHelper.isValidCurrencyGoal(currency, amount)) {
            ShopTeamManager manager = ShopTeamManager.getInstance(player.serverLevel());
            String team = manager.getPlayerTeamKey(player);
            success = team != null && (remove
                    ? manager.removeTeamCurrency(team, currency, amount)
                    : manager.addTeamCurrency(team, currency, amount));
        }
        if (notify) {
            player.sendSystemMessage(Component.translatable(
                    success
                            ? (remove ? "ftbquests.reward.iska_lib.iska_currency.removed"
                                    : "ftbquests.reward.iska_lib.iska_currency.added")
                            : "ftbquests.reward.iska_lib.iska_currency.failed",
                    IskaQuestsHelper.formatAmount(amount),
                    IskaQuestsHelper.currencyDisplayName(currency)), true);
        }
    }

    @Override
    public MutableComponent getAltTitle() {
        return IskaQuestsHelper.currencyPlayerTitle(currency, amount);
    }
}
