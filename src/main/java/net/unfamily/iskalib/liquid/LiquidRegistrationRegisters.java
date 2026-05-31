package net.unfamily.iskalib.liquid;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Consumer mod {@link DeferredRegister} instances for liquid registration.
 */
public record LiquidRegistrationRegisters(
        DeferredRegister<FluidType> fluidTypes,
        DeferredRegister<Fluid> fluids,
        DeferredRegister.Blocks blocks,
        DeferredRegister.Items items
) {}
