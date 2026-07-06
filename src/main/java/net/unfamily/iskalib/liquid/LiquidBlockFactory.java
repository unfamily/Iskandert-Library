package net.unfamily.iskalib.liquid;

import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * Optional custom liquid block factory (e.g. Colossal {@code BreeziumBlock}).
 */
@FunctionalInterface
public interface LiquidBlockFactory {
    LiquidBlock create(FlowingFluid fluid, BlockBehaviour.Properties properties);
}
