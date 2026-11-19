package net.watershader.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes particle age and scale fields so {@code ModernWeatherRenderer} can fade rain splashes cleanly.
 */
@Mixin(Particle.class)
public interface ParticleAccessor {
    @Accessor("particleAge")
    int watershader$getParticleAge();

    @Accessor("maxParticleAge")
    int watershader$getMaxParticleAge();

    @Accessor("scale")
    float watershader$getScale();

    @Accessor("textureId")
    int watershader$getTextureId();
}
