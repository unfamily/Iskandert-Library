package net.unfamily.iskalib.transfer;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.Objects;

/**
 * Presents a legacy {@link IFluidHandler} as {@link ResourceHandler}{@code <FluidResource>} for NeoForge 26 capabilities.
 */
public final class LegacyIFluidHandlerResourceHandler implements ResourceHandler<FluidResource> {

    private final IFluidHandler delegate;

    private LegacyIFluidHandlerResourceHandler(IFluidHandler delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    public static ResourceHandler<FluidResource> wrap(IFluidHandler delegate) {
        return new LegacyIFluidHandlerResourceHandler(delegate);
    }

    @Override
    public int size() {
        return delegate.getTanks();
    }

    @Override
    public FluidResource getResource(int index) {
        Objects.checkIndex(index, size());
        return FluidResource.of(delegate.getFluidInTank(index));
    }

    @Override
    public long getAmountAsLong(int index) {
        Objects.checkIndex(index, size());
        return delegate.getFluidInTank(index).getAmount();
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        Objects.checkIndex(index, size());
        if (!resource.isEmpty() && !delegate.isFluidValid(index, resource.toStack(1))) {
            return 0;
        }
        return delegate.getTankCapacity(index);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        Objects.checkIndex(index, size());
        if (resource.isEmpty()) {
            return true;
        }
        return delegate.isFluidValid(index, resource.toStack(1));
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        Objects.checkIndex(index, size());
        try (Transaction sub = Transaction.open(transaction)) {
            FluidStack stack = resource.toStack(amount);
            int sim = delegate.fill(stack, IFluidHandler.FluidAction.SIMULATE);
            if (sim <= 0) {
                return 0;
            }
            int filled = delegate.fill(resource.toStack(sim), IFluidHandler.FluidAction.EXECUTE);
            sub.commit();
            return filled;
        }
    }

    @Override
    public int insert(FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        try (Transaction sub = Transaction.open(transaction)) {
            FluidStack stack = resource.toStack(amount);
            int sim = delegate.fill(stack, IFluidHandler.FluidAction.SIMULATE);
            if (sim <= 0) {
                return 0;
            }
            int filled = delegate.fill(resource.toStack(sim), IFluidHandler.FluidAction.EXECUTE);
            sub.commit();
            return filled;
        }
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        Objects.checkIndex(index, size());
        try (Transaction sub = Transaction.open(transaction)) {
            FluidStack template = resource.toStack(amount);
            FluidStack sim = delegate.drain(template, IFluidHandler.FluidAction.SIMULATE);
            if (sim.isEmpty()) {
                return 0;
            }
            FluidStack drained = delegate.drain(template, IFluidHandler.FluidAction.EXECUTE);
            sub.commit();
            return drained.getAmount();
        }
    }

    @Override
    public int extract(FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        try (Transaction sub = Transaction.open(transaction)) {
            FluidStack template = resource.toStack(amount);
            FluidStack sim = delegate.drain(template, IFluidHandler.FluidAction.SIMULATE);
            if (sim.isEmpty()) {
                return 0;
            }
            FluidStack drained = delegate.drain(template, IFluidHandler.FluidAction.EXECUTE);
            sub.commit();
            return drained.getAmount();
        }
    }
}
