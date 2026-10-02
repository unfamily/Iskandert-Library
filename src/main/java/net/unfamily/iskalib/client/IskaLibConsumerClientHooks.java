package net.unfamily.iskalib.client;

import net.neoforged.bus.api.IEventBus;
import net.unfamily.iskalib.client.gas.IskaLibGasBlockModels;
import net.unfamily.iskalib.client.gas.IskaLibGasFluidModels;
import net.unfamily.iskalib.client.liquid.IskaLibLiquidFluidModels;

/**
 * Shared client hooks for consumer mods that register both gases and liquids via iska_lib.
 */
public final class IskaLibConsumerClientHooks {

    private static final java.util.Set<IEventBus> CONSUMER_HOOKED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private IskaLibConsumerClientHooks() {}

    public static void hookConsumerModClientOnce(IEventBus modEventBus) {
        if (!isPhysicalClient() || !CONSUMER_HOOKED.add(modEventBus)) {
            return;
        }
        modEventBus.addListener(IskaLibGasFluidModels::registerFluidModels);
        modEventBus.addListener(IskaLibGasBlockModels::registerBlockTintSources);
        modEventBus.addListener(IskaLibLiquidFluidModels::registerFluidModels);
    }

    private static boolean isPhysicalClient() {
        try {
            Class.forName("net.minecraft.client.Minecraft");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
