package net.unfamily.iskalib.gas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.unfamily.iskalib.transfer.LegacyIFluidHandlerResourceHandler;

/**
 * Drain-only pickup for the top of a rising gas fluid column ({@link GasLiquidBlock}).
 */
public final class GasFluidInteractions {
    private static final int BUCKET_VOLUME = 1000;

    private GasFluidInteractions() {}

    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        GasRegistry.bindBlocks();
        for (RegisteredGas gas : IskaLibGases.allRegisteredGases()) {
            if (!(gas.block() instanceof GasLiquidBlock)) {
                continue;
            }
            event.registerBlock(
                    Capabilities.Fluid.BLOCK,
                    (level, pos, state, blockEntity, direction) -> {
                        if (!GasLiquidBlock.isExtractableAt(level, pos, gas)) {
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
        if (gas == null || !GasLiquidBlock.isExtractableAt(event.getLevel(), event.getPos(), gas)) {
            return;
        }

        Player player = event.getEntity();
        InteractionHand hand = event.getHand();
        ItemStack stack = player.getItemInHand(hand);
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        Direction face = event.getFace() != null ? event.getFace() : Direction.UP;
        if (tryExtractOnly(level, player, hand, stack, gas, pos, face)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    public static boolean tryExtractOnly(Level level, Player player, InteractionHand hand, ItemStack stack,
                                          RegisteredGas gas, BlockPos pos) {
        return tryExtractOnly(level, player, hand, stack, gas, pos, Direction.UP);
    }

    public static boolean tryExtractOnly(Level level, Player player, InteractionHand hand, ItemStack stack,
                                          RegisteredGas gas, BlockPos pos, Direction side) {
        if (level.isClientSide() || player == null) {
            return false;
        }
        if (stack.isEmpty() || containsPlaceableGas(stack, gas)) {
            return false;
        }
        if (!GasLiquidBlock.isExtractableAt(level, pos, gas)) {
            return false;
        }
        if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, side)) {
            player.getInventory().setChanged();
            return true;
        }
        return false;
    }

    public static InteractionResult useItemOnCollectableGas(
            ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand
    ) {
        return useItemOnCollectableGas(stack, state, level, pos, player, hand, (Direction) null);
    }

    public static InteractionResult useItemOnCollectableGas(
            ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            @org.jetbrains.annotations.Nullable BlockHitResult hit
    ) {
        return useItemOnCollectableGas(stack, state, level, pos, player, hand,
                hit != null ? hit.getDirection() : null);
    }

    public static InteractionResult useItemOnCollectableGas(
            ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            @org.jetbrains.annotations.Nullable Direction side
    ) {
        if (!GasLiquidBlock.isExtractableAt(level, pos, GasRegistry.fromState(state))) {
            return InteractionResult.PASS;
        }
        RegisteredGas gas = GasRegistry.fromState(state);
        if (gas == null) {
            return InteractionResult.PASS;
        }
        if (stack.isEmpty() || containsPlaceableGas(stack, gas)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        Direction face = side != null ? side : Direction.UP;
        return tryExtractOnly(level, player, hand, stack, gas, pos, face)
                ? InteractionResult.SUCCESS
                : InteractionResult.FAIL;
    }

    private static boolean containsPlaceableGas(ItemStack stack, RegisteredGas gas) {
        FluidStack contained = FluidUtil.getFirstStackContained(stack);
        return !contained.isEmpty() && contained.is(gas.sourceFluid());
    }

    static void removeGasBlock(Level level, BlockPos pos) {
        GasLiquidBlock.removeGasAt(level, pos);
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
            if (maxDrain <= 0 || !GasLiquidBlock.isExtractableAt(level, pos, gas)) {
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
