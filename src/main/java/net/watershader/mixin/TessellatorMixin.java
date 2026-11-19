package net.watershader.mixin;

import net.minecraft.client.render.Tessellator;
import net.watershader.render.StencilNormalHelper;
import net.watershader.render.WaterLayer1Capture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fixes vanilla {@link Tessellator#normal} byte overflow/sign-extension and mirrors Tessellator
 * calls during Layer 1 chunk rebuilds before optimization mods can cancel them.
 */
@Mixin(value = Tessellator.class, priority = 900)
final class TessellatorMixin {
    @Shadow
    private boolean hasNormals;
    @Shadow
    private int normal;

    @Inject(method = "normal", at = @At("HEAD"), cancellable = true)
    private void watershader$packCleanNormal(float x, float y, float z, CallbackInfo ci) {
        int packed = StencilNormalHelper.packNormal(x, y, z);
        this.hasNormals = true;
        this.normal = packed;
        WaterLayer1Capture.onNormal(packed);
        ci.cancel();
    }

    @Inject(method = "start", at = @At("HEAD"))
    private void watershader$onStart(int mode, CallbackInfo ci) {
        WaterLayer1Capture.onStart(mode);
    }

    @Inject(method = "draw", at = @At("HEAD"))
    private void watershader$onDraw(CallbackInfo ci) {
        WaterLayer1Capture.onDraw();
    }

    @Inject(method = "setOffset", at = @At("HEAD"))
    private void watershader$onSetOffset(double x, double y, double z, CallbackInfo ci) {
        WaterLayer1Capture.onSetOffset(x, y, z);
    }

    @Inject(method = "translate", at = @At("HEAD"))
    private void watershader$onTranslate(float x, float y, float z, CallbackInfo ci) {
        WaterLayer1Capture.onTranslate(x, y, z);
    }

    @Inject(method = "texture", at = @At("HEAD"))
    private void watershader$onTexture(double u, double v, CallbackInfo ci) {
        WaterLayer1Capture.onTexture(u, v);
    }

    @Inject(method = "color(IIII)V", at = @At("HEAD"))
    private void watershader$onColor(int r, int g, int b, int a, CallbackInfo ci) {
        WaterLayer1Capture.onColor(r, g, b, a);
    }

    @Inject(method = "disableColor", at = @At("HEAD"))
    private void watershader$onDisableColor(CallbackInfo ci) {
        WaterLayer1Capture.onDisableColor();
    }

    @Inject(method = "vertex(DDD)V", at = @At("HEAD"))
    private void watershader$onVertex(double x, double y, double z, CallbackInfo ci) {
        WaterLayer1Capture.onVertex(x, y, z);
    }
}
