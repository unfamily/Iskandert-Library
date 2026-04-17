package net.unfamily.iskalib.transfer;

import java.util.Objects;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Bridges a legacy {@link IItemHandler} to NeoForge 26 {@link ResourceHandler}{@code <ItemResource>} for
 * {@link net.neoforged.neoforge.capabilities.Capabilities.Item#BLOCK} registration. Uses snapshots only when
 * delegates implement {@link IItemHandlerModifiable} (required for transactional rollback).
 */
public final class LegacyItemHandlerResourceHandler extends SnapshotJournal<NonNullList<ItemStack>>
        implements ResourceHandler<ItemResource> {

    private final IItemHandler delegate;

    private LegacyItemHandlerResourceHandler(IItemHandler delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public static ResourceHandler<ItemResource> wrap(IItemHandler delegate) {
        return new LegacyItemHandlerResourceHandler(delegate);
    }

    @Override
    public int size() {
        return delegate.getSlots();
    }

    @Override
    public ItemResource getResource(int index) {
        Objects.checkIndex(index, size());
        return ItemResource.of(delegate.getStackInSlot(index));
    }

    @Override
    public long getAmountAsLong(int index) {
        Objects.checkIndex(index, size());
        return delegate.getStackInSlot(index).getCount();
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        Objects.checkIndex(index, size());
        if (resource.isEmpty()) {
            return delegate.getSlotLimit(index);
        }
        if (!delegate.isItemValid(index, resource.toStack(1))) {
            return 0;
        }
        return Math.min(delegate.getSlotLimit(index), resource.getMaxStackSize());
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        Objects.checkIndex(index, size());
        TransferPreconditions.checkNonEmpty(resource);
        return delegate.isItemValid(index, resource.toStack(1));
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        Objects.checkIndex(index, size());
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        updateSnapshots(transaction);
        ItemStack toInsert = resource.toStack(amount);
        int requested = toInsert.getCount();
        ItemStack remainder = delegate.insertItem(index, toInsert, false);
        return requested - remainder.getCount();
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        Objects.checkIndex(index, size());
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        ItemStack current = delegate.getStackInSlot(index);
        if (!resource.matches(current)) {
            return 0;
        }
        updateSnapshots(transaction);
        return delegate.extractItem(index, amount, false).getCount();
    }

    @Override
    protected NonNullList<ItemStack> createSnapshot() {
        int slots = delegate.getSlots();
        NonNullList<ItemStack> list = NonNullList.withSize(slots, ItemStack.EMPTY);
        for (int i = 0; i < slots; i++) {
            list.set(i, delegate.getStackInSlot(i).copy());
        }
        return list;
    }

    @Override
    protected void revertToSnapshot(NonNullList<ItemStack> snapshot) {
        if (delegate instanceof IItemHandlerModifiable modifiable) {
            for (int i = 0; i < snapshot.size(); i++) {
                modifiable.setStackInSlot(i, snapshot.get(i).copy());
            }
            return;
        }
        throw new IllegalStateException(
                "Transactional item transfer requires IItemHandlerModifiable but got: " + delegate.getClass().getName());
    }
}
