package net.unfamily.iskalib.liquid;

import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/**
 * Mirrors NeoForge {@link BaseFlowingFluid.Properties} flow fields.
 */
public record FlowingFluidProperties(
        int tickRate,
        int slopeFindDistance,
        int levelDecreasePerBlock,
        float explosionResistance
) {
    public static final FlowingFluidProperties DEFAULT = new FlowingFluidProperties(5, 4, 1, 1.0F);

    /** Gas-style non-spreading fluid (see {@link net.unfamily.iskalib.gas.GasFlowingProperties}). */
    public static final FlowingFluidProperties NON_SPREADING = new FlowingFluidProperties(
            Integer.MAX_VALUE / 4, 0, Integer.MAX_VALUE / 4, 1.0F);

    void applyTo(BaseFlowingFluid.Properties props) {
        props.tickRate(tickRate)
                .slopeFindDistance(slopeFindDistance)
                .levelDecreasePerBlock(levelDecreasePerBlock)
                .explosionResistance(explosionResistance);
    }
}
