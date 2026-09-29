package net.unfamily.iskalib.liquid;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.unfamily.iskalib.IskaLib;

/**
 * Library-owned deferred registers for fluids declared under {@code data/iska_lib/iska_lib/liquids/}.
 */
public final class IskaLibOwnedLiquidRegisters {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IskaLib.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IskaLib.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(BuiltInRegistries.FLUID, IskaLib.MOD_ID);
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, IskaLib.MOD_ID);

    private static boolean registered;

    private IskaLibOwnedLiquidRegisters() {}

    public static LiquidRegistrationRegisters asLiquidRegisters() {
        return new LiquidRegistrationRegisters(FLUID_TYPES, FLUIDS, BLOCKS, ITEMS);
    }

    public static void register(IEventBus modEventBus) {
        if (registered) {
            return;
        }
        registered = true;
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
