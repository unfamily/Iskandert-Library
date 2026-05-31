package net.unfamily.iskalib;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
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
        NeoForge.EVENT_BUS.register(ExplosionSystem.class);
        if (isPhysicalClient()) {
            VanillaWorldMarkerClientHooks.registerIfNeeded(NeoForge.EVENT_BUS);
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

