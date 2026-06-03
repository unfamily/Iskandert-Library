package net.unfamily.iskalib.gas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Rising gas fluid in the world: no flooding, moves upward on a schedule, extractable only at the column top.
 * Renders as a full-block model with no collision; interaction hitbox only at the column top.
 */
public class GasLiquidBlock extends LiquidBlock {

    public static final BooleanProperty COLLECTABLE = BooleanProperty.create("collectable");
    public static final int DEFAULT_RISE_TICK_INTERVAL = 10;

    private final Supplier<RegisteredGas> gas;
    private final int tickInterval;

    public GasLiquidBlock(FlowingFluid fluid, BlockBehaviour.Properties properties, Supplier<RegisteredGas> gas, int tickInterval) {
        super(fluid, properties);
        this.gas = gas;
        this.tickInterval = Math.max(1, tickInterval);
        registerDefaultState(stateDefinition.any().setValue(COLLECTABLE, false));
    }

    public static boolean isGasFluidBlock(BlockState state) {
        return state.getBlock() instanceof GasLiquidBlock;
    }

    public static boolean isCollectable(BlockState state) {
        return isGasFluidBlock(state) && state.getValue(COLLECTABLE);
    }

    public static boolean isTopOfColumn(Level level, BlockPos pos, @Nullable RegisteredGas expected) {
        RegisteredGas at = GasRegistry.fromState(level.getBlockState(pos));
        if (at == null || (expected != null && at != expected)) {
            return false;
        }
        BlockState above = level.getBlockState(pos.above());
        if (!isGasFluidBlock(above)) {
            return true;
        }
        RegisteredGas aboveGas = GasRegistry.fromState(above);
        return aboveGas == null || aboveGas != at;
    }

    public static boolean isExtractableAt(Level level, BlockPos pos, @Nullable RegisteredGas expected) {
        return isTopOfColumn(level, pos, expected);
    }

    public static void removeGasAt(Level level, BlockPos pos) {
        if (GasRegistry.fromState(level.getBlockState(pos)) != null) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COLLECTABLE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return false;
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        InteractionResult result = GasFluidInteractions.useItemOnCollectableGas(stack, state, level, pos, player, hand, hitResult);
        if (result == InteractionResult.SUCCESS) {
            return ItemInteractionResult.SUCCESS;
        }
        if (result == InteractionResult.FAIL) {
            return ItemInteractionResult.FAIL;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return 0.0F;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (cullNonSource(level, pos)) {
            return;
        }
        wakeAndSchedule(level, pos);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (level.getBlockState(pos).getBlock() == this) {
            wakeAndSchedule(level, pos);
        }
    }

    private boolean cullNonSource(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() != this) {
            return true;
        }
        RegisteredGas registered = gas.get();
        if (registered == null) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return true;
        }
        FluidState fluidState = level.getFluidState(pos);
        if (state.getValue(LEVEL) != 0
                || !fluidState.isSource()
                || fluidState.getType() != registered.sourceFluid()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return true;
        }
        return false;
    }

    private void wakeAndSchedule(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        if (cullNonSource(level, pos)) {
            return;
        }
        BlockState live = level.getBlockState(pos);
        if (live.getBlock() != this) {
            return;
        }
        RegisteredGas registered = gas.get();
        if (registered == null) {
            return;
        }
        if (live.getValue(COLLECTABLE) && shouldRise(level, pos, registered)) {
            live = live.setValue(COLLECTABLE, false);
            level.setBlock(pos, live, Block.UPDATE_ALL);
        } else if (live.getValue(COLLECTABLE) && shouldMarkCollectable(level, pos, registered)) {
            return;
        }
        if (!live.getValue(COLLECTABLE)) {
            schedule(level, pos);
        }
    }

    private void schedule(Level level, BlockPos pos) {
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, tickInterval);
        }
    }

    private static int ceilingY(Level level) {
        return level.getMaxBuildHeight() - 1;
    }

    private static boolean shouldRise(Level level, BlockPos pos, RegisteredGas gas) {
        if (pos.getY() >= ceilingY(level)) {
            return false;
        }
        BlockState above = level.getBlockState(pos.above());
        if (isSameGasFluid(above, gas)) {
            return false;
        }
        return above.canBeReplaced();
    }

    private static boolean shouldMarkCollectable(Level level, BlockPos pos, RegisteredGas gas) {
        return isTopOfColumn(level, pos, gas) && !shouldRise(level, pos, gas);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getBlock() != this) {
            return;
        }
        if (cullNonSource(level, pos)) {
            return;
        }
        RegisteredGas registered = gas.get();
        if (registered == null) {
            return;
        }

        BlockState live = level.getBlockState(pos);
        if (!isGasFluidBlock(live)) {
            return;
        }

        if (live.getValue(COLLECTABLE)) {
            if (shouldRise(level, pos, registered)) {
                live = live.setValue(COLLECTABLE, false);
                level.setBlock(pos, live, Block.UPDATE_ALL);
            } else if (shouldMarkCollectable(level, pos, registered)) {
                return;
            } else {
                live = live.setValue(COLLECTABLE, false);
                level.setBlock(pos, live, Block.UPDATE_ALL);
            }
        }

        live = level.getBlockState(pos);
        if (!isGasFluidBlock(live) || live.getValue(COLLECTABLE)) {
            return;
        }

        BlockPos above = pos.above();
        BlockState aboveState = level.getBlockState(above);

        if (!shouldRise(level, pos, registered)) {
            if (shouldMarkCollectable(level, pos, registered)) {
                level.setBlock(pos, live.setValue(COLLECTABLE, true), Block.UPDATE_ALL);
            }
            return;
        }

        if (isSameGasFluid(aboveState, registered)) {
            schedule(level, pos);
            return;
        }

        BlockState fluidAbove = registered.block().defaultBlockState().setValue(COLLECTABLE, false);
        BlockState toRestore = live;
        if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) {
            schedule(level, pos);
            return;
        }
        if (!level.setBlock(above, fluidAbove, Block.UPDATE_ALL)) {
            level.setBlock(pos, toRestore, Block.UPDATE_ALL);
            schedule(level, pos);
            return;
        }
        schedule(level, above);
    }

    private static boolean isSameGasFluid(BlockState state, RegisteredGas gas) {
        if (!isGasFluidBlock(state)) {
            return false;
        }
        RegisteredGas at = GasRegistry.fromState(state);
        return at == gas;
    }

    public static BlockBehaviour.Properties configureProperties(int lightLevel) {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.NONE)
                .noOcclusion()
                .noLootTable()
                .replaceable()
                .liquid()
                .pushReaction(PushReaction.IGNORE)
                .strength(-1.0F)
                .lightLevel(s -> lightLevel);
    }
}
