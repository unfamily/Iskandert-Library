package net.unfamily.iskalib.liquid;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * {@link FluidType} that consults {@link LiquidBehaviorRegistry} overlays at runtime for
 * infinity ({@code canConvertToSource}) and loggable-driven hydrate / boating behaviour.
 */
public final class OverlayAwareFluidType extends FluidType {
    private final ResourceLocation sourceFluidId;
    private final boolean registeredCanConvertToSource;
    private final boolean registeredCanHydrate;
    private final boolean registeredSupportsBoating;

    public OverlayAwareFluidType(
            Properties properties,
            ResourceLocation sourceFluidId,
            boolean registeredCanConvertToSource,
            boolean registeredCanHydrate,
            boolean registeredSupportsBoating
    ) {
        super(properties);
        this.sourceFluidId = sourceFluidId;
        this.registeredCanConvertToSource = registeredCanConvertToSource;
        this.registeredCanHydrate = registeredCanHydrate;
        this.registeredSupportsBoating = registeredSupportsBoating;
    }

    public ResourceLocation sourceFluidId() {
        return sourceFluidId;
    }

    @Override
    public boolean canConvertToSource(FluidState state, LevelReader reader, BlockPos pos) {
        return LiquidBehaviorRegistry.canConvertToSource(sourceFluidId, registeredCanConvertToSource);
    }

    @Override
    public boolean canConvertToSource(FluidStack stack) {
        return LiquidBehaviorRegistry.canConvertToSource(sourceFluidId, registeredCanConvertToSource);
    }

    @Override
    public boolean canHydrate(Entity entity) {
        return LiquidBehaviorRegistry.isLoggable(sourceFluidId, registeredCanHydrate);
    }

    @Override
    public boolean canHydrate(FluidState state, BlockGetter getter, BlockPos pos, BlockState source, BlockPos sourcePos) {
        return LiquidBehaviorRegistry.isLoggable(sourceFluidId, registeredCanHydrate);
    }

    @Override
    public boolean canHydrate(FluidStack stack) {
        return LiquidBehaviorRegistry.isLoggable(sourceFluidId, registeredCanHydrate);
    }

    @Override
    public boolean supportsBoating(Boat boat) {
        return LiquidBehaviorRegistry.isLoggable(sourceFluidId, registeredSupportsBoating);
    }

    @Override
    public boolean supportsBoating(FluidState state, Boat boat) {
        return LiquidBehaviorRegistry.isLoggable(sourceFluidId, registeredSupportsBoating);
    }
}
