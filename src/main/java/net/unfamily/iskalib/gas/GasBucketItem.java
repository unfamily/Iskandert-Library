package net.unfamily.iskalib.gas;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Places the gas block in the world (not a liquid block).
 */
public class GasBucketItem extends BucketItem {
    private final RegisteredGas gas;

    public GasBucketItem(Properties properties, RegisteredGas gas) {
        super(gas.sourceFluid(), properties);
        this.gas = gas;
    }

    public RegisteredGas registeredGas() {
        return gas;
    }

    public static ItemStack createFilledBucket(RegisteredGas gas) {
        return new ItemStack(gas.bucketItem());
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        BlockHitResult hit = getPlayerPOVHitResult(level, player, net.minecraft.world.level.ClipContext.Fluid.NONE);
        InteractionResult result = super.use(level, player, hand);
        if (result != InteractionResult.SUCCESS) {
            return placeGasBlock(level, player, hand, hit);
        }
        return result;
    }

    @Override
    public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState clicked = level.getBlockState(pos);
        BlockPos placePos = clicked.canBeReplaced() ? pos : pos.relative(context.getClickedFace());

        if (tryPlaceGas(level, placePos)) {
            if (!level.isClientSide()) {
                Player player = context.getPlayer();
                if (player != null && !player.getAbilities().instabuild) {
                    context.getItemInHand().shrink(1);
                    if (!player.getInventory().add(new ItemStack(Items.BUCKET))) {
                        player.drop(new ItemStack(Items.BUCKET), false);
                    }
                }
                level.playSound(null, placePos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.gameEvent(context.getPlayer(), GameEvent.FLUID_PLACE, placePos);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }

    private InteractionResult placeGasBlock(Level level, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hit == null) {
            return InteractionResult.FAIL;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        BlockPos placePos = state.canBeReplaced() ? pos : pos.relative(hit.getDirection());
        if (!tryPlaceGas(level, placePos)) {
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            if (!player.getAbilities().instabuild) {
                player.getItemInHand(hand).shrink(1);
                if (!player.getInventory().add(new ItemStack(Items.BUCKET))) {
                    player.drop(new ItemStack(Items.BUCKET), false);
                }
            }
            level.playSound(null, placePos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(player, GameEvent.FLUID_PLACE, placePos);
        }
        return InteractionResult.SUCCESS;
    }

    private boolean tryPlaceGas(Level level, BlockPos pos) {
        if (pos.getY() > level.getMaxY() || pos.getY() < level.getMinY()) {
            return false;
        }
        BlockState existing = level.getBlockState(pos);
        if (!existing.canBeReplaced()) {
            return false;
        }
        BlockState gasState = gas.block().defaultBlockState();
        return level.setBlock(pos, gasState, Block.UPDATE_ALL);
    }
}
