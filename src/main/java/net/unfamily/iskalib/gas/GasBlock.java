package net.unfamily.iskalib.gas;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Transparent gas column block: rises upward, does not spread, becomes collectible at build height limit.
 * When {@link #COLLECTABLE}, exposes a fluid handler for extraction only; the block is removed once drained.
 */
public class GasBlock extends Block {
    public static final BooleanProperty COLLECTABLE = BooleanProperty.create("collectable");
    /** Server tick delay between rise steps (one block upward per step). */
    public static final int DEFAULT_RISE_TICK_INTERVAL = GasSpec.DEFAULT_TICK_INTERVAL;

    private static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

    private final Supplier<RegisteredGas> gas;
    private final int tickInterval;

    public GasBlock(Properties properties, Supplier<RegisteredGas> gas, int tickInterval) {
        super(properties);
        this.gas = gas;
        this.tickInterval = Math.max(1, tickInterval);
        registerDefaultState(stateDefinition.any().setValue(COLLECTABLE, false));
    }

    public static boolean isCollectable(BlockState state) {
        return state.getBlock() instanceof GasBlock && state.getValue(COLLECTABLE);
    }

    /**
     * True when {@code pos} holds a collectible gas block of {@code expected} (if non-null).
     */
    public static boolean isCollectableGasAt(Level level, BlockPos pos, @Nullable RegisteredGas expected) {
        BlockState state = level.getBlockState(pos);
        if (!isCollectable(state)) {
            return false;
        }
        if (expected == null) {
            return true;
        }
        RegisteredGas at = GasRegistry.fromState(state);
        return at == expected;
    }

    /**
     * Removes the gas block at {@code pos} only if it is still a registered gas block (avoids wiping unrelated blocks).
     */
    public static void removeGasBlockIfPresent(Level level, BlockPos pos) {
        if (GasRegistry.fromState(level.getBlockState(pos)) != null) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    @Nullable
    public RegisteredGas registeredGas() {
        return gas.get();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLLECTABLE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.getValue(COLLECTABLE)) {
            schedule(level, pos);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, @Nullable net.minecraft.world.level.redstone.Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, orientation, movedByPiston);
        if (!state.getValue(COLLECTABLE) && level.getBlockState(pos).getBlock() == this) {
            schedule(level, pos);
        }
    }

    private void schedule(Level level, BlockPos pos) {
        if (!level.isClientSide()) {
            level.scheduleTick(pos, this, tickInterval);
        }
    }

    private static int gasCeilingY(Level level) {
        return level.getMaxY();
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getBlock() != this) {
            return;
        }
        if (state.getValue(COLLECTABLE)) {
            return;
        }

        BlockState live = level.getBlockState(pos);
        if (live.getBlock() != this || live.getValue(COLLECTABLE)) {
            return;
        }

        int ceiling = gasCeilingY(level);
        if (pos.getY() >= ceiling) {
            if (!live.getValue(COLLECTABLE)) {
                level.setBlock(pos, live.setValue(COLLECTABLE, true), Block.UPDATE_ALL);
            }
            return;
        }

        BlockPos above = pos.above();
        BlockState aboveState = level.getBlockState(above);
        if (aboveState.getBlock() instanceof GasBlock) {
            schedule(level, pos);
            return;
        }
        if (!aboveState.canBeReplaced()) {
            schedule(level, pos);
            return;
        }

        BlockState rising = live.setValue(COLLECTABLE, false);
        if (!level.setBlock(above, rising, Block.UPDATE_ALL)) {
            schedule(level, pos);
            return;
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        schedule(level, above);
    }

    public static Block.Properties defaultProperties(int lightLevel) {
        return Properties.of()
                .mapColor(MapColor.NONE)
                .noOcclusion()
                .noLootTable()
                .replaceable()
                .pushReaction(PushReaction.DESTROY)
                .strength(0.0F)
                .lightLevel(s -> lightLevel);
    }
}
