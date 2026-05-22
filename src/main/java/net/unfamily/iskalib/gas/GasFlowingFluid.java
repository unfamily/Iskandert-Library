package net.unfamily.iskalib.gas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

import java.util.Collections;
import java.util.Map;

/**
 * Gas fluids for stacks/ports/world block. Does not flood — {@link GasLiquidBlock} handles upward movement.
 */
public final class GasFlowingFluid {

    private GasFlowingFluid() {}

    public static final class Source extends BaseFlowingFluid.Source {
        public Source(Properties properties) {
            super(properties);
        }

        @Override
        public void tick(ServerLevel level, BlockPos pos, BlockState blockState, FluidState fluidState) {
        }

        @Override
        protected void spread(ServerLevel level, BlockPos pos, BlockState state, FluidState fluidState) {
        }

        @Override
        protected void spreadTo(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState fluidState) {
        }

        @Override
        protected Map<Direction, FluidState> getSpread(ServerLevel level, BlockPos pos, BlockState state) {
            return Collections.emptyMap();
        }

        @Override
        protected int getSlopeFindDistance(LevelReader level) {
            return 0;
        }

        @Override
        protected int getDropOff(LevelReader level) {
            return Integer.MAX_VALUE / 2;
        }

        @Override
        protected boolean canConvertToSource(ServerLevel level) {
            return false;
        }

        @Override
        public boolean canConvertToSource(FluidState state, ServerLevel level, BlockPos pos) {
            return false;
        }
    }

    public static final class Flowing extends BaseFlowingFluid.Flowing {
        public Flowing(Properties properties) {
            super(properties);
        }

        @Override
        public void tick(ServerLevel level, BlockPos pos, BlockState blockState, FluidState fluidState) {
        }

        @Override
        protected void spread(ServerLevel level, BlockPos pos, BlockState state, FluidState fluidState) {
        }

        @Override
        protected void spreadTo(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState fluidState) {
        }

        @Override
        protected Map<Direction, FluidState> getSpread(ServerLevel level, BlockPos pos, BlockState state) {
            return Collections.emptyMap();
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
