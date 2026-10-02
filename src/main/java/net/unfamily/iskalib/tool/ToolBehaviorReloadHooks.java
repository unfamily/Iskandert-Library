package net.unfamily.iskalib.tool;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.unfamily.iskalib.IskaLib;

@EventBusSubscriber(modid = IskaLib.MOD_ID)
public final class ToolBehaviorReloadHooks {
    private ToolBehaviorReloadHooks() {}

    @SubscribeEvent
    public static void onAddServerReloadListeners(AddServerReloadListenersEvent event) {
        Identifier id = Identifier.fromNamespaceAndPath(IskaLib.MOD_ID, "tool_behaviors");
        event.addListener(id, new SimplePreparableReloadListener<Object>() {
            @Override
            protected Object prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Object prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
                ToolBehaviorLoader.loadAll(resourceManager);
            }
        });
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ToolBehaviorLoader.loadAll(event.getServer().getResourceManager());
    }
}
