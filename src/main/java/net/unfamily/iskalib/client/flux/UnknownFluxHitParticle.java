package net.unfamily.iskalib.client.flux;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.unfamily.iskalib.flux.IskaLibParticles;

/**
 * Crit/enchanted-hit motion using the vanilla enchanted_hit sprite, tinted fixed {@code #2b576c}
 * (no MagicProvider purple skew). Client-only; registered from {@link IskaLibFluxClientHooks}.
 */
public final class UnknownFluxHitParticle extends SingleQuadParticle {
    private UnknownFluxHitParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            TextureAtlasSprite sprite) {
        super(level, x, y, z, 0.0, 0.0, 0.0, sprite);
        this.friction = 0.7F;
        this.gravity = 0.5F;
        this.xd *= 0.1F;
        this.yd *= 0.1F;
        this.zd *= 0.1F;
        this.xd += xSpeed * 0.4;
        this.yd += ySpeed * 0.4;
        this.zd += zSpeed * 0.4;
        int rgb = IskaLibParticles.UNKNOWN_FLUX_HIT_RGB;
        float brightness = this.random.nextFloat() * 0.15F + 0.85F;
        this.rCol = ((rgb >> 16) & 0xFF) / 255.0F * brightness;
        this.gCol = ((rgb >> 8) & 0xFF) / 255.0F * brightness;
        this.bCol = (rgb & 0xFF) / 255.0F * brightness;
        this.quadSize *= 0.75F;
        this.lifetime = Math.max((int) (6.0 / (this.random.nextFloat() * 0.8 + 0.6)), 1);
        this.hasPhysics = false;
        this.tick();
    }

    @Override
    public float getQuadSize(float partialTick) {
        return this.quadSize * Mth.clamp((this.age + partialTick) / this.lifetime * 32.0F, 0.0F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        // Keep teal; do not skew toward red like CritParticle.MagicProvider.
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.OPAQUE;
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
                double zSpeed,
                RandomSource random) {
            return new UnknownFluxHitParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites.get(random));
        }
    }
}
