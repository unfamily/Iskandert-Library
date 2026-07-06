package net.unfamily.iskalib.liquid;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class LiquidRegistry {
    private static final Map<Identifier, RegisteredLiquid> BY_FLUID_ID = new ConcurrentHashMap<>();
    private static final List<RegisteredLiquid> ALL = new ArrayList<>();

    private LiquidRegistry() {}

    static void register(RegisteredLiquid liquid) {
        ALL.add(liquid);
        BY_FLUID_ID.put(liquid.sourceFluidId(), liquid);
        BY_FLUID_ID.put(Identifier.fromNamespaceAndPath(
                liquid.spec().modId(), liquid.spec().fluidFlowingId()), liquid);
    }

    @Nullable
    static RegisteredLiquid fromFluid(Fluid fluid) {
        Identifier id = BuiltInRegistries.FLUID.getKey(fluid);
        return id != null ? BY_FLUID_ID.get(id) : null;
    }

    static List<RegisteredLiquid> all() {
        return List.copyOf(ALL);
    }
}
