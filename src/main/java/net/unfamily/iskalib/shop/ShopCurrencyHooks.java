package net.unfamily.iskalib.shop;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Integration point for currency metadata and suggestions.
 *
 * <p>The library team system stores balances by currency id, but does not own the currency catalog.
 * Consumers can provide currency names/symbols for display and suggestion.
 */
public final class ShopCurrencyHooks {
    private ShopCurrencyHooks() {}

    public record CurrencyInfo(String translationKey, String symbol) {}

    public interface Listener {
        /**
         * @return known currency ids for suggestions (may be empty).
         */
        List<String> listCurrencyIds();

        /**
         * @return currency metadata for display if available.
         */
        Optional<CurrencyInfo> getCurrencyInfo(String currencyId);
    }

    private static volatile Listener listener = new Listener() {
        @Override
        public List<String> listCurrencyIds() {
            return Collections.emptyList();
        }

        @Override
        public Optional<CurrencyInfo> getCurrencyInfo(String currencyId) {
            return Optional.empty();
        }
    };

    public static Listener getListener() {
        return listener;
    }

    public static void setListener(Listener newListener) {
        listener = newListener != null ? newListener : listener;
    }
}

