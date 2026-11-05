package net.unfamily.iskalib.flux;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * Default {@link IUnknownFluxStorage} backed by {@code long} fields.
 *
 * <p>Unknown Flux is an internal energy unit for specific mod mechanics. It is
 * <b>not</b> Forge Energy: do not expose this as an FE capability and do not convert
 * to/from FE. Ordinary power transfer should keep using FE.
 */
public class UnknownFluxStorage implements IUnknownFluxStorage {

    public static final String TAG_UNKNOWN_FLUX = "unknownFlux";
    public static final String TAG_CAPACITY = "unknownFluxCapacity";

    private long stored;
    private long capacity;
    private long maxInsert;
    private long maxExtract;
    private final Runnable onChange;

    public UnknownFluxStorage(long capacity, long maxInsert, long maxExtract) {
        this(capacity, maxInsert, maxExtract, 0L, null);
    }

    public UnknownFluxStorage(long capacity, long maxInsert, long maxExtract, long initial, @Nullable Runnable onChange) {
        if (capacity < 0L || maxInsert < 0L || maxExtract < 0L || initial < 0L) {
            throw new IllegalArgumentException("Unknown Flux arguments must be non-negative");
        }
        this.capacity = capacity;
        this.maxInsert = maxInsert;
        this.maxExtract = maxExtract;
        this.stored = Math.min(capacity, initial);
        this.onChange = onChange != null ? onChange : () -> {};
    }

    @Override
    public long getUnknownFlux() {
        return stored;
    }

    @Override
    public long getUnknownFluxCapacity() {
        return capacity;
    }

    @Override
    public long getMaxUnknownFluxInsert() {
        return maxInsert;
    }

    @Override
    public long getMaxUnknownFluxExtract() {
        return maxExtract;
    }

    @Override
    public long insertUnknownFlux(long amount, boolean simulate) {
        if (amount <= 0L || maxInsert <= 0L) {
            return 0L;
        }
        long space = capacity - stored;
        if (space <= 0L) {
            return 0L;
        }
        long inserted = Math.min(amount, Math.min(space, maxInsert));
        if (inserted <= 0L) {
            return 0L;
        }
        if (!simulate) {
            stored += inserted;
            onChange.run();
        }
        return inserted;
    }

    @Override
    public long extractUnknownFlux(long amount, boolean simulate) {
        if (amount <= 0L || maxExtract <= 0L || stored <= 0L) {
            return 0L;
        }
        long extracted = Math.min(amount, Math.min(stored, maxExtract));
        if (extracted <= 0L) {
            return 0L;
        }
        if (!simulate) {
            stored -= extracted;
            onChange.run();
        }
        return extracted;
    }

    /** Sets stored amount, clamped to {@code [0, capacity]}. */
    public void setUnknownFlux(long amount) {
        if (amount < 0L) {
            throw new IllegalArgumentException("Unknown Flux amount must be non-negative");
        }
        long clamped = Math.min(amount, capacity);
        if (stored != clamped) {
            stored = clamped;
            onChange.run();
        }
    }

    /** Resizes limits; clamps stored amount to the new capacity. */
    public void resize(long newCapacity, long newMaxInsert, long newMaxExtract) {
        if (newCapacity < 0L || newMaxInsert < 0L || newMaxExtract < 0L) {
            throw new IllegalArgumentException("Unknown Flux arguments must be non-negative");
        }
        capacity = newCapacity;
        maxInsert = newMaxInsert;
        maxExtract = newMaxExtract;
        stored = Math.min(stored, capacity);
        onChange.run();
    }

    public void save(CompoundTag tag) {
        tag.putLong(TAG_UNKNOWN_FLUX, stored);
        tag.putLong(TAG_CAPACITY, capacity);
    }

    public void load(CompoundTag tag) {
        if (tag.contains(TAG_CAPACITY)) {
            capacity = Math.max(0L, tag.getLong(TAG_CAPACITY));
        }
        stored = Math.max(0L, Math.min(capacity, tag.contains(TAG_UNKNOWN_FLUX) ? tag.getLong(TAG_UNKNOWN_FLUX) : 0L));
    }
}
