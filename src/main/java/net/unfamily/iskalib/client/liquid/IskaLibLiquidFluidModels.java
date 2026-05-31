package net.unfamily.iskalib.client.liquid;

import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSources;
import net.unfamily.iskalib.liquid.IskaLibLiquids;
import net.unfamily.iskalib.liquid.RegisteredLiquid;

public final class IskaLibLiquidFluidModels {

    private IskaLibLiquidFluidModels() {}

    public static void registerFluidModels(RegisterFluidModelsEvent event) {
        for (RegisteredLiquid liquid : IskaLibLiquids.allRegisteredLiquids()) {
            int tint = liquid.spec().tintArgb();
            Material still = new Material(liquid.spec().stillTexture());
            Material flowing = new Material(liquid.spec().flowingTexture());
            FluidModel.Unbaked model = new FluidModel.Unbaked(
                    still, flowing, null, FluidTintSources.constant(tint), null);
            event.register(model, liquid::sourceFluid, liquid::flowingFluid);
        }
    }
}
