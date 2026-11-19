package net.watershader.mixin;

import net.minecraft.client.Minecraft;
import net.watershader.WaterShaderClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Compiles Water Shader GPU programs right after Minecraft creates its LWJGL OpenGL display.
 */
@Mixin(Minecraft.class)
final class MinecraftInitMixin {
    @Inject(method = "init", at = @At("RETURN"))
    private void watershader$onGlContextReady(CallbackInfo ci) {
        WaterShaderClient.onGlContextReady((Minecraft) (Object) this);
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void watershader$onShutdown(CallbackInfo ci) {
        WaterShaderClient.shutdown();
    }
}
