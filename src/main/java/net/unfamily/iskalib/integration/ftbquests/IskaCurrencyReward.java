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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.unfamily.iskalib.team.ShopTeamManager;

public final class IskaCurrencyReward extends Reward {
    static RewardType TYPE;

    private String currency = "null_coin";
    private double amount = 1.0;
    private boolean remove;
    /** B5: Currency rewards always target the team; defaults to true. */
    private boolean teamReward = true;

    public IskaCurrencyReward(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public RewardType getType() {
        return TYPE;
    }

    @Override
    public void writeData(Json5Object json, HolderLookup.Provider provider) {
        super.writeData(json, provider);
        json.addProperty("currency", currency);
        json.addProperty("amount", amount);
        if (remove) json.addProperty("remove", true);
        json.addProperty("team_reward", teamReward);
    }

    @Override
    public void readData(Json5Object json, HolderLookup.Provider provider) {
        super.readData(json, provider);
        currency = Json5Util.getString(json, "currency").orElse("null_coin");
        amount = Math.max(0.0, Json5Util.getDouble(json, "amount").orElse(1.0));
        remove = Json5Util.getBoolean(json, "remove").orElse(false);
        teamReward = Json5Util.getBoolean(json, "team_reward").orElse(true);
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(currency, Short.MAX_VALUE);
        buffer.writeDouble(amount);
        buffer.writeBoolean(remove);
        buffer.writeBoolean(teamReward);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        currency = buffer.readUtf(Short.MAX_VALUE);
        amount = Math.max(0.0, buffer.readDouble());
        remove = buffer.readBoolean();
        teamReward = buffer.readBoolean();
    }

    @Override
    public void fillConfigGroup(EditableConfigGroup config) {
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
        // B5: team_reward toggle — currency rewards always operate on the team; default true
        config.addBool("team_reward", teamReward, value -> {
            teamReward = value;
            clearCachedData();
        }, true).setNameKey("ftbquests.reward.iska_lib.iska_currency.team_reward");
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
            ShopTeamManager manager = ShopTeamManager.getInstance((ServerLevel) player.level());
            String teamKey = manager.getPlayerTeamKey(player);
            success = teamKey != null && (remove
                    ? manager.removeTeamCurrency(teamKey, currency, amount)
                    : manager.addTeamCurrency(teamKey, currency, amount));
        }
        // B2: actionbar feedback removed — silent claim.
    }

    @Override
    public MutableComponent getAltTitle() {
        // Team currency rewards keep the default (non-blue) title.
        return IskaQuestsHelper.currencyPlayerTitle(currency, amount);
    }

    @Override
    public String getButtonText() {
        return IskaQuestsHelper.abbreviateAmount(amount);
    }
}
