package net.unfamily.iskalib.client.liquid;

import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSources;
import net.unfamily.iskalib.client.OncePerEvent;
import net.unfamily.iskalib.liquid.IskaLibLiquids;
import net.unfamily.iskalib.liquid.RegisteredLiquid;

public final class IskaLibLiquidFluidModels {

    private IskaLibLiquidFluidModels() {}

    public static void registerFluidModels(RegisterFluidModelsEvent event) {
        // RegisterFluidModelsEvent is posted to every mod bus with the same instance.
        if (!OncePerEvent.claim(IskaLibLiquidFluidModels.class, event)) {
            return;
        }
        for (RegisteredLiquid liquid : IskaLibLiquids.allRegisteredLiquids()) {
            int tint = liquid.spec().tintArgb();
            Material still = new Material(liquid.spec().stillTexture());
            Material flowing = new Material(liquid.spec().flowingTexture());
            Identifier overlayId = liquid.spec().clientProperties().overlayTexture();
            Material overlay = overlayId != null ? new Material(overlayId) : null;
            FluidModel.Unbaked model = new FluidModel.Unbaked(
                    still, flowing, overlay, FluidTintSources.constant(tint), null);
            event.register(model, liquid::sourceFluid, liquid::flowingFluid);
        }
    }
}
