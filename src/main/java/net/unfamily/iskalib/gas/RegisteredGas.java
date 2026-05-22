package net.unfamily.iskalib.gas;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.function.Supplier;

/**
 * Handle for a fully registered gas type (fluids, block, bucket).
 */
public final class RegisteredGas {
    private final GasSpec spec;
    private final DeferredHolder<Fluid, ? extends Fluid> sourceFluid;
    private final DeferredHolder<Fluid, ? extends Fluid> flowingFluid;
    private final DeferredHolder<Block, ? extends Block> block;
    private final Supplier<DeferredHolder<Item, ? extends Item>> bucketItem;
    private final Identifier sourceFluidId;
    private final Identifier blockId;
    private final Identifier bucketId;

    public RegisteredGas(
            GasSpec spec,
            DeferredHolder<Fluid, ? extends Fluid> sourceFluid,
            DeferredHolder<Fluid, ? extends Fluid> flowingFluid,
            DeferredHolder<Block, ? extends Block> block,
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

    public GasSpec spec() {
        return spec;
    }

    public String modId() {
        return spec.modId();
    }

    public String name() {
        return spec.name();
    }

    public int tintArgb() {
        return spec.tintArgb();
    }

    public Fluid sourceFluid() {
        return sourceFluid.get();
    }

    public Fluid flowingFluid() {
        return flowingFluid.get();
    }

    public DeferredHolder<Block, ? extends Block> blockHolder() {
        return block;
    }

    public Block block() {
        return block.get();
    }

    public boolean isBucketReady() {
        DeferredHolder<Item, ? extends Item> holder = bucketItem.get();
        return holder != null && holder.isBound();
    }

    public Item bucketItem() {
        DeferredHolder<Item, ? extends Item> holder = bucketItem.get();
        if (holder == null) {
            throw new IllegalStateException("Bucket not registered yet for gas " + spec.modId() + ":" + spec.name());
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
