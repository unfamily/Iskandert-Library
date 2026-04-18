package net.unfamily.iskalib.client.marker;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Subscribes to the level render pipeline for world markers.
 * <p>
 * Registration is <strong>not</strong> done via {@code @EventBusSubscriber(modid = "iska_lib")} alone: when the
 * library is bundled with another mod (e.g. only {@code iska_utils} is listed in the mod list), that annotation
 * may never run. Call {@link #registerIfNeeded(IEventBus)} once from the embedding mod's client setup
 * (and/or from {@link net.unfamily.iskalib.IskaLib} when the library loads as its own mod).
 */
public final class VanillaWorldMarkerClientHooks {
    private static volatile boolean registered;

    private VanillaWorldMarkerClientHooks() {}

    /**
     * Registers this class on the NeoForge game bus (client). Safe to call from both {@code iska_lib} and an embedder.
     */
    public static void registerIfNeeded(IEventBus gameBus) {
        if (registered) {
            return;
        }
        synchronized (VanillaWorldMarkerClientHooks.class) {
            if (registered) {
                return;
            }
            gameBus.register(VanillaWorldMarkerClientHooks.class);
            registered = true;
        }
    }

    @SubscribeEvent
    public static void onRenderLevelAfterTranslucent(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        MarkRenderer.getInstance().renderWorldMarkers(partialTick);
    }
}
