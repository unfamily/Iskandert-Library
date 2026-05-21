package net.unfamily.iskalib.gas;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Consumer mod {@link DeferredRegister} instances for gas registration.
 * Required so each namespace uses a single registrar per registry (NeoForge 26).
 */
public record GasRegistrationRegisters(
        DeferredRegister<FluidType> fluidTypes,
        DeferredRegister<Fluid> fluids,
        DeferredRegister.Blocks blocks,
        DeferredRegister.Items items
) {}
