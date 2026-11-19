package net.watershader.mixin;

import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Exposes active particle buckets on {@link ParticleManager} for zero-allocation splash inspection
 * during the isolated modern weather pass.
 */
@Mixin(ParticleManager.class)
public interface ParticleManagerAccessor {
    @Accessor("particles")
    List<?>[] watershader$getParticles();
}
