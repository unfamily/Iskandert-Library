package net.unfamily.iskalib.stage;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.unfamily.iskalib.IskaLib;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ensures stage datapack JSON (actions, known_stages + AS item/recipe/block/gui via {@link StageReloadHooks})
 * loads at world start and on {@code /reload}, not only on manual stage reload commands.
 */
@EventBusSubscriber(modid = IskaLib.MOD_ID)
public final class StageJsonReloadHooks {
    private static final Logger LOGGER = LoggerFactory.getLogger(StageJsonReloadHooks.class);

    private StageJsonReloadHooks() {}

    @SubscribeEvent
    public static void onAddServerReloadListeners(AddServerReloadListenersEvent event) {
        Identifier id = Identifier.fromNamespaceAndPath(IskaLib.MOD_ID, "stage_json");
        event.addListener(id, new SimplePreparableReloadListener<Object>() {
            @Override
            protected Object prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Object prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
                triggerStageReload("datapack reload", resourceManager);
            }
        });
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        triggerStageReload("server started", event.getServer().getResourceManager());
    }

    private static void triggerStageReload(String reason, @org.jetbrains.annotations.Nullable ResourceManager resourceManager) {
        StageReloadHooks.Listener listener = StageReloadHooks.getListener();
        if (listener == null) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        try {
            StageReloadHooks.runWithResourceManager(resourceManager, () -> {
                listener.reloadStageBlock(server.createCommandSourceStack());
            });
            LOGGER.debug("Stage JSON reload triggered ({})", reason);
        } catch (Exception e) {
            LOGGER.error("Stage JSON reload failed ({}): {}", reason, e.getMessage());
        }
    }
}
