package net.watershader.mixin;

import net.minecraft.client.render.WorldRenderer;
import net.watershader.render.WaterShaderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Unbinds pass shaders and restores color mask after both vanilla and Amphetamine finish drawing
 * inside {@code WorldRenderer.renderLastChunks}. Runs after Amphetamine (priority 1100).
 */
@Mixin(value = WorldRenderer.class, priority = 1100)
final class WorldRendererPostMixin {
    @Inject(method = "renderLastChunks", at = @At("RETURN"))
    private void watershader$afterRenderLastChunks(int renderPass, double delta, CallbackInfo ci) {
        WaterShaderPipeline.onWorldRenderLastChunksEnd(renderPass);
    }
}
