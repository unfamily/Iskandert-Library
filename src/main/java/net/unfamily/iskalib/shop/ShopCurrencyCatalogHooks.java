package net.unfamily.iskalib.shop;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.unfamily.iskalib.IskaLib;

/**
 * Reloads {@link ShopCurrencyCatalog} with datapacks and keeps the default hook wired.
 */
@EventBusSubscriber(modid = IskaLib.MOD_ID)
public final class ShopCurrencyCatalogHooks {
    private ShopCurrencyCatalogHooks() {}

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new SimplePreparableReloadListener<Object>() {
            @Override
            protected Object prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Object prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
                ShopCurrencyCatalog.reload(resourceManager);
            }
        });
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ShopCurrencyCatalog.reload(event.getServer().getResourceManager());
    }
}
