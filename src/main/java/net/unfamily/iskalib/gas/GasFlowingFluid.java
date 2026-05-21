package net.unfamily.iskalib.gas;

import net.minecraft.world.level.LevelReader;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/**
 * Gas fluids for stacks/ports/world block. Does not flood — {@link GasLiquidBlock} handles upward movement.
 */
public class GasFlowingFluid {

    private GasFlowingFluid() {}

    public static final class Source extends BaseFlowingFluid.Source {
        public Source(Properties properties) {
            super(properties);
        }

        @Override
        protected int getSlopeFindDistance(LevelReader level) {
            return 0;
        }

        @Override
        protected int getDropOff(LevelReader level) {
            return Integer.MAX_VALUE / 2;
        }
    }

    public static final class Flowing extends BaseFlowingFluid.Flowing {
        public Flowing(Properties properties) {
            super(properties);
        }

        @Override
        protected int getSlopeFindDistance(LevelReader level) {
            return 0;
        }

        @Override
        protected int getDropOff(LevelReader level) {
            return Integer.MAX_VALUE / 2;
        }
    }
}
