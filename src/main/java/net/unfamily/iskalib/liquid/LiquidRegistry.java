package net.unfamily.iskalib.liquid;

import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class LiquidRegistry {
    private static final Map<Fluid, RegisteredLiquid> BY_FLUID = new ConcurrentHashMap<>();
    private static final List<RegisteredLiquid> ALL = new ArrayList<>();

    private LiquidRegistry() {}

    static void register(RegisteredLiquid liquid) {
        ALL.add(liquid);
        BY_FLUID.put(liquid.sourceFluid(), liquid);
        BY_FLUID.put(liquid.flowingFluid(), liquid);
    }

    static RegisteredLiquid fromFluid(Fluid fluid) {
        return BY_FLUID.get(fluid);
    }

    static List<RegisteredLiquid> all() {
        return List.copyOf(ALL);
    }
}
