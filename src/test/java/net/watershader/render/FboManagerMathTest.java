package net.watershader.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FboManagerMathTest {

    @Test
    void computesSupersampledColorAndDividedReflectionSizes() {
        FboManager.FboDimensions dims = FboManager.computeDimensions(1920, 1080, 2, 4, 16384);
        assertEquals(3840, dims.colorWidth());
        assertEquals(2160, dims.colorHeight());
        assertEquals(480, dims.reflectionWidth());
        assertEquals(270, dims.reflectionHeight());
    }

    @Test
    void clampsInvalidDivisorsAndMaxTextureLimitsPreservingAspectRatio() {
        FboManager.FboDimensions clamped = FboManager.computeDimensions(1920, 1080, 0, -2, 16384);
        assertEquals(1920, clamped.colorWidth());
        assertEquals(1080, clamped.colorHeight());
        assertEquals(1920, clamped.reflectionWidth());
        assertEquals(1080, clamped.reflectionHeight());

        FboManager.FboDimensions capped = FboManager.computeDimensions(2000, 1000, 4, 8, 4096);
        assertEquals(4096, capped.colorWidth());
        assertEquals(2048, capped.colorHeight());
        assertEquals(250, capped.reflectionWidth());
        assertEquals(125, capped.reflectionHeight());

        FboManager.FboDimensions extremeDiv = FboManager.computeDimensions(640, 480, 1, 9999, 16384);
        assertEquals(1, extremeDiv.reflectionWidth());
        assertEquals(1, extremeDiv.reflectionHeight());
    }

    @Test
    void needsRecreateTriggersOnAnySingleChangedDimensionOrMode() {
        assertTrue(FboManager.needsRecreate(false, 1280, 720, 2, 4, true, 1280, 720, 2, 4, true));
        assertFalse(FboManager.needsRecreate(true, 1280, 720, 2, 4, true, 1280, 720, 2, 4, true));

        // Width-only change MUST trigger recreate (unlike original v4c's && bug)
        assertTrue(FboManager.needsRecreate(true, 1280, 720, 2, 4, true, 1281, 720, 2, 4, true));
        // Height-only change
        assertTrue(FboManager.needsRecreate(true, 1280, 720, 2, 4, true, 1280, 721, 2, 4, true));
        // AA change
        assertTrue(FboManager.needsRecreate(true, 1280, 720, 2, 4, true, 1280, 720, 1, 4, true));
        // Reflection divisor change
        assertTrue(FboManager.needsRecreate(true, 1280, 720, 2, 4, true, 1280, 720, 2, 2, true));
        // Mode toggle (water_mode 2 <-> 1)
        assertTrue(FboManager.needsRecreate(true, 1280, 720, 2, 4, true, 1280, 720, 2, 4, false));
    }

    @Test
    void validatesDepthCopyTargetsBeforeCopying() {
        assertTrue(FboManager.isDepthCopyValid(true, 7, 1920, 1080));
        assertFalse(FboManager.isDepthCopyValid(false, 7, 1920, 1080));
        assertFalse(FboManager.isDepthCopyValid(true, 0, 1920, 1080));
        assertFalse(FboManager.isDepthCopyValid(true, 7, 0, 1080));
        assertFalse(FboManager.isDepthCopyValid(true, 7, 1920, -1));
    }

    @Test
    void stencilResolutionModeComputesMatchedOrNative1xDimensionsAndTriggersRecreate() {
        FboManager.FboDimensions matched = FboManager.computeDimensions(1920, 1080, 2, 1, false, 16384);
        assertEquals(3840, matched.stencilWidth());
        assertEquals(2160, matched.stencilHeight());

        FboManager.FboDimensions native1x = FboManager.computeDimensions(1920, 1080, 2, 1, true, 16384);
        assertEquals(3840, native1x.colorWidth());
        assertEquals(2160, native1x.colorHeight());
        assertEquals(1920, native1x.stencilWidth());
        assertEquals(1080, native1x.stencilHeight());

        assertFalse(FboManager.needsRecreate(true, 1920, 1080, 2, 1, true, false, 1920, 1080, 2, 1, true, false));
        assertTrue(FboManager.needsRecreate(true, 1920, 1080, 2, 1, true, false, 1920, 1080, 2, 1, true, true));
    }
}
