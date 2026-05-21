package net.unfamily.iskalib.gas;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime lookup for all gases registered through {@link IskaLibGases}.
 */
public final class GasRegistry {
    private static final Map<Block, RegisteredGas> BY_BLOCK = new ConcurrentHashMap<>();
    private static final Map<Identifier, RegisteredGas> BY_FLUID_ID = new ConcurrentHashMap<>();
    private static final List<RegisteredGas> ALL = Collections.synchronizedList(new ArrayList<>());

    private GasRegistry() {}

    static void register(RegisteredGas gas) {
        BY_BLOCK.put(gas.block(), gas);
        BY_FLUID_ID.put(gas.sourceFluidId(), gas);
        BY_FLUID_ID.put(Identifier.fromNamespaceAndPath(gas.modId(), gas.spec().fluidFlowingId()), gas);
        ALL.add(gas);
    }

    @Nullable
    public static RegisteredGas fromBlock(Block block) {
        return BY_BLOCK.get(block);
    }

    @Nullable
    public static RegisteredGas fromState(BlockState state) {
        return fromBlock(state.getBlock());
    }

    @Nullable
    public static RegisteredGas fromFluid(Fluid fluid) {
        Identifier id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid);
        return id != null ? BY_FLUID_ID.get(id) : null;
    }

    public static List<RegisteredGas> all() {
        return List.copyOf(ALL);
    }

    static void clearForTests() {
        BY_BLOCK.clear();
        BY_FLUID_ID.clear();
        ALL.clear();
    }
}
