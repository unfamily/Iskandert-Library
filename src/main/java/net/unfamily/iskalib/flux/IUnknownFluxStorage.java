package net.unfamily.iskalib.flux;

/**
 * Internal long-based energy buffer for Iskandert mods.
 *
 * <p><b>Not Forge Energy (FE).</b> Unknown Flux has no direct conversion to or from FE /
 * {@code IEnergyStorage}. Prefer FE for normal machines and cables; use Unknown Flux only for
 * specific mechanics that need an isolated long energy pool.
 */
public interface IUnknownFluxStorage {

    /** Currently stored Unknown Flux. */
    long getUnknownFlux();

    /** Maximum Unknown Flux this buffer can hold. */
    long getUnknownFluxCapacity();

    /** Maximum Unknown Flux accepted per {@link #insertUnknownFlux(long, boolean)} call. */
    long getMaxUnknownFluxInsert();

    /** Maximum Unknown Flux removed per {@link #extractUnknownFlux(long, boolean)} call. */
    long getMaxUnknownFluxExtract();

    /**
     * Inserts up to {@code amount} Unknown Flux.
     *
     * @param simulate if true, do not mutate state
     * @return amount actually (or that would be) inserted
     */
    long insertUnknownFlux(long amount, boolean simulate);

    /**
     * Extracts up to {@code amount} Unknown Flux.
     *
     * @param simulate if true, do not mutate state
     * @return amount actually (or that would be) extracted
     */
    long extractUnknownFlux(long amount, boolean simulate);

    /** Remaining free capacity. */
    default long getUnknownFluxSpace() {
        return Math.max(0L, getUnknownFluxCapacity() - getUnknownFlux());
    }

    default boolean canReceiveUnknownFlux() {
        return getMaxUnknownFluxInsert() > 0L && getUnknownFluxSpace() > 0L;
    }

    default boolean canExtractUnknownFlux() {
        return getMaxUnknownFluxExtract() > 0L && getUnknownFlux() > 0L;
    }
}
