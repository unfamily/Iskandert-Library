package net.unfamily.iskalib.gas;

import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/**
 * Flow properties for non-spreading gas fluids.
 */
public record GasFlowingProperties(
        int tickRate,
        int slopeFindDistance,
        int levelDecreasePerBlock,
        float explosionResistance
) {
    public static final GasFlowingProperties STANDARD = new GasFlowingProperties(
            Integer.MAX_VALUE / 4, 0, Integer.MAX_VALUE / 4, 1.0F);

    void applyTo(BaseFlowingFluid.Properties props) {
        props.tickRate(tickRate)
                .slopeFindDistance(slopeFindDistance)
                .levelDecreasePerBlock(levelDecreasePerBlock)
                .explosionResistance(explosionResistance);
    }
}
