package net.unfamily.iskalib.client.flux;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.unfamily.iskalib.flux.IskaLibParticles;

/**
 * Crit/enchanted-hit motion using the vanilla enchanted_hit sprite, tinted fixed {@code #2b576c}
 * (no MagicProvider purple skew). Client-only; registered from {@link IskaLibFluxClientHooks}.
 */
public final class UnknownFluxHitParticle extends TextureSheetParticle {
    private UnknownFluxHitParticle(
            ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.friction = 0.7F;
        this.gravity = 0.5F;
        this.xd *= 0.1F;
        this.yd *= 0.1F;
        this.zd *= 0.1F;
        this.xd += xSpeed * 0.4;
        this.yd += ySpeed * 0.4;
        this.zd += zSpeed * 0.4;
        int rgb = IskaLibParticles.UNKNOWN_FLUX_HIT_RGB;
        float brightness = (float) (Math.random() * 0.15F + 0.85F);
        this.rCol = ((rgb >> 16) & 0xFF) / 255.0F * brightness;
        this.gCol = ((rgb >> 8) & 0xFF) / 255.0F * brightness;
        this.bCol = (rgb & 0xFF) / 255.0F * brightness;
        this.quadSize *= 0.75F;
        this.lifetime = Math.max((int) (6.0 / (Math.random() * 0.8 + 0.6)), 1);
        this.hasPhysics = false;
        this.tick();
    }

    @Override
    public float getQuadSize(float partialTick) {
        return this.quadSize * Mth.clamp(((float) this.age + partialTick) / (float) this.lifetime * 32.0F, 0.0F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed) {
            UnknownFluxHitParticle particle = new UnknownFluxHitParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}
