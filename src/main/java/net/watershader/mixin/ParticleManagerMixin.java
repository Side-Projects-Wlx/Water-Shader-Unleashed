package net.watershader.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.RainSplashParticle;
import net.minecraft.client.particle.WaterBubbleParticle;
import net.minecraft.client.render.Tessellator;
import net.minecraft.entity.Entity;
import net.watershader.render.WaterShaderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips opaque vanilla {@link RainSplashParticle}, {@link net.minecraft.client.particle.WaterSplashParticle},
 * and {@link WaterBubbleParticle} quads during {@code ParticleManager.render} so splashes render in
 * the isolated weather pass and bubbles render blended before Layer 1 water with matched pixel squares.
 */
@Mixin(ParticleManager.class)
final class ParticleManagerMixin {
    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/particle/Particle;render(Lnet/minecraft/client/render/Tessellator;FFFFFF)V"
            )
    )
    private void watershader$deferRainSplashToIsolatedWeather(
            Particle particle,
            Tessellator tessellator,
            float tickDelta,
            float rotX,
            float rotXZ,
            float rotZ,
            float rotYZ,
            float rotXY) {
        if (particle instanceof RainSplashParticle && WaterShaderPipeline.shouldReplaceWaterSplashes()) {
            return;
        }
        if (particle instanceof WaterBubbleParticle && WaterShaderPipeline.shouldReplaceWaterBubbles()) {
            return;
        }
        particle.render(tessellator, tickDelta, rotX, rotXZ, rotZ, rotYZ, rotXY);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void watershader$renderWaterHarmonizedBubbles(Entity camera, float tickDelta, CallbackInfo ci) {
        if (WaterShaderPipeline.shouldReplaceWaterBubbles()) {
            WaterShaderPipeline.renderModernWaterBubbles(camera, tickDelta);
        }
    }
}
