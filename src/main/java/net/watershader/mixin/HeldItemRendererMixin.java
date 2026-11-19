package net.watershader.mixin;

import net.minecraft.client.render.item.HeldItemRenderer;
import net.watershader.render.WaterShaderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses vanilla's flat {@code /misc/water.png} quad overlay when the Water Shader underwater
 * effect is active, while preserving {@code renderScreenOverlays}'s {@code GL_ALPHA_TEST} restore.
 */
@Mixin(HeldItemRenderer.class)
final class HeldItemRendererMixin {
    @Inject(method = "renderUnderwaterOverlay", at = @At("HEAD"), cancellable = true)
    private void watershader$cancelVanillaUnderwaterOverlay(float tickDelta, CallbackInfo ci) {
        if (WaterShaderPipeline.shouldReplaceUnderwaterOverlay()) {
            ci.cancel();
        }
    }
}
