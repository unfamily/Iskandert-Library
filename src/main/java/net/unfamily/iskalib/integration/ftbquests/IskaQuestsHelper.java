package net.unfamily.iskalib.integration.ftbquests;

import dev.ftb.mods.ftblibrary.client.config.EditableConfigGroup;
import dev.ftb.mods.ftblibrary.util.NameMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.unfamily.iskalib.shop.ShopCurrencyHooks;

import java.util.List;
import java.util.Locale;

/**
 * Shared FTB Quests UI / validation helpers for Iska stage and currency types.
 */
final class IskaQuestsHelper {
    static final String[] SCOPES = {"player", "world", "team"};
    static final String[] STAGE_MODES = {"add", "remove"};

    private IskaQuestsHelper() {}

    static boolean isKnownCurrency(String currencyId) {
        return currencyId != null
                && !currencyId.isBlank()
                && ShopCurrencyHooks.getListener().getCurrencyInfo(currencyId).isPresent();
    }

    static boolean isValidCurrencyGoal(String currencyId, double amount) {
        return amount > 0.0 && isKnownCurrency(currencyId);
    }

    static boolean isValidStageId(String stage) {
        return stage != null && !stage.isBlank();
    }

    static String normalizeScope(String value) {
        return "world".equals(value) || "team".equals(value) ? value : "player";
    }

    static String normalizeStageMode(String value) {
        return "remove".equals(value) ? "remove" : "add";
    }

    static boolean isRemoveStageMode(String mode) {
        return "remove".equals(normalizeStageMode(mode));
    }

    static Component currencyDisplayName(String currencyId) {
        return ShopCurrencyHooks.getListener().getCurrencyInfo(currencyId)
                .map(info -> (Component) Component.translatable(info.translationKey()))
                .orElseGet(() -> Component.literal(currencyId == null || currencyId.isBlank() ? "?" : currencyId));
    }

    static String formatAmount(double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            return "0.0";
        }
        if (amount == Math.rint(amount) && Math.abs(amount) < 1_000_000_000_000d) {
            return String.format(Locale.ROOT, "%.1f", amount);
        }
        return Double.toString(amount);
    }

    /** Player-facing title, e.g. {@code Null Coin: 1.0}. */
    static MutableComponent currencyPlayerTitle(String currencyId, double amount) {
        return currencyDisplayName(currencyId).copy()
                .append(": ")
                .append(Component.literal(formatAmount(amount)).withStyle(ChatFormatting.YELLOW));
    }

    /** Player-facing title, e.g. {@code Stage: my_stage} or {@code Remove Stage: my_stage}. */
    static MutableComponent stagePlayerTitle(String stage) {
        return stagePlayerTitle(stage, false);
    }

    static MutableComponent stagePlayerTitle(String stage, boolean remove) {
        String id = stage == null || stage.isBlank() ? "?" : stage;
        return Component.translatable(remove ? "ftbquests.iska_lib.stage_label_remove" : "ftbquests.iska_lib.stage_label")
                .append(": ")
                .append(Component.literal(id).withStyle(remove ? ChatFormatting.RED : ChatFormatting.YELLOW));
    }

    static void addCurrencySelector(EditableConfigGroup config, String current, java.util.function.Consumer<String> setter, String nameKey) {
        List<String> ids = ShopCurrencyHooks.getListener().listCurrencyIds();
        if (!ids.isEmpty()) {
            String selected = ids.contains(current) ? current : ids.getFirst();
            config.addEnum(
                    "currency",
                    selected,
                    setter,
                    NameMap.of(selected, ids.toArray(String[]::new))
                            .name(IskaQuestsHelper::currencyDisplayName)
                            .create()
            ).setNameKey(nameKey);
        } else {
            config.addString("currency", current == null ? "" : current, setter, "null_coin")
                    .setNameKey(nameKey);
        }
    }

    static void addScopeSelector(EditableConfigGroup config, String current, java.util.function.Consumer<String> setter, String nameKey) {
        String selected = normalizeScope(current);
        config.addEnum(
                "scope",
                selected,
                value -> setter.accept(normalizeScope(value)),
                NameMap.of(selected, SCOPES)
                        .name(scope -> Component.translatable("ftbquests.iska_lib.scope." + scope))
                        .create()
        ).setNameKey(nameKey);
    }

    static void addStageModeSelector(EditableConfigGroup config, String current, java.util.function.Consumer<String> setter, String nameKey) {
        String selected = normalizeStageMode(current);
        config.addEnum(
                "mode",
                selected,
                value -> setter.accept(normalizeStageMode(value)),
                NameMap.of(selected, STAGE_MODES)
                        .name(mode -> Component.translatable("ftbquests.iska_lib.stage_mode." + mode))
                        .create()
        ).setNameKey(nameKey);
    }
}
