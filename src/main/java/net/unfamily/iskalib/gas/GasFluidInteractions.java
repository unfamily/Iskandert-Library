package net.unfamily.iskalib.gas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.unfamily.iskalib.transfer.LegacyIFluidHandlerResourceHandler;

/**
 * Pickup for collectible {@link GasBlock}: fluid exists only for extraction (drain), never insertion (fill).
 * Block is removed once drained (empty).
 */
public final class GasFluidInteractions {
    private static final int BUCKET_VOLUME = 1000;

    private GasFluidInteractions() {}

    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        for (RegisteredGas gas : IskaLibGases.allRegisteredGases()) {
            if (!(gas.block() instanceof GasBlock)) {
                continue;
            }
            event.registerBlock(
                    Capabilities.Fluid.BLOCK,
                    (level, pos, state, blockEntity, direction) -> {
                        if (!GasBlock.isCollectable(state)) {
                            return null;
                        }
                        return LegacyIFluidHandlerResourceHandler.wrap(
                                new GasBlockFluidHandler(gas, level, pos));
                    },
                    gas.block());
        }
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        RegisteredGas gas = GasRegistry.fromState(state);
        if (gas == null || !GasBlock.isCollectable(state)) {
            return;
        }

        Player player = event.getEntity();
        InteractionHand hand = event.getHand();
        ItemStack stack = player.getItemInHand(hand);
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        Direction face = event.getFace() != null ? event.getFace() : Direction.UP;

        if (tryExtractOnly(level, player, hand, stack, gas, pos)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    private static boolean tryExtractOnly(Level level, Player player, InteractionHand hand, ItemStack stack,
                                          RegisteredGas gas, BlockPos pos) {
        if (stack.is(Items.BUCKET)) {
            if (!GasBlock.isCollectableGasAt(level, pos, gas)) {
                return false;
            }
            ItemStack filled = GasBucketItem.createFilledBucket(gas);
            if (filled.isEmpty()) {
                return false;
            }
            player.setItemInHand(hand, filled);
            GasBlock.removeGasBlockIfPresent(level, pos);
            playEmptySound(level, player, gas);
            return true;
        }

        if (stack.isEmpty() || containsPlaceableGas(stack, gas)) {
            return false;
        }

        var itemAccess = ItemAccess.forPlayerInteraction(player, hand).oneByOne();
        ResourceHandler<FluidResource> itemHandler = itemAccess.getCapability(Capabilities.Fluid.ITEM);
        if (itemHandler == null) {
            return false;
        }

        ResourceHandler<FluidResource> blockHandler =
                LegacyIFluidHandlerResourceHandler.wrap(new GasBlockFluidHandler(gas, level, pos));
        var moved = ResourceHandlerUtil.moveFirst(
                blockHandler, itemHandler, fr -> fr.is(gas.sourceFluid()), BUCKET_VOLUME, null);
        if (moved == null || moved.amount() <= 0) {
            return false;
        }
        FluidUtil.triggerSoundAndGameEvent(moved.resource(), level, Vec3.atCenterOf(pos), player, true);
        return true;
    }

    private static boolean containsPlaceableGas(ItemStack stack, RegisteredGas gas) {
        FluidStack contained = FluidUtil.getFirstStackContained(stack);
        return !contained.isEmpty() && contained.is(gas.sourceFluid());
    }

    static void removeGasBlock(Level level, BlockPos pos) {
        GasBlock.removeGasBlockIfPresent(level, pos);
    }

    private static void playEmptySound(Level level, Player player, RegisteredGas gas) {
        var soundEvent = gas.sourceFluid().getFluidType().getSound(SoundActions.BUCKET_EMPTY);
        if (soundEvent != null) {
            level.playSound(null, player.blockPosition(), soundEvent, SoundSource.BLOCKS, 1.0F, 1.0F);
        } else {
            level.playSound(null, player.blockPosition(), SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    static final class GasBlockFluidHandler implements IFluidHandler {
        private final RegisteredGas gas;
        private final Level level;
        private final BlockPos pos;

        GasBlockFluidHandler(RegisteredGas gas, Level level, BlockPos pos) {
            this.gas = gas;
            this.level = level;
            this.pos = pos;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return new FluidStack(gas.sourceFluid(), BUCKET_VOLUME);
        }

        @Override
        public int getTankCapacity(int tank) {
            return BUCKET_VOLUME;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (!resource.isEmpty() && !resource.is(gas.sourceFluid())) {
                return FluidStack.EMPTY;
            }
            return drain(resource.isEmpty() ? BUCKET_VOLUME : resource.getAmount(), action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0 || !GasBlock.isCollectableGasAt(level, pos, gas)) {
                return FluidStack.EMPTY;
            }
            int amount = Math.min(maxDrain, BUCKET_VOLUME);
            FluidStack drained = new FluidStack(gas.sourceFluid(), amount);
            if (action == FluidAction.EXECUTE && level instanceof ServerLevel) {
                removeGasBlock(level, pos);
            }
            return drained;
        }
    }
}
