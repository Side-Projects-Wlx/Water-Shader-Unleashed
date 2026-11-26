package net.watershader.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.world.BlockView;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.watershader.math.WaterEnvironmentMath;
import net.watershader.render.StencilNormalHelper;
import net.watershader.render.WaterLayer1Capture;
import net.watershader.render.WaterShaderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Classifies Layer 1 block normals for the Pass 1 water stencil and Layer 0/1 transparency shader.
 * Works identically across vanilla and StationAPI Arsenic fluid rendering.
 */
@Mixin(BlockRenderManager.class)
final class BlockRenderManagerMixin {
    @Shadow
    private BlockView blockView;
    @Unique
    private int watershader$blockX;
    @Unique
    private int watershader$blockZ;
    @Unique
    private float watershader$waterSkyExposure = 1.0F;

    @Inject(method = "render(Lnet/minecraft/block/Block;III)Z", at = @At("HEAD"))
    private void watershader$beginBlock(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        if (!WaterLayer1Capture.isRebuildingChunk() || block == null) {
            return;
        }
        this.watershader$blockX = x;
        this.watershader$blockZ = z;
        boolean layer1 = block.getRenderLayer() == 1;
        boolean water = layer1 && block.material == Material.WATER && !WaterShaderPipeline.isBlockExcluded(block);
        this.watershader$waterSkyExposure = water ? watershader$sampleWaterSkyExposure(x, y, z) : 0.0F;
        WaterLayer1Capture.beginBlock(layer1, water, y);
        if (water && this.watershader$waterSkyExposure > 0.01F) {
            WaterLayer1Capture.markSkyExposedWater();
        }
        if (layer1) {
            Tessellator.INSTANCE.normal(0.0F, 1.0F, 0.0F);
        }
    }

    @Inject(method = "render(Lnet/minecraft/block/Block;III)Z", at = @At("RETURN"))
    private void watershader$endBlock(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        if (!WaterLayer1Capture.isRebuildingChunk()) {
            return;
        }
        if (WaterLayer1Capture.isActiveLayer1Block()) {
            Tessellator.INSTANCE.normal(0.0F, 1.0F, 0.0F);
        }
        WaterLayer1Capture.endBlock();
    }

    @Redirect(
            method = "renderFluid",
            at = {
                    @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Tessellator;vertex(DDDDD)V", ordinal = 0),
                    @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Tessellator;vertex(DDDDD)V", ordinal = 1),
                    @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Tessellator;vertex(DDDDD)V", ordinal = 2),
                    @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Tessellator;vertex(DDDDD)V", ordinal = 3)
            }
    )
    private void watershader$tagFluidTopVertex(
            Tessellator tess,
            double vx,
            double vy,
            double vz,
            double u,
            double v) {
        double emitVy = vy;
        if (WaterLayer1Capture.isActiveWaterBlock()) {
            int by = WaterLayer1Capture.getActiveWaterBlockY();
            float skyExp = watershader$sampleCornerSkyExposure(vx, vz, by);
            if (skyExp > 0.01F) {
                WaterLayer1Capture.markSkyExposedWater();
            }
            int bx = this.watershader$blockX;
            int bz = this.watershader$blockZ;
            boolean openStill = watershader$isOpenStillWater(bx, by, bz);
            if (vy > (double) by + StencilNormalHelper.UPPER_CONTACT_CLAMP_THRESHOLD) {
                boolean cornerOpenStill = openStill || watershader$cornerHasOpenStillAtY(vx, vz, by);
                emitVy = StencilNormalHelper.resolveFluidTopVertexY(vy, by, cornerOpenStill);
            }
            if (openStill && !watershader$hasAdjacentOpenStillWater(bx, by, bz)) {
                boolean touchesUpperWater = vy > (double) by + StencilNormalHelper.UPPER_CONTACT_CLAMP_THRESHOLD;
                if (!watershader$hasAdjacentWater(bx, by, bz) && !touchesUpperWater) {
                    // Isolated 1x1 water block (no adjacent water): preserve calm transparency, suppress reflection via nz = +1
                    tess.normal(skyExp, -1.0F, 1.0F);
                } else {
                    // Single 1x1 source pouring into flowing water (or touching upper inflow corner): blend as flowing water
                    tess.normal(skyExp, 0.0F, 0.0F);
                }
            } else {
                float ny = watershader$classifyCorner(vx, vy, vz, by);
                tess.normal(skyExp, ny, 0.0F);
            }
        }
        tess.vertex(vx, emitVy, vz, u, v);
    }

    @Unique
    private boolean watershader$isOpenStillWater(int bx, int y, int bz) {
        if (this.blockView == null || y < 0 || y > 127) {
            return false;
        }
        return this.blockView.getMaterial(bx, y, bz) == Material.WATER
                && this.blockView.getBlockMeta(bx, y, bz) == 0
                && (y >= 127 || this.blockView.getMaterial(bx, y + 1, bz) != Material.WATER);
    }

    @Unique
    private boolean watershader$hasAdjacentOpenStillWater(int bx, int y, int bz) {
        return watershader$isOpenStillWater(bx + 1, y, bz)
                || watershader$isOpenStillWater(bx - 1, y, bz)
                || watershader$isOpenStillWater(bx, y, bz + 1)
                || watershader$isOpenStillWater(bx, y, bz - 1);
    }

    @Unique
    private boolean watershader$hasAdjacentWater(int bx, int y, int bz) {
        if (this.blockView == null || y < 0 || y > 127) {
            return false;
        }
        return this.blockView.getMaterial(bx + 1, y, bz) == Material.WATER
                || this.blockView.getMaterial(bx - 1, y, bz) == Material.WATER
                || this.blockView.getMaterial(bx, y, bz + 1) == Material.WATER
                || this.blockView.getMaterial(bx, y, bz - 1) == Material.WATER;
    }

    @Unique
    private boolean watershader$isReflectingStillWater(int bx, int y, int bz) {
        return watershader$isOpenStillWater(bx, y, bz)
                && watershader$hasAdjacentOpenStillWater(bx, y, bz);
    }

    @Unique
    private boolean watershader$isFlowingOrNonIsolatedWater(int bx, int y, int bz) {
        if (this.blockView == null || y < 0 || y > 127) {
            return false;
        }
        if (this.blockView.getMaterial(bx, y, bz) != Material.WATER) {
            return false;
        }
        if (watershader$isReflectingStillWater(bx, y, bz)) {
            return false;
        }
        // An isolated 1x1 puddle surrounded by non-water should never dampen a diagonally touching pond
        return !watershader$isOpenStillWater(bx, y, bz) || watershader$hasAdjacentWater(bx, y, bz);
    }

    @Unique
    private boolean watershader$cornerHasOpenStillAtY(double vx, double vz, int y) {
        if (this.blockView == null || y < 0 || y > 127) {
            return false;
        }
        int cx = (int) Math.round(vx);
        int cz = (int) Math.round(vz);
        for (int dx = -1; dx <= 0; dx++) {
            int bx = cx + dx;
            for (int dz = -1; dz <= 0; dz++) {
                int bz = cz + dz;
                if (watershader$isOpenStillWater(bx, y, bz)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Unique
    private float watershader$classifyCorner(double vx, double vy, double vz, int by) {
        if (this.blockView == null) {
            return StencilNormalHelper.classifyTopFluidVertex(vy, by);
        }
        int cx = (int) Math.round(vx);
        int cz = (int) Math.round(vz);
        int reflectingStillAtY = 0;
        int flowingWaterAtY = 0;
        for (int dx = -1; dx <= 0; dx++) {
            int bx = cx + dx;
            for (int dz = -1; dz <= 0; dz++) {
                int bz = cz + dz;
                if (watershader$isReflectingStillWater(bx, by, bz)) {
                    reflectingStillAtY++;
                } else if (watershader$isFlowingOrNonIsolatedWater(bx, by, bz)) {
                    flowingWaterAtY++;
                }
            }
        }
        return StencilNormalHelper.classifyCornerNormalY(vy, by, reflectingStillAtY, flowingWaterAtY);
    }

    @Inject(
            method = "renderFluid",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/block/BlockRenderManager;renderBottomFace(Lnet/minecraft/block/Block;DDDI)V"
            )
    )
    private void watershader$tagFluidBottomFace(CallbackInfoReturnable<Boolean> cir) {
        if (WaterLayer1Capture.isActiveWaterBlock()) {
            Tessellator.INSTANCE.normal(this.watershader$waterSkyExposure, 0.0F, 0.0F);
        }
    }

    @Redirect(
            method = "renderFluid",
            at = {
                    @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Tessellator;vertex(DDDDD)V", ordinal = 4),
                    @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Tessellator;vertex(DDDDD)V", ordinal = 5)
            }
    )
    private void watershader$tagFluidSideTopVertex(
            Tessellator tess,
            double vx,
            double vy,
            double vz,
            double u,
            double v) {
        double emitVy = vy;
        if (WaterLayer1Capture.isActiveWaterBlock()) {
            int by = WaterLayer1Capture.getActiveWaterBlockY();
            if (vy > (double) by + StencilNormalHelper.UPPER_CONTACT_CLAMP_THRESHOLD) {
                boolean cornerOpenStill = watershader$isOpenStillWater(this.watershader$blockX, by, this.watershader$blockZ)
                        || watershader$cornerHasOpenStillAtY(vx, vz, by);
                emitVy = StencilNormalHelper.resolveFluidTopVertexY(vy, by, cornerOpenStill);
            }
            tess.normal(watershader$sampleCornerSkyExposure(vx, vz, by), 0.0F, 0.0F);
        }
        tess.vertex(vx, emitVy, vz, u, v);
    }

    @Redirect(
            method = "renderFluid",
            at = {
                    @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Tessellator;vertex(DDDDD)V", ordinal = 6),
                    @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Tessellator;vertex(DDDDD)V", ordinal = 7)
            }
    )
    private void watershader$tagFluidSideBottomVertex(
            Tessellator tess,
            double vx,
            double vy,
            double vz,
            double u,
            double v) {
        double emitVy = vy;
        if (WaterLayer1Capture.isActiveWaterBlock()) {
            int by = WaterLayer1Capture.getActiveWaterBlockY();
            boolean waterBelow = this.blockView != null && by > 0
                    && this.blockView.getMaterial(this.watershader$blockX, by - 1, this.watershader$blockZ) == Material.WATER;
            boolean lowerOpenStill = waterBelow && watershader$cornerHasOpenStillAtY(vx, vz, by - 1);
            emitVy = StencilNormalHelper.resolveFluidSideBottomVertexY(vy, by, waterBelow, lowerOpenStill);
            tess.normal(this.watershader$waterSkyExposure, 0.0F, 0.0F);
        }
        tess.vertex(vx, emitVy, vz, u, v);
    }

    @Unique
    private World watershader$resolveWorld() {
        if (this.blockView instanceof World w) {
            return w;
        }
        if (this.blockView instanceof WorldRegionAccessor region) {
            return region.watershader$getWorld();
        }
        return null;
    }

    @Unique
    private float watershader$sampleWaterSkyExposure(int x, int y, int z) {
        World world = watershader$resolveWorld();
        if (world == null) {
            return 1.0F;
        }
        if (world.dimension != null && (world.dimension.isNether || world.dimension.hasCeiling)) {
            return 0.0F;
        }
        return watershader$sampleBlockSkyExposure(world, x, Math.max(0, Math.min(127, y)), z);
    }

    @Unique
    private float watershader$sampleCornerSkyExposure(double vx, double vz, int by) {
        World world = watershader$resolveWorld();
        if (world == null) {
            return 1.0F;
        }
        if (world.dimension != null && (world.dimension.isNether || world.dimension.hasCeiling)) {
            return 0.0F;
        }
        int cx = (int) Math.round(vx);
        int cz = (int) Math.round(vz);
        int clampedY = Math.max(0, Math.min(127, by));
        float sum = 0.0F;
        int count = 0;
        for (int dx = -1; dx <= 0; dx++) {
            int bx = cx + dx;
            for (int dz = -1; dz <= 0; dz++) {
                int bz = cz + dz;
                if (this.blockView != null && this.blockView.getMaterial(bx, by, bz) == Material.WATER) {
                    sum += watershader$sampleBlockSkyExposure(world, bx, clampedY, bz);
                    count++;
                }
            }
        }
        return count > 0 ? (sum / (float) count) : this.watershader$waterSkyExposure;
    }

    /**
     * Queries effective sky exposure for a water block, also inspecting adjacent non-opaque blocks
     * so tall sunlit outdoor waterfalls (whose internal column skylight attenuates -3 per block)
     * do not falsely drop into dark cave-water shading.
     */
    @Unique
    private static float watershader$sampleBlockSkyExposure(World world, int x, int clampedY, int z) {
        int maxSky = (clampedY < 127) ? world.getBrightness(LightType.SKY, x, clampedY + 1, z) : 15;
        if (maxSky >= 15) {
            return 1.0F;
        }
        int skyAt = world.getBrightness(LightType.SKY, x, clampedY, z);
        if (skyAt > maxSky) {
            maxSky = skyAt;
        }
        if (maxSky < 15 && !WaterShaderPipeline.isOpaqueSolid(world, x + 1, clampedY, z)) {
            maxSky = Math.max(maxSky, world.getBrightness(LightType.SKY, x + 1, clampedY, z));
        }
        if (maxSky < 15 && !WaterShaderPipeline.isOpaqueSolid(world, x - 1, clampedY, z)) {
            maxSky = Math.max(maxSky, world.getBrightness(LightType.SKY, x - 1, clampedY, z));
        }
        if (maxSky < 15 && !WaterShaderPipeline.isOpaqueSolid(world, x, clampedY, z + 1)) {
            maxSky = Math.max(maxSky, world.getBrightness(LightType.SKY, x, clampedY, z + 1));
        }
        if (maxSky < 15 && !WaterShaderPipeline.isOpaqueSolid(world, x, clampedY, z - 1)) {
            maxSky = Math.max(maxSky, world.getBrightness(LightType.SKY, x, clampedY, z - 1));
        }
        return WaterEnvironmentMath.normalizeSkyLight(maxSky);
    }
}
