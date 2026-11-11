package net.unfamily.iskalib.client.flux;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.unfamily.iskalib.IskaLib;
import net.unfamily.iskalib.flux.IskaLibParticles;

@EventBusSubscriber(modid = IskaLib.MOD_ID, value = Dist.CLIENT)
public final class IskaLibFluxClientHooks {
    private IskaLibFluxClientHooks() {}

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(IskaLibParticles.UNKNOWN_FLUX_HIT.get(), UnknownFluxHitParticle.Provider::new);
    }
}
