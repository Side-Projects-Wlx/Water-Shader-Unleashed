package net.watershader.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StencilNormalMathTest {

    @Test
    void packNormalPreventsSignExtensionAndPositiveOneOverflow() {
        int up = StencilNormalHelper.packNormal(0.0F, 1.0F, 0.0F);
        assertEquals(0, StencilNormalHelper.unpackNormalXByte(up));
        assertEquals(127, StencilNormalHelper.unpackNormalYByte(up));
        assertEquals(0, StencilNormalHelper.unpackNormalZByte(up));

        int down = StencilNormalHelper.packNormal(0.0F, -1.0F, 0.0F);
        assertEquals(0, StencilNormalHelper.unpackNormalXByte(down));
        assertEquals(-127, StencilNormalHelper.unpackNormalYByte(down));
        assertEquals(0, StencilNormalHelper.unpackNormalZByte(down));

        // Vanilla bug: +1.0F * 128 -> -128! Must pack cleanly as +127.
        int east = StencilNormalHelper.packNormal(1.0F, 0.0F, 0.0F);
        assertEquals(127, StencilNormalHelper.unpackNormalXByte(east));
        assertEquals(0, StencilNormalHelper.unpackNormalYByte(east));
        assertEquals(0, StencilNormalHelper.unpackNormalZByte(east));

        // Vanilla bug: negative X (-1.0F -> 0xFFFFFF81) overwrote Y/Z with 0xFF (-1).
        int westWithPositiveY = StencilNormalHelper.packNormal(-1.0F, 1.0F, 0.5F);
        assertEquals(-127, StencilNormalHelper.unpackNormalXByte(westWithPositiveY));
        assertEquals(127, StencilNormalHelper.unpackNormalYByte(westWithPositiveY));
        assertEquals(63, StencilNormalHelper.unpackNormalZByte(westWithPositiveY));
    }

    @Test
    void classifiesStillAndFlowingTopWaterVerticesAccurately() {
        // Beta 1.7.3 source water corner height is 1 - 1/9 = 0.8888889F at block Y = 63
        float stillHeight = 1.0F - 1.0F / 9.0F;
        assertEquals(-1.0F, StencilNormalHelper.classifyTopFluidVertex(63.0 + stillHeight, 63));
        assertEquals(-1.0F, StencilNormalHelper.classifyCornerHeight(stillHeight));

        // Water under another water block returns 1.0F (> 0.89) -> not top surface (0.0F)
        assertEquals(0.0F, StencilNormalHelper.classifyTopFluidVertex(63.0 + 1.0, 63));
        // Flowing water / waterfall edge (< 0.88) -> flowing/side (0.0F)
        assertEquals(0.0F, StencilNormalHelper.classifyTopFluidVertex(63.0 + 0.8333333, 63));
        assertEquals(0.0F, StencilNormalHelper.classifyTopFluidVertex(63.0 + 0.5, 63));
    }

    @Test
    void classifiesSmoothBoundaryRatiosAndVerticalInflowsConsistently() {
        double stillY = 63.0 + (1.0 - 1.0 / 9.0);
        // Full still water (4 still neighbors at Y) -> -1.0F
        assertEquals(-1.0F, StencilNormalHelper.classifyCornerNormalY(stillY, 63, 4, 0), 1e-6F);
        // Calm pond corner touching only still water & solid land -> -1.0F (baseline preserved)
        assertEquals(-1.0F, StencilNormalHelper.classifyCornerNormalY(stillY, 63, 2, 0), 1e-6F);
        assertEquals(-1.0F, StencilNormalHelper.classifyCornerNormalY(stillY, 63, 1, 0), 1e-6F);

        // Horizontal still-to-flowing boundary: both sides get identical still/(still+flowing) ratio
        assertEquals(-0.5F, StencilNormalHelper.classifyCornerNormalY(stillY, 63, 2, 2), 1e-6F);
        assertEquals(-0.5F, StencilNormalHelper.classifyCornerNormalY(stillY, 63, 1, 1), 1e-6F);
        assertEquals(-0.75F, StencilNormalHelper.classifyCornerNormalY(stillY, 63, 3, 1), 1e-6F);
        assertEquals(-1.0F / 3.0F, StencilNormalHelper.classifyCornerNormalY(stillY, 63, 1, 2), 1e-5F);

        // Pure flowing water or upper pouring stream (0 reflecting still neighbors at Y) -> 0.0F
        assertEquals(0.0F, StencilNormalHelper.classifyCornerNormalY(63.65, 63, 0, 4), 1e-6F);
        assertEquals(0.0F, StencilNormalHelper.classifyCornerNormalY(64.50, 64, 0, 1), 1e-6F);
        assertEquals(0.0F, StencilNormalHelper.classifyCornerNormalY(64.12, 64, 0, 1), 1e-6F);

        // Lower still-pool corner touching vertical inflow above (vy = 64.0 > blockY + 0.89) -> 0.0F,
        // matching the welded 0.0F bottom edge of the upper waterfall face with zero seam
        assertEquals(0.0F, StencilNormalHelper.classifyCornerNormalY(64.0, 63, 4, 0), 1e-6F);
        assertEquals(0.0F, StencilNormalHelper.classifyCornerNormalY(64.0, 63, 2, 2), 1e-6F);
    }

    @Test
    void selectsSymmetricWaterQuadDiagonalForSingleCornerInflowTransitions() {
        int still = StencilNormalHelper.packWaterNormal(1.0F, -1.0F);
        int flow = StencilNormalHelper.packWaterNormal(1.0F, 0.0F);

        // Uniform or straight-edge transitions keep default (0, 2) diagonal
        assertFalse(StencilNormalHelper.shouldFlipWaterQuadDiagonal(still, still, still, still));
        assertFalse(StencilNormalHelper.shouldFlipWaterQuadDiagonal(flow, flow, still, still));
        assertFalse(StencilNormalHelper.shouldFlipWaterQuadDiagonal(flow, still, still, flow));

        // Single inflow corner at v0 or v2 already lies on diagonal (0, 2) -> keep default
        assertFalse(StencilNormalHelper.shouldFlipWaterQuadDiagonal(flow, still, still, still));
        assertFalse(StencilNormalHelper.shouldFlipWaterQuadDiagonal(still, still, flow, still));

        // Single inflow corner at v1 or v3 requires diagonal (1, 3) for symmetric radial fade
        assertTrue(StencilNormalHelper.shouldFlipWaterQuadDiagonal(still, flow, still, still));
        assertTrue(StencilNormalHelper.shouldFlipWaterQuadDiagonal(still, still, still, flow));

        // Concave corner (3 flow + 1 still at v1 or v3) also flips to diagonal (1, 3)
        assertTrue(StencilNormalHelper.shouldFlipWaterQuadDiagonal(flow, still, flow, flow));
        assertTrue(StencilNormalHelper.shouldFlipWaterQuadDiagonal(flow, flow, flow, still));
    }

    @Test
    void packNormalEncodesIsolatedSingleBlockReflectionSuppressionInZ() {
        int isolatedOpen = StencilNormalHelper.packNormal(1.0F, -1.0F, 1.0F);
        assertEquals(127, StencilNormalHelper.unpackNormalXByte(isolatedOpen));
        assertEquals(-127, StencilNormalHelper.unpackNormalYByte(isolatedOpen));
        assertEquals(127, StencilNormalHelper.unpackNormalZByte(isolatedOpen));

        int isolatedCave = StencilNormalHelper.packNormal(0.0F, -1.0F, 1.0F);
        assertEquals(0, StencilNormalHelper.unpackNormalXByte(isolatedCave));
        assertEquals(-127, StencilNormalHelper.unpackNormalYByte(isolatedCave));
        assertEquals(127, StencilNormalHelper.unpackNormalZByte(isolatedCave));
    }

    @Test
    void packWaterNormalEncodesSkyExposureInXWithoutAlteringYOrZ() {
        int caveStill = StencilNormalHelper.packWaterNormal(0.0F, -1.0F);
        assertEquals(0, StencilNormalHelper.unpackNormalXByte(caveStill));
        assertEquals(-127, StencilNormalHelper.unpackNormalYByte(caveStill));
        assertEquals(0, StencilNormalHelper.unpackNormalZByte(caveStill));

        int openStill = StencilNormalHelper.packWaterNormal(1.0F, -1.0F);
        assertEquals(127, StencilNormalHelper.unpackNormalXByte(openStill));
        assertEquals(-127, StencilNormalHelper.unpackNormalYByte(openStill));
        assertEquals(0, StencilNormalHelper.unpackNormalZByte(openStill));

        int partialFlow = StencilNormalHelper.packWaterNormal(0.5F, 0.0F);
        assertEquals(63, StencilNormalHelper.unpackNormalXByte(partialFlow));
        assertEquals(0, StencilNormalHelper.unpackNormalYByte(partialFlow));
        assertEquals(0, StencilNormalHelper.unpackNormalZByte(partialFlow));

        // Clamps out-of-bounds sky exposure cleanly to [0, 1] -> [0, 127]
        int clampedLow = StencilNormalHelper.packWaterNormal(-2.0F, -1.0F);
        int clampedHigh = StencilNormalHelper.packWaterNormal(5.0F, -1.0F);
        assertEquals(0, StencilNormalHelper.unpackNormalXByte(clampedLow));
        assertEquals(127, StencilNormalHelper.unpackNormalXByte(clampedHigh));
    }

    @Test
    void resolvesTopAndSideFluidHeightsWatertightWithoutSolidPokeThroughOrFloatingWalls() {
        double lowerStillPlane = 63.0 + (8.0 / 9.0);

        // Open-still pool corner lifted to 64.0 by upper waterfall clamps to 63 + 8/9
        double clampedLowerCorner = StencilNormalHelper.resolveFluidTopVertexY(64.0, 63, true);
        assertEquals(lowerStillPlane, clampedLowerCorner);

        // Adjacent non-still block at y = 63 sharing that corner also clamps identically (zero tear)
        assertEquals(clampedLowerCorner, StencilNormalHelper.resolveFluidTopVertexY(64.0, 63, true));

        // Pure waterfall column corner with NO open-still block at y = 63 stays at 64.0
        assertEquals(64.0, StencilNormalHelper.resolveFluidTopVertexY(64.0, 63, false));

        // Flowing water top/side-top vertices at y = 64 are NEVER pulled below 64.0
        // (even for thin meta=7 streams at 64 + 1/36 or 64.22 over solid ledges)
        assertEquals(64.0 + 1.0 / 36.0, StencilNormalHelper.resolveFluidTopVertexY(64.0 + 1.0 / 36.0, 64, false));
        assertEquals(64.22, StencilNormalHelper.resolveFluidTopVertexY(64.22, 64, true));
        assertEquals(64.44, StencilNormalHelper.resolveFluidTopVertexY(64.44, 64, false));

        // Waterfall/pouring block at y = 64 sitting over water (y = 63) welds side-bottom bit-for-bit to clamped lower still pool
        double weldedSideBottom = StencilNormalHelper.resolveFluidSideBottomVertexY(64.0, 64, true, true);
        assertEquals(clampedLowerCorner, weldedSideBottom);

        // Water sitting on a solid dirt/grass ledge (!waterDirectlyBelow) keeps bottom at 64.0 (zero Z-fighting)
        assertEquals(64.0, StencilNormalHelper.resolveFluidSideBottomVertexY(64.0, 64, false, true));

        // Waterfall over water with no adjacent lower open-still pool keeps bottom at 64.0
        assertEquals(64.0, StencilNormalHelper.resolveFluidSideBottomVertexY(64.0, 64, true, false));
    }
}
