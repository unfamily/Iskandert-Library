package net.unfamily.iskalib.client.gas;

import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSources;
import net.unfamily.iskalib.IskaLib;
import net.unfamily.iskalib.gas.GasRegistry;
import net.unfamily.iskalib.gas.RegisteredGas;

/**
 * Fluid stack rendering (JEI, buckets, GUIs) — world gas uses {@link IskaLibGasBlockModels}.
 */
public final class IskaLibGasFluidModels {

    private static final Material GAS_STILL = new Material(Identifier.fromNamespaceAndPath(IskaLib.MOD_ID, "block/gas"));
    private static final Material GAS_FLOW = new Material(Identifier.fromNamespaceAndPath(IskaLib.MOD_ID, "block/gas"));

    private IskaLibGasFluidModels() {}

    public static void registerFluidModels(RegisterFluidModelsEvent event) {
        for (RegisteredGas gas : GasRegistry.all()) {
            int tint = gas.tintArgb();
            FluidModel.Unbaked model = new FluidModel.Unbaked(GAS_STILL, GAS_FLOW, null, FluidTintSources.constant(tint));
            event.register(model, gas::sourceFluid, gas::flowingFluid);
        }
    }
}
