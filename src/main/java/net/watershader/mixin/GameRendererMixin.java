package net.watershader.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;
import net.watershader.render.WaterShaderPipeline;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Orchestrates multi-pass Main/Stencil/Reflection frame execution in {@link GameRenderer}.
 */
@Mixin(value = GameRenderer.class, priority = 950)
final class GameRendererMixin {
    @Shadow
    private Minecraft client;

    @Shadow
    protected void renderSnow(float tickDelta) {
    }

    @Shadow
    private void renderFirstPersonHand(float tickDelta, int eye) {
    }

    @Redirect(
            method = "onFrameUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/GameRenderer;renderFrame(FJ)V"
            )
    )
    private void watershader$runMultiPassFrame(GameRenderer self, float tickDelta, long finishTimeNano) {
        WaterShaderPipeline.renderFramePasses(
                this.client,
                tickDelta,
                () -> self.renderFrame(tickDelta, finishTimeNano));
    }

    @Redirect(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/GameRenderer;updateTargetedEntity(F)V"
            )
    )
    private void watershader$guardUpdateTargetedEntity(GameRenderer self, float tickDelta) {
        if (WaterShaderPipeline.shouldUpdateTargetedOrCompileChunks()) {
            self.updateTargetedEntity(tickDelta);
        }
    }

    @Redirect(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL11;glViewport(IIII)V",
                    remap = false
            )
    )
    private void watershader$redirectViewport(int x, int y, int width, int height) {
        GL11.glViewport(
                x,
                y,
                WaterShaderPipeline.getActiveViewportWidth(width),
                WaterShaderPipeline.getActiveViewportHeight(height));
    }

    @Redirect(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL11;glClear(I)V",
                    ordinal = 0,
                    remap = false
            )
    )
    private void watershader$redirectInitialClear(int mask) {
        WaterShaderPipeline.onGlClear(mask);
    }

    @Inject(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/GameRenderer;renderWorld(FI)V",
                    shift = At.Shift.AFTER
            )
    )
    private void watershader$afterCameraSetup(float tickDelta, long finishTimeNano, CallbackInfo ci) {
        WaterShaderPipeline.afterCameraSetup(this.client, tickDelta);
    }

    @Redirect(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;renderSky(F)V"
            )
    )
    private void watershader$guardRenderSky(WorldRenderer worldRenderer, float tickDelta) {
        if (WaterShaderPipeline.shouldRenderSky()) {
            WaterShaderPipeline.onBeforeRenderSky();
            try {
                worldRenderer.renderSky(tickDelta);
            } finally {
                WaterShaderPipeline.onAfterRenderSky();
            }
        }
    }

    @Redirect(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;compileChunks(Lnet/minecraft/entity/LivingEntity;Z)Z"
            )
    )
    private boolean watershader$guardCompileChunks(WorldRenderer worldRenderer, LivingEntity camera, boolean force) {
        if (!WaterShaderPipeline.shouldUpdateTargetedOrCompileChunks()) {
            return false;
        }
        return worldRenderer.compileChunks(camera, force);
    }

    @Redirect(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;renderEntities(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/client/render/Culler;F)V"
            )
    )
    private void watershader$wrapRenderEntities(
            WorldRenderer worldRenderer,
            Vec3d pos,
            Culler culler,
            float tickDelta) {
        if (!WaterShaderPipeline.shouldRenderEntities()) {
            return;
        }
        boolean prevThirdPerson = this.client != null && this.client.options != null && this.client.options.thirdPerson;
        WaterShaderPipeline.onBeforeRenderEntities(this.client);
        try {
            worldRenderer.renderEntities(pos, culler, tickDelta);
        } finally {
            WaterShaderPipeline.onAfterRenderEntities(this.client, prevThirdPerson);
        }
    }

    @Redirect(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/particle/ParticleManager;renderLit(Lnet/minecraft/entity/Entity;F)V"
            )
    )
    private void watershader$guardRenderLitParticles(ParticleManager manager, Entity camera, float tickDelta) {
        if (WaterShaderPipeline.shouldRenderParticlesAndOverlays()) {
            manager.renderLit(camera, tickDelta);
        }
    }

    @Redirect(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/particle/ParticleManager;render(Lnet/minecraft/entity/Entity;F)V"
            )
    )
    private void watershader$guardRenderParticles(ParticleManager manager, Entity camera, float tickDelta) {
        if (WaterShaderPipeline.shouldRenderParticlesAndOverlays()) {
            manager.render(camera, tickDelta);
        }
    }

    @Redirect(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;renderMiningProgress(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/hit/HitResult;ILnet/minecraft/item/ItemStack;F)V"
            )
    )
    private void watershader$guardRenderMiningProgress(
            WorldRenderer worldRenderer,
            PlayerEntity player,
            HitResult hit,
            int mode,
            ItemStack item,
            float tickDelta) {
        if (WaterShaderPipeline.shouldRenderParticlesAndOverlays()) {
            worldRenderer.renderMiningProgress(player, hit, mode, item, tickDelta);
        }
    }

    @Redirect(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;renderBlockOutline(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/hit/HitResult;ILnet/minecraft/item/ItemStack;F)V"
            )
    )
    private void watershader$guardRenderBlockOutline(
            WorldRenderer worldRenderer,
            PlayerEntity player,
            HitResult hit,
            int mode,
            ItemStack item,
            float tickDelta) {
        if (WaterShaderPipeline.shouldRenderParticlesAndOverlays()) {
            worldRenderer.renderBlockOutline(player, hit, mode, item, tickDelta);
        }
    }

    @Redirect(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/entity/LivingEntity;ID)I"
            )
    )
    private int watershader$guardWorldRenderLayer(
            WorldRenderer worldRenderer,
            LivingEntity camera,
            int renderPass,
            double tickDelta) {
        if (renderPass == 1 && !WaterShaderPipeline.shouldRenderLayer1()) {
            return 0;
        }
        return worldRenderer.render(camera, renderPass, tickDelta);
    }

    @Redirect(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;renderLastChunks(ID)V"
            )
    )
    private void watershader$guardFancyLayer1LastChunks(
            WorldRenderer worldRenderer,
            int renderPass,
            double tickDelta) {
        if (renderPass == 1 && !WaterShaderPipeline.shouldRenderLayer1()) {
            return;
        }
        worldRenderer.renderLastChunks(renderPass, tickDelta);
    }

    @Inject(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL11;glDepthMask(Z)V",
                    shift = At.Shift.AFTER,
                    remap = false
            )
    )
    private void watershader$afterLayer1DepthMask(float tickDelta, long finishTimeNano, CallbackInfo ci) {
        WaterShaderPipeline.onAfterLayer1();
    }

    @Redirect(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/GameRenderer;renderSnow(F)V"
            )
    )
    private void watershader$guardRenderSnow(GameRenderer self, float tickDelta) {
        if (!WaterShaderPipeline.shouldRenderParticlesAndOverlays()) {
            return;
        }
        WaterShaderPipeline.renderWeatherIsolated(this.client, tickDelta, () -> this.renderSnow(tickDelta));
    }

    @Redirect(
            method = "renderSnow",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/biome/Biome;canRain()Z"
            )
    )
    private boolean watershader$replaceVanillaRain(Biome biome) {
        if (WaterShaderPipeline.shouldReplaceVanillaRain()) {
            return false;
        }
        return biome.canRain();
    }

    @Redirect(
            method = "renderSnow",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL11;glBlendFunc(II)V",
                    remap = false
            )
    )
    private void watershader$weatherSnowBlendFunc(int sfactor, int dfactor) {
        if (WaterShaderPipeline.isRenderingIsolatedWeather()) {
            WaterShaderPipeline.applyWeatherBlendFunc();
        } else {
            GL11.glBlendFunc(sfactor, dfactor);
        }
    }

    @Redirect(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;renderClouds(F)V"
            )
    )
    private void watershader$guardRenderClouds(WorldRenderer worldRenderer, float tickDelta) {
        if (WaterShaderPipeline.shouldRenderClouds()) {
            worldRenderer.renderClouds(tickDelta);
        }
    }

    @Redirect(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL11;glClear(I)V",
                    ordinal = 1,
                    remap = false
            )
    )
    private void watershader$guardHandDepthClear(int mask) {
        if (WaterShaderPipeline.shouldRenderFirstPersonHand()) {
            GL11.glClear(mask);
        }
    }

    @Redirect(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/GameRenderer;renderFirstPersonHand(FI)V"
            )
    )
    private void watershader$wrapFirstPersonHand(GameRenderer self, float tickDelta, int eye) {
        if (!WaterShaderPipeline.shouldRenderFirstPersonHand()) {
            return;
        }
        WaterShaderPipeline.onBeforeFirstPersonHand();
        try {
            this.renderFirstPersonHand(tickDelta, eye);
        } finally {
            WaterShaderPipeline.onAfterFirstPersonHand();
        }
    }
}
