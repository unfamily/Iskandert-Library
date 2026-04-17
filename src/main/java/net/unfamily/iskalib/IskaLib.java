package net.unfamily.iskalib;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

@Mod(IskaLib.MOD_ID)
public class IskaLib {
    public static final String MOD_ID = "iska_lib";
    public static final Logger LOGGER = LogUtils.getLogger();

    public IskaLib(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, IskaLibConfig.SPEC);
    }
}

