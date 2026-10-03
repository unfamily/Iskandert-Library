package net.unfamily.iskalib.client.gas;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.fluid.CustomFluidRenderer;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.fluids.FluidStack;
import net.unfamily.iskalib.IskaLib;
import net.unfamily.iskalib.client.OncePerEvent;
import net.unfamily.iskalib.gas.GasRegistry;
import net.unfamily.iskalib.gas.RegisteredGas;

/**
 * Fluid stack rendering (JEI, buckets, GUIs) — world gas uses {@link IskaLibGasBlockModels} only.
 * Vanilla fluid mesh must not draw in-world on top of the gas block model.
 */
public final class IskaLibGasFluidModels {

    private static final Material GAS_STILL = new Material(Identifier.fromNamespaceAndPath(IskaLib.MOD_ID, "block/gas"));
    private static final Material GAS_FLOW = new Material(Identifier.fromNamespaceAndPath(IskaLib.MOD_ID, "block/gas"));

    /** Skip chunk fluid tessellation entirely (block model draws the gas). */
    private static final CustomFluidRenderer SKIP_WORLD_FLUID_MESH =
            (fluidRenderer, fluidState, getter, pos, output, blockState) -> true;

    private IskaLibGasFluidModels() {}

    public static void registerFluidModels(RegisterFluidModelsEvent event) {
        // Same event instance is fanned out to every mod bus; register at most once.
        if (!OncePerEvent.claim(IskaLibGasFluidModels.class, event)) {
            return;
        }
        for (RegisteredGas gas : GasRegistry.all()) {
            int stackTint = gas.tintArgb();
            FluidTintSource tint = new FluidTintSource() {
                @Override
                public int color(FluidState state) {
                    return stackTint;
                }

                @Override
                public int colorAsStack(FluidStack stack) {
                    return stackTint;
                }

                @Override
                public int colorInWorld(
                        FluidState fluidState, BlockState blockState, BlockAndTintGetter level, BlockPos pos) {
                    return 0x00000000;
                }
            };
            FluidModel.Unbaked model = new FluidModel.Unbaked(
                    GAS_STILL, GAS_FLOW, null, tint, SKIP_WORLD_FLUID_MESH);
            event.register(model, gas::sourceFluid, gas::flowingFluid);
        }
    }
}
