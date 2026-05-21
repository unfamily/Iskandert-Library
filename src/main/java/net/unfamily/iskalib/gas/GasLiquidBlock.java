package net.unfamily.iskalib.gas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
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
 * Pump/bucket compatible via standard fluid block + drain-only capability when {@link #isExtractableAt}.
 */
public class GasLiquidBlock extends LiquidBlock {

    public static final BooleanProperty COLLECTABLE = BooleanProperty.create("collectable");
    private static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

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

    /**
     * Top of a gas column: no registered gas fluid block directly above (fixes extraction under another steam block).
     */
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return false;
    }

    @Override
    protected InteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        InteractionResult result = GasFluidInteractions.useItemOnCollectableGas(stack, state, level, pos, player, hand, hitResult);
        return result != InteractionResult.PASS ? result : super.useItemOn(stack, state, level, pos, player, hand, hitResult);
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
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return ItemStack.EMPTY;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.getValue(COLLECTABLE)) {
            schedule(level, pos);
        }
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighbor,
            @Nullable net.minecraft.world.level.redstone.Orientation orientation,
            boolean movedByPiston
    ) {
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

    private static int ceilingY(Level level) {
        return level.getMaxY();
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getBlock() != this || state.getValue(COLLECTABLE)) {
            return;
        }
        RegisteredGas registered = gas.get();
        if (registered == null) {
            return;
        }

        BlockState live = level.getBlockState(pos);
        if (!isGasFluidBlock(live) || live.getValue(COLLECTABLE)) {
            return;
        }

        BlockPos above = pos.above();
        BlockState aboveState = level.getBlockState(above);

        if (pos.getY() >= ceilingY(level) || (isBlockedAbove(aboveState) && !isSameGasFluid(aboveState, registered))) {
            if (isTopOfColumn(level, pos, registered)) {
                level.setBlock(pos, live.setValue(COLLECTABLE, true), Block.UPDATE_ALL);
            }
            return;
        }

        if (isSameGasFluid(aboveState, registered)) {
            schedule(level, pos);
            return;
        }

        if (!aboveState.canBeReplaced()) {
            if (isTopOfColumn(level, pos, registered)) {
                level.setBlock(pos, live.setValue(COLLECTABLE, true), Block.UPDATE_ALL);
            }
            return;
        }

        Fluid source = registered.sourceFluid();
        BlockState fluidAbove = source.defaultFluidState().createLegacyBlock();
        if (!level.setBlock(above, fluidAbove, Block.UPDATE_ALL)) {
            schedule(level, pos);
            return;
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        schedule(level, above);
    }

    private static boolean isSameGasFluid(BlockState state, RegisteredGas gas) {
        if (!isGasFluidBlock(state)) {
            return false;
        }
        RegisteredGas at = GasRegistry.fromState(state);
        return at == gas;
    }

    private static boolean isBlockedAbove(BlockState above) {
        return !above.canBeReplaced();
    }

    public static BlockBehaviour.Properties configureProperties(BlockBehaviour.Properties props, int lightLevel) {
        return props
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
