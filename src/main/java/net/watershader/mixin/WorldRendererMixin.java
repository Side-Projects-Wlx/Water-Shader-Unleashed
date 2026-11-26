package net.watershader.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.watershader.render.WaterChunkAccess;
import net.watershader.render.WaterLayer1Mesh;
import net.watershader.render.WaterShaderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Binds pass shaders before {@code WorldRenderer.renderLastChunks} and draws captured Layer 1
 * geometry when Amphetamine replaces display lists. Runs before Amphetamine (priority 900).
 */
@Mixin(value = WorldRenderer.class, priority = 900)
final class WorldRendererMixin {
    @Shadow
    private Minecraft client;
    @Shadow
    private List chunksInCurrentLayer;
    @Shadow
    private ChunkBuilder[] chunks;

    @Inject(method = "renderLastChunks", at = @At("HEAD"), cancellable = true)
    private void watershader$beforeRenderLastChunks(int renderPass, double delta, CallbackInfo ci) {
        if (renderPass == 1 && this.chunksInCurrentLayer != null) {
            boolean foundWater = false;
            boolean foundSkyExposed = false;
            for (int i = 0, count = this.chunksInCurrentLayer.size(); i < count; i++) {
                Object entry = this.chunksInCurrentLayer.get(i);
                if (entry instanceof WaterChunkAccess access && access.watershader$hasWater()) {
                    foundWater = true;
                    if (access.watershader$hasSkyExposedWater()) {
                        foundSkyExposed = true;
                        break;
                    }
                }
            }
            if (foundWater) {
                WaterShaderPipeline.markWaterVisibleThisPass(foundSkyExposed);
            }
        }
        boolean customLayer1 = WaterShaderPipeline.onWorldRenderLastChunksBegin(renderPass);
        if (customLayer1) {
            try {
                watershader$drawCapturedLayer1(delta);
            } finally {
                WaterShaderPipeline.onWorldRenderLastChunksEnd(renderPass);
            }
            ci.cancel();
        }
    }

    @Redirect(
            method = "renderEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;render(Lnet/minecraft/entity/Entity;F)V"
            )
    )
    private void watershader$filterReflectedEntity(
            EntityRenderDispatcher dispatcher,
            Entity entity,
            float tickDelta) {
        if (WaterShaderPipeline.shouldRenderEntityInCurrentPass(entity, this.client)) {
            dispatcher.render(entity, tickDelta);
        }
    }

    @Inject(method = "releaseGlLists", at = @At("HEAD"))
    private void watershader$releaseLayer1Meshes(CallbackInfo ci) {
        if (this.chunks == null) {
            return;
        }
        for (ChunkBuilder chunk : this.chunks) {
            if (chunk instanceof WaterChunkAccess access) {
                access.watershader$releaseLayer1Mesh();
            }
        }
    }

    @Unique
    private void watershader$drawCapturedLayer1(double tickDelta) {
        if (this.client == null || this.client.camera == null
                || this.chunksInCurrentLayer == null || this.chunksInCurrentLayer.isEmpty()) {
            return;
        }
        LivingEntity camera = this.client.camera;
        double camX = camera.lastTickX + (camera.x - camera.lastTickX) * tickDelta;
        double camY = camera.lastTickY + (camera.y - camera.lastTickY) * tickDelta;
        double camZ = camera.lastTickZ + (camera.z - camera.lastTickZ) * tickDelta;

        WaterLayer1Mesh.beginDrawBatch();
        try {
            for (int i = 0, count = this.chunksInCurrentLayer.size(); i < count; i++) {
                Object entry = this.chunksInCurrentLayer.get(i);
                if (entry instanceof WaterChunkAccess access) {
                    WaterLayer1Mesh mesh = access.watershader$getLayer1Mesh();
                    if (mesh != null) {
                        mesh.draw(camX, camY, camZ);
                    }
                }
            }
        } finally {
            WaterLayer1Mesh.endDrawBatch();
        }
    }
}
