package net.unfamily.iskalib.client;

import net.neoforged.bus.api.IEventBus;
import net.unfamily.iskalib.client.gas.IskaLibGasAssetPack;
import net.unfamily.iskalib.client.gas.IskaLibGasBlockModels;
import net.unfamily.iskalib.client.gas.IskaLibGasFluidModels;
import net.unfamily.iskalib.client.liquid.IskaLibLiquidFluidModels;

/**
 * Shared client hooks for gases/liquids registered via iska_lib.
 * <p>
 * Prefer attaching from {@link net.unfamily.iskalib.IskaLib} only. Consumer
 * {@code registerLiquid}/{@code registerGas} still call this for backwards
 * compatibility, but listeners are wired at most once.
 * <p>
 * Handlers also use {@link OncePerEvent} because {@code IModBusEvent}s such as
 * {@code RegisterFluidModelsEvent} are posted to every mod bus with the same instance.
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
            return net.neoforged.fml.loading.FMLEnvironment.getDist()
                    == net.neoforged.api.distmarker.Dist.CLIENT;
        } catch (Throwable ignored) {
            try {
                Class.forName("net.minecraft.client.Minecraft");
                return true;
            } catch (Throwable ignored2) {
                return false;
            }
        }
    }
}
