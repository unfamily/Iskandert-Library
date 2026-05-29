package net.unfamily.iskalib.command;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.unfamily.iskalib.IskaLib;

/**
 * Auto-registers library commands when the library is present as a standalone mod.
 */
@EventBusSubscriber(modid = IskaLib.MOD_ID)
public final class IskaLibCommandBootstrap {
    private IskaLibCommandBootstrap() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        MarkerCommand.register(dispatcher);
        StageCommand.register(dispatcher);
        IskaLibDebugCommand.register(dispatcher);
        ExplosionCommand.register(dispatcher);
        ShopTeamCommand.register(dispatcher);
    }
}

