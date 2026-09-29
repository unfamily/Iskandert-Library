package net.unfamily.iskalib.liquid;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.unfamily.iskalib.IskaLib;

/**
 * Reloads liquid JSON behavior overlays from datapacks.
 */
@EventBusSubscriber(modid = IskaLib.MOD_ID)
public final class LiquidJsonReloadHooks {
    private LiquidJsonReloadHooks() {}

    @SubscribeEvent
    public static void onAddServerReloadListeners(AddServerReloadListenersEvent event) {
        Identifier id = Identifier.fromNamespaceAndPath(IskaLib.MOD_ID, "liquid_json");
        event.addListener(id, new SimplePreparableReloadListener<Object>() {
            @Override
            protected Object prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Object prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
                LiquidJsonLoader.reload(resourceManager);
            }
        });
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        LiquidJsonLoader.reload(event.getServer().getResourceManager());
    }
}
