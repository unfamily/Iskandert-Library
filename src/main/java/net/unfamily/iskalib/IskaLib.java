package net.unfamily.iskalib;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
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
import net.unfamily.iskalib.gas.GasJsonLoader;
import net.unfamily.iskalib.liquid.LiquidJsonLoader;
import net.unfamily.iskalib.IskaLibCreativeTabs;
import net.unfamily.iskalib.shop.ShopCurrencyCatalog;
import net.unfamily.iskalib.stage.StageBootstrap;
import net.unfamily.iskalib.tool.ToolBehaviorLoader;

//change_hash
@Mod(IskaLib.MOD_ID)
public class IskaLib {
    public static final String MOD_ID = "iska_lib";
    public static final Logger LOGGER = LogUtils.getLogger();

    public IskaLib(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, IskaLibConfig.SPEC);
        if (ModList.get().isLoaded("ftbquests")) {
            try {
                Class.forName("net.unfamily.iskalib.integration.ftbquests.FtbQuestsIntegration")
                        .getMethod("init").invoke(null);
            } catch (Throwable t) {
                LOGGER.error("Failed to initialize FTB Quests integration", t);
            }
        }
        if (ModList.get().isLoaded("ftbultimine")) {
            try {
                Class.forName("net.unfamily.iskalib.integration.ftbultimine.FtbUltimineIntegration")
                        .getMethod("init")
                        .invoke(null);
            } catch (Throwable error) {
                LOGGER.error("Failed to initialize FTB Ultimine integration", error);
            }
        }
        IskaLibGases.initLibrary(modEventBus);
        IskaLibLiquids.initLibrary(modEventBus);
        LiquidJsonLoader.bootstrapAndRegister(true);
        GasJsonLoader.bootstrapAndRegister(true);
        ToolBehaviorLoader.loadAllBootstrap();
        IskaLibCreativeTabs.registerIfNeeded(modEventBus);
        StageBootstrap.install();
        modEventBus.addListener(IskaLibGases::registerCapabilities);
        NeoForge.EVENT_BUS.register(ExplosionSystem.class);
        ShopCurrencyCatalog.bootstrapFromJar();
        ShopCurrencyCatalog.installAsDefaultListener();
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
