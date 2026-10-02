package net.unfamily.iskalib.client;

import net.neoforged.bus.api.IEventBus;
import net.unfamily.iskalib.client.gas.IskaLibGasAssetPack;
import net.unfamily.iskalib.client.gas.IskaLibGasBlockModels;
import net.unfamily.iskalib.client.gas.IskaLibGasFluidModels;
import net.unfamily.iskalib.client.liquid.IskaLibLiquidFluidModels;

/**
 * Shared client hooks for consumer mods that register gases/liquids via iska_lib.
 * <p>
 * RegisterFluidModelsEvent / BlockTintSources are posted to every mod bus with the
 * <strong>same</strong> event instance, so listeners must be attached only once
 * (otherwise fluid models register twice and throw).
 */
public final class IskaLibConsumerClientHooks {

    private static final java.util.concurrent.atomic.AtomicBoolean HOOKED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private IskaLibConsumerClientHooks() {}

    public static void hookConsumerModClientOnce(IEventBus modEventBus) {
        if (!isPhysicalClient() || modEventBus == null || !HOOKED.compareAndSet(false, true)) {
            return;
        }
        modEventBus.addListener(IskaLibGasFluidModels::registerFluidModels);
        modEventBus.addListener(IskaLibGasBlockModels::registerBlockTintSources);
        modEventBus.addListener(IskaLibGasAssetPack::onAddPackFinders);
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
