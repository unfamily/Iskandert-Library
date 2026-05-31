package net.unfamily.iskalib.liquid;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.function.Supplier;

/**
 * Handle for a fully registered liquid (fluids, block, optional bucket).
 */
public final class RegisteredLiquid {
    private final LiquidSpec spec;
    private final DeferredHolder<Fluid, ? extends Fluid> sourceFluid;
    private final DeferredHolder<Fluid, ? extends Fluid> flowingFluid;
    private final DeferredBlock<? extends Block> block;
    private final Supplier<DeferredHolder<Item, ? extends Item>> bucketItem;
    private final Identifier sourceFluidId;
    private final Identifier blockId;
    private final Identifier bucketId;

    public RegisteredLiquid(
            LiquidSpec spec,
            DeferredHolder<Fluid, ? extends Fluid> sourceFluid,
            DeferredHolder<Fluid, ? extends Fluid> flowingFluid,
            DeferredBlock<? extends Block> block,
            Supplier<DeferredHolder<Item, ? extends Item>> bucketItem,
            Identifier sourceFluidId,
            Identifier blockId,
            Identifier bucketId
    ) {
        this.spec = spec;
        this.sourceFluid = sourceFluid;
        this.flowingFluid = flowingFluid;
        this.block = block;
        this.bucketItem = bucketItem;
        this.sourceFluidId = sourceFluidId;
        this.blockId = blockId;
        this.bucketId = bucketId;
    }

    public LiquidSpec spec() {
        return spec;
    }

    public DeferredHolder<Fluid, ? extends Fluid> sourceHolder() {
        return sourceFluid;
    }

    public DeferredHolder<Fluid, ? extends Fluid> flowingHolder() {
        return flowingFluid;
    }

    public DeferredBlock<? extends Block> blockHolder() {
        return block;
    }

    public DeferredHolder<Item, ? extends Item> bucketHolder() {
        DeferredHolder<Item, ? extends Item> holder = bucketItem.get();
        if (holder == null) {
            throw new IllegalStateException("Bucket not registered for liquid " + spec.modId() + ":" + spec.name());
        }
        return holder;
    }

    public Fluid sourceFluid() {
        return sourceFluid.get();
    }

    /** Alias matching Colossal Reactors {@code TintedFluid#getSource}. */
    public Fluid getSource() {
        return sourceFluid.get();
    }

    public Fluid flowingFluid() {
        return flowingFluid.get();
    }

    /** Alias matching Colossal Reactors {@code TintedFluid#getFlowing}. */
    public Fluid getFlowing() {
        return flowingFluid.get();
    }

    public Block block() {
        return block.get();
    }

    public Item bucketItem() {
        DeferredHolder<Item, ? extends Item> holder = bucketItem.get();
        if (holder == null || !holder.isBound()) {
            throw new IllegalStateException("Bucket not registered for liquid " + spec.modId() + ":" + spec.name());
        }
        return holder.get();
    }

    public Identifier sourceFluidId() {
        return sourceFluidId;
    }

    public Identifier blockId() {
        return blockId;
    }

    public Identifier bucketId() {
        return bucketId;
    }
}
