package net.watershader.mixin;

import net.minecraft.client.render.chunk.ChunkBuilder;
import net.watershader.render.WaterChunkAccess;
import net.watershader.render.WaterLayer1Capture;
import net.watershader.render.WaterLayer1Mesh;
import net.watershader.render.WaterShaderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Manages Layer 1 translucent geometry capture lifetime on each {@link ChunkBuilder}.
 */
@Mixin(ChunkBuilder.class)
final class ChunkBuilderMixin implements WaterChunkAccess {
    @Shadow
    public int cameraOffsetX;
    @Shadow
    public int cameraOffsetY;
    @Shadow
    public int cameraOffsetZ;
    @Shadow
    public boolean dirty;

    @Unique
    private WaterLayer1Mesh watershader$layer1Mesh;

    @Unique
    private boolean watershader$hasWater = false;

    @Unique
    private boolean watershader$hasSkyExposedWater = false;

    @Override
    public WaterLayer1Mesh watershader$getLayer1Mesh() {
        return this.watershader$layer1Mesh;
    }

    @Override
    public void watershader$setLayer1Mesh(WaterLayer1Mesh mesh) {
        this.watershader$layer1Mesh = mesh;
    }

    @Override
    public void watershader$releaseLayer1Mesh() {
        if (this.watershader$layer1Mesh != null) {
            this.watershader$layer1Mesh.close();
            this.watershader$layer1Mesh = null;
        }
    }

    @Override
    public boolean watershader$hasWater() {
        return this.watershader$hasWater;
    }

    @Override
    public void watershader$setHasWater(boolean hasWater) {
        this.watershader$hasWater = hasWater;
    }

    @Override
    public boolean watershader$hasSkyExposedWater() {
        return this.watershader$hasSkyExposedWater;
    }

    @Override
    public void watershader$setHasSkyExposedWater(boolean hasSkyExposedWater) {
        this.watershader$hasSkyExposedWater = hasSkyExposedWater;
    }

    @Inject(method = "rebuild", at = @At("HEAD"))
    private void watershader$beginRebuild(CallbackInfo ci) {
        if (!this.dirty) {
            return;
        }
        this.watershader$releaseLayer1Mesh();
        if (!WaterShaderPipeline.isConfiguredEnabled()) {
            this.watershader$hasWater = false;
            this.watershader$hasSkyExposedWater = false;
            return;
        }
        WaterLayer1Capture.beginChunkRebuild(
                WaterShaderPipeline.isAmphetamineDetected(),
                this.cameraOffsetX,
                this.cameraOffsetY,
                this.cameraOffsetZ);
    }

    @Inject(method = "rebuild", at = @At("RETURN"))
    private void watershader$endRebuild(CallbackInfo ci) {
        if (WaterLayer1Capture.isRebuildingChunk()) {
            this.watershader$hasWater = WaterLayer1Capture.didChunkHaveWater();
            this.watershader$hasSkyExposedWater = WaterLayer1Capture.didChunkHaveSkyExposedWater();
            this.watershader$layer1Mesh = WaterLayer1Capture.endChunkRebuild();
        }
    }

    @Inject(method = "reset", at = @At("HEAD"))
    private void watershader$onReset(CallbackInfo ci) {
        this.watershader$releaseLayer1Mesh();
        this.watershader$hasWater = false;
        this.watershader$hasSkyExposedWater = false;
    }
}
