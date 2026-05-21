package net.unfamily.iskalib.client.gas;

import net.minecraft.client.color.block.BlockTintSources;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.unfamily.iskalib.gas.GasRegistry;
import net.unfamily.iskalib.gas.RegisteredGas;

import java.util.List;

/**
 * Per-gas block tint for the shared {@code iska_lib:block/gas} model (consumer blockstates should reference that model).
 */
public final class IskaLibGasBlockModels {

    private IskaLibGasBlockModels() {}

    public static void registerBlockTintSources(RegisterColorHandlersEvent.BlockTintSources event) {
        for (RegisteredGas gas : GasRegistry.all()) {
            event.getBlockColors().register(List.of(BlockTintSources.constant(gas.tintArgb())), gas.block());
        }
    }
}
