package net.unfamily.iskalib.client;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.unfamily.iskalib.IskaLib;
import net.unfamily.iskalib.gas.GasRegistry;
import net.unfamily.iskalib.gas.RegisteredGas;
import net.unfamily.iskalib.liquid.IskaLibLiquids;
import net.unfamily.iskalib.liquid.RegisteredLiquid;

/**
 * Client tint and textures for library-registered gases and liquids (NeoForge 1.21.1).
 */
public final class IskaLibFluidClient {
    private static final ResourceLocation GAS_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(IskaLib.MOD_ID, "block/gas");

    private IskaLibFluidClient() {}

    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        for (RegisteredGas gas : GasRegistry.all()) {
            if (!gas.fluidTypeHolder().isBound()) {
                continue;
            }
            int tint = gas.tintArgb();
            event.registerFluidType(new IClientFluidTypeExtensions() {
                @Override
                public ResourceLocation getStillTexture() {
                    return GAS_TEXTURE;
                }

                @Override
                public ResourceLocation getFlowingTexture() {
                    return GAS_TEXTURE;
                }

                @Override
                public int getTintColor() {
                    return tint;
                }

                @Override
                public int getTintColor(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
                    return tint;
                }
            }, gas.fluidTypeHolder().get());
        }

        for (RegisteredLiquid liquid : IskaLibLiquids.allRegisteredLiquids()) {
            if (!liquid.fluidTypeHolder().isBound()) {
                continue;
            }
            ResourceLocation still = liquid.spec().stillTexture();
            ResourceLocation flow = liquid.spec().flowingTexture();
            int tint = liquid.tintArgb();
            event.registerFluidType(new IClientFluidTypeExtensions() {
                @Override
                public ResourceLocation getStillTexture() {
                    return still;
                }

                @Override
                public ResourceLocation getFlowingTexture() {
                    return flow;
                }

                @Override
                public int getTintColor() {
                    return tint;
                }

                @Override
                public int getTintColor(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
                    return tint;
                }
            }, liquid.fluidTypeHolder().get());
        }
    }

    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        for (RegisteredGas gas : GasRegistry.all()) {
            if (!gas.blockHolder().isBound()) {
                continue;
            }
            int tint = gas.tintArgb();
            event.register((state, level, pos, index) -> tint, gas.block());
        }
    }
}
