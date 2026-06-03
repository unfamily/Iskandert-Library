package net.unfamily.iskalib.client.marker;

import com.mojang.blaze3d.vertex.PoseStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;

/**
 * Subscribes to the level render pipeline for world markers.
 */
public final class VanillaWorldMarkerClientHooks {
    private static volatile boolean registered;

    private VanillaWorldMarkerClientHooks() {}

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
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        MarkRenderer.getInstance().render(poseStack, 0.0f);
    }
}
