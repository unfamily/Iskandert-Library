package net.unfamily.iskalib.flux;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.unfamily.iskalib.IskaLib;

/**
 * Unknown Flux visual particles. {@link #UNKNOWN_FLUX_HIT} reuses the enchanted-hit sprite with a fixed
 * teal tint ({@code #2b576c}).
 */
public final class IskaLibParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, IskaLib.MOD_ID);

    /** Enchanted-hit style spark tinted {@code #2b576c}. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> UNKNOWN_FLUX_HIT =
            PARTICLE_TYPES.register("unknown_flux_hit", () -> new SimpleParticleType(false));

    /** Fixed spawn tint matching {@code #2b576c}. */
    public static final int UNKNOWN_FLUX_HIT_RGB = 0x2b576c;

    private IskaLibParticles() {}

    public static void register(IEventBus modEventBus) {
        PARTICLE_TYPES.register(modEventBus);
    }
}
