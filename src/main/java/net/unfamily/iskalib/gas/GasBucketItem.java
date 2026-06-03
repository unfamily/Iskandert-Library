package net.unfamily.iskalib.gas;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Places gas as a rising fluid block via vanilla bucket logic; extraction only at the column top.
 */
public class GasBucketItem extends BucketItem {
    private final RegisteredGas gas;

    public GasBucketItem(Properties properties, RegisteredGas gas, DeferredHolder<Fluid, ? extends Fluid> sourceFluid) {
        super(sourceFluid.get(), properties);
        this.gas = gas;
    }

    public static ItemStack createFilledBucket(RegisteredGas gas) {
        return new ItemStack(gas.bucketItem());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockState clicked = level.getBlockState(context.getClickedPos());
        Player player = context.getPlayer();
        if (player != null && GasLiquidBlock.isExtractableAt(level, context.getClickedPos(), null)) {
            InteractionResult extract = GasFluidInteractions.useItemOnCollectableGas(
                    context.getItemInHand(), clicked, level, context.getClickedPos(), player, context.getHand(),
                    context.getClickedFace());
            if (extract != InteractionResult.PASS) {
                return extract;
            }
        }
        return super.useOn(context);
    }
}
