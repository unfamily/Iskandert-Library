package net.unfamily.iskalib.gas;

import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/**
 * Logical fluid for gas stacks and ports. Does not spread in the world (no {@link net.minecraft.world.level.block.LiquidBlock}).
 */
public class GasFlowingFluid {

    private GasFlowingFluid() {}

    public static final class Source extends BaseFlowingFluid.Source {
        public Source(Properties properties) {
            super(properties);
        }
    }

    public static final class Flowing extends BaseFlowingFluid.Flowing {
        public Flowing(Properties properties) {
            super(properties);
        }
    }
}
