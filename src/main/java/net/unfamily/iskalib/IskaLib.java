package net.unfamily.iskalib;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.unfamily.iskalib.client.marker.VanillaWorldMarkerClientHooks;
import net.unfamily.iskalib.explosion.ExplosionSystem;
import net.unfamily.iskalib.gas.IskaLibGases;
import net.unfamily.iskalib.liquid.IskaLibLiquids;

//change_hash
@Mod(IskaLib.MOD_ID)
public class IskaLib {
    public static final String MOD_ID = "iska_lib";
    public static final Logger LOGGER = LogUtils.getLogger();

    public IskaLib(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, IskaLibConfig.SPEC);
        IskaLibGases.initLibrary(modEventBus);
        IskaLibLiquids.initLibrary(modEventBus);
        modEventBus.addListener(IskaLibGases::registerCapabilities);
        NeoForge.EVENT_BUS.register(ExplosionSystem.class);
        if (isPhysicalClient()) {
            VanillaWorldMarkerClientHooks.registerIfNeeded(NeoForge.EVENT_BUS);
        }
    }

    @EventBusSubscriber(modid = MOD_ID)
    public static final class ServerHooks {
        private ServerHooks() {}

        @SubscribeEvent
        public static void onServerStarting(ServerStartingEvent event) {
            if (!net.neoforged.fml.ModList.get().isLoaded("ftbteams")) {
                return;
            }
            // Optional integration: do not load FtbTeamsEvents unless ftbteams is present (compileOnly API).
            try {
                Class<?> events = Class.forName("net.unfamily.iskalib.integration.ftbteams.FtbTeamsEvents");
                events.getMethod("init").invoke(null);
            } catch (Throwable t) {
                LOGGER.error("Failed to initialize FTB Teams integration", t);
            }
        }
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

