package net.watershader.math;

import net.watershader.config.BiomeTintMode;
import net.watershader.render.ModernWeatherRenderer;
import net.watershader.render.WaterShaderPipeline;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WaterEnvironmentMathTest {

    @Test
    void reflectionBrightnessAndTransparencySkyLightRespondToRainAndCeilingOnly() {
        // Sky ambient light reflects day/night for 6.4 underwater brightness & subtle night rain sheen
        assertEquals(1.0F, WaterEnvironmentMath.computeSkyAmbientLight(0.0F), 1e-5F);
        assertEquals(0.5F, WaterEnvironmentMath.computeSkyAmbientLight(0.25F), 1e-5F);
        assertEquals(0.0F, WaterEnvironmentMath.computeSkyAmbientLight(0.5F), 1e-5F);
        assertEquals(0.5F, WaterEnvironmentMath.computeSkyAmbientLight(0.75F), 1e-5F);

        // Reflection brightness scales ONLY with rain/thunder & ceiling (never dimmed by night/timeOfDay)
        float clear = WaterEnvironmentMath.computeReflectionBrightness(0.0F, 0.0F, false);
        float rain = WaterEnvironmentMath.computeReflectionBrightness(1.0F, 0.0F, false);
        float storm = WaterEnvironmentMath.computeReflectionBrightness(1.0F, 1.0F, false);
        float nether = WaterEnvironmentMath.computeReflectionBrightness(0.0F, 0.0F, true);

        assertEquals(1.0F, clear, 1e-5F);
        assertEquals(0.80F, rain, 1e-5F);
        assertEquals(0.72F, storm, 1e-5F);
        assertEquals(0.0F, nether, 1e-6F);

        assertEquals(1.0F, WaterEnvironmentMath.computeTransparencySkyLight(0.0F, 0.0F, false), 1e-5F);
        assertEquals(0.0F, WaterEnvironmentMath.computeTransparencySkyLight(0.0F, 0.0F, true), 1e-6F);
        float rainLift = WaterEnvironmentMath.computeTransparencySkyLight(1.0F, 0.0F, false);
        assertEquals(0.88F, rainLift, 1e-5F);
    }

    @Test
    void reflectionSkyRenderAndSkyLightNormalizationGateCavesCorrectly() {
        assertTrue(WaterEnvironmentMath.shouldRenderReflectionSky(false, 15));
        assertTrue(WaterEnvironmentMath.shouldRenderReflectionSky(false, 1));
        assertFalse(WaterEnvironmentMath.shouldRenderReflectionSky(false, 0));
        assertFalse(WaterEnvironmentMath.shouldRenderReflectionSky(true, 15));

        assertEquals(0.0F, WaterEnvironmentMath.normalizeSkyLight(-5), 1e-6F);
        assertEquals(0.0F, WaterEnvironmentMath.normalizeSkyLight(0), 1e-6F);
        assertEquals(1.0F, WaterEnvironmentMath.normalizeSkyLight(15), 1e-6F);
        assertEquals(1.0F, WaterEnvironmentMath.normalizeSkyLight(20), 1e-6F);
        assertEquals(8.0F / 15.0F, WaterEnvironmentMath.normalizeSkyLight(8), 1e-5F);
    }

    @Test
    void depthLinearizationMapsNearAndFarCleanly() {
        float n = 0.05F;
        float f = 256.0F;
        assertEquals(n, WaterEnvironmentMath.linearizeDepth(0.0F, n, f), 1e-4F);
        assertEquals(f, WaterEnvironmentMath.linearizeDepth(1.0F, n, f), 1e-2F);

        // Mid-distance check: z_eye = -10 -> z_ndc = (f+n - 2fn/10)/(f-n) -> depth = (z_ndc+1)/2
        float targetDist = 10.0F;
        float zNdc = (f + n - (2.0F * f * n) / targetDist) / (f - n);
        float rawDepth = 0.5F * (zNdc + 1.0F);
        assertEquals(targetDist, WaterEnvironmentMath.linearizeDepth(rawDepth, n, f), 1e-2F);
    }

    @Test
    void modernRainStreakTextureHasSoftCenterFilamentAndTransparentEdges() {
        int w = ModernWeatherRenderer.STREAK_TEX_WIDTH;
        int h = ModernWeatherRenderer.STREAK_TEX_HEIGHT;
        byte[] rgba = ModernWeatherRenderer.generateRainStreakRgba(w, h);
        assertEquals(w * h * 4, rgba.length);

        // Left/right border columns must stay completely transparent (zero quad-edge clipping)
        for (int y = 0; y < h; y++) {
            int leftAlpha = rgba[(y * w) * 4 + 3] & 0xFF;
            int rightAlpha = rgba[(y * w + (w - 1)) * 4 + 3] & 0xFF;
            assertEquals(0, leftAlpha);
            assertEquals(0, rightAlpha);
        }

        // Top and bottom rows fade smoothly; center filament near ~80% height has strong peak alpha
        int centerX = w / 2;
        int topEdgeAlpha = rgba[(centerX) * 4 + 3] & 0xFF;
        int bottomEdgeAlpha = rgba[((h - 1) * w + centerX) * 4 + 3] & 0xFF;
        int tipCoreAlpha = rgba[((int) (h * 0.80) * w + centerX) * 4 + 3] & 0xFF;
        assertTrue(topEdgeAlpha < 16);
        assertTrue(bottomEdgeAlpha < 32);
        assertTrue(tipCoreAlpha > 200);
    }

    @Test
    void modernSplashAtlasUsesCrispSquareVariantsAndWaterHarmonizedTint() {
        int w = ModernWeatherRenderer.SPLASH_ATLAS_WIDTH;
        int h = ModernWeatherRenderer.SPLASH_ATLAS_HEIGHT;
        byte[] rgba = ModernWeatherRenderer.generateSplashAtlasRgba(w, h);
        assertEquals(w * h * 4, rgba.length);

        int tileW = w / ModernWeatherRenderer.SPLASH_TILE_COUNT;
        int rowH = h / 2;
        for (int tile = 0; tile < ModernWeatherRenderer.SPLASH_TILE_COUNT; tile++) {
            int splashPixels = 0;
            int bubblePixels = 0;
            for (int y = 0; y < rowH; y++) {
                for (int x = 0; x < tileW; x++) {
                    int splashIdx = (y * w + tile * tileW + x) * 4;
                    if ((rgba[splashIdx + 3] & 0xFF) > 0) {
                        splashPixels++;
                        assertTrue((rgba[splashIdx] & 0xFF) >= 180 && (rgba[splashIdx + 2] & 0xFF) >= 200);
                    }
                    int bubbleIdx = ((rowH + y) * w + tile * tileW + x) * 4;
                    if ((rgba[bubbleIdx + 3] & 0xFF) > 0) {
                        bubblePixels++;
                        assertTrue((rgba[bubbleIdx] & 0xFF) >= 180 && (rgba[bubbleIdx + 2] & 0xFF) >= 200);
                    }
                }
            }
            assertTrue(splashPixels >= 2 && splashPixels <= 4,
                    "Splash tile should hold 2-4 crisp pixel squares (tile " + tile + " had " + splashPixels + ")");
            assertTrue(bubblePixels >= 5 && bubblePixels <= 10,
                    "Bubble tile should hold a compact hollow ring/pair silhouette (tile " + tile + " had " + bubblePixels + ")");
        }

        float[] rainRgb = new float[3];
        float[] swimRgb = new float[3];
        float[] bubbleRgb = new float[3];
        float[] defaultWater = {0.0F, 0.5F, 0.35F};
        ModernWeatherRenderer.computeSplashTintRgb(defaultWater, 1.0F, 0.0F, false, rainRgb);
        ModernWeatherRenderer.computeSplashTintRgb(defaultWater, 1.0F, 0.0F, true, swimRgb);
        ModernWeatherRenderer.computeBubbleTintRgb(defaultWater, 1.0F, 0.0F, bubbleRgb);

        // Water-harmonized aqua-teal tint (green/blue balanced, never cobalt blue R~0, B~1)
        assertTrue(rainRgb[0] >= 0.45F && rainRgb[0] <= 0.72F);
        assertTrue(rainRgb[1] > rainRgb[0] && rainRgb[2] > rainRgb[0]);
        assertTrue(swimRgb[1] > rainRgb[1] * 0.95F);
        // Bubbles match the water family while staying slightly lighter/airier than rain splashes
        assertTrue(bubbleRgb[2] > rainRgb[2] && bubbleRgb[1] > rainRgb[1]);
        assertTrue(bubbleRgb[1] > bubbleRgb[0]);

        float[] darkRgb = new float[3];
        ModernWeatherRenderer.computeSplashTintRgb(defaultWater, 0.0F, 0.0F, false, darkRgb);
        assertTrue(darkRgb[1] < rainRgb[1] * 0.5F);

        // Extended outer procedural splash range reaches 36 blocks with smooth near/far cross-fades
        assertEquals(0.0F, ModernWeatherRenderer.computeOuterSplashDistanceFade(5.0, 5.5, 9.5, 18.0, 36.0), 1e-5F);
        assertEquals(1.0F, ModernWeatherRenderer.computeOuterSplashDistanceFade(14.0, 5.5, 9.5, 18.0, 36.0), 1e-5F);
        float outerMidFade = ModernWeatherRenderer.computeOuterSplashDistanceFade(27.0, 5.5, 9.5, 18.0, 36.0);
        assertTrue(outerMidFade > 0.40F && outerMidFade < 0.60F);
        assertEquals(0.0F, ModernWeatherRenderer.computeOuterSplashDistanceFade(36.1, 5.5, 9.5, 18.0, 36.0), 1e-6F);
        assertEquals(1.0, ModernWeatherRenderer.computeSplashPerspectiveScale(4.0), 1e-6);
        assertTrue(ModernWeatherRenderer.computeSplashPerspectiveScale(26.0) > 2.0);

        // Behind-camera half-space culling keeps near surround (<=4 blocks) & forward hemisphere, culls far behind
        assertFalse(ModernWeatherRenderer.isBehindCameraHalfSpace(0.0, -3.0, 0.0, 1.0, 2.5));
        assertFalse(ModernWeatherRenderer.isBehindCameraHalfSpace(0.0, 10.0, 0.0, 1.0, 2.5));
        assertTrue(ModernWeatherRenderer.isBehindCameraHalfSpace(0.0, -8.0, 0.0, 1.0, 2.5));
        assertFalse(ModernWeatherRenderer.isBehindCameraHalfSpace(0.0, -5.0, 0.0, 1.0, 6.5));
        assertTrue(ModernWeatherRenderer.isBehindCameraHalfSpace(0.0, -10.0, 0.0, 1.0, 6.5));
    }

    @Test
    void underwaterTransitionAndBrightnessBehaveSmoothly() {
        float v = 0.0F;
        for (int i = 0; i < 40; i++) {
            v = WaterEnvironmentMath.stepUnderwaterFactor(v, true, 0.016);
        }
        assertTrue(v > 0.99F);

        for (int i = 0; i < 60; i++) {
            v = WaterEnvironmentMath.stepUnderwaterFactor(v, false, 0.016);
        }
        assertEquals(0.0F, v, 1e-5F);

        float openDay = WaterEnvironmentMath.computeUnderwaterBrightness(1.0F, 0.0F, 15, false);
        float caveUw = WaterEnvironmentMath.computeUnderwaterBrightness(1.0F, 0.0F, 0, false);
        assertEquals(1.0F, openDay, 1e-5F);
        assertTrue(caveUw >= 0.12F && caveUw <= 0.35F);
    }

    @Test
    void cameraInWaterUniformSeparatesSubmersionFromUnderwaterEffectToggle() {
        assertEquals(1.0F, WaterEnvironmentMath.computeCameraInWaterUniform(true), 1e-6F);
        assertEquals(0.0F, WaterEnvironmentMath.computeCameraInWaterUniform(false), 1e-6F);

        // With underwater_effect=0, physical submersion remains 1.0 while effect intensity is 0.0
        assertEquals(0.0F, WaterEnvironmentMath.computeUnderwaterEffectUniform(false, 1.0F), 1e-6F);
        assertEquals(0.75F, WaterEnvironmentMath.computeUnderwaterEffectUniform(true, 0.75F), 1e-6F);
        assertEquals(1.0F, WaterEnvironmentMath.computeUnderwaterEffectUniform(true, 1.5F), 1e-6F);
        assertEquals(0.0F, WaterEnvironmentMath.computeUnderwaterEffectUniform(true, Float.NaN), 1e-6F);
    }

    @Test
    void handDepthDetectionAndFogDistancePreventOverFoggingHeldItems() {
        assertTrue(WaterEnvironmentMath.isFirstPersonHandDepth(0.8334F));
        assertFalse(WaterEnvironmentMath.isFirstPersonHandDepth(1.0F));
        assertFalse(WaterEnvironmentMath.isFirstPersonHandDepth(0.99995F));
        assertFalse(WaterEnvironmentMath.isFirstPersonHandDepth(Float.NaN));

        // Hand pixel over 48m+ background sky/trench stays pinned to ~0.3m instead of 48m
        float handDist = WaterEnvironmentMath.computeUnderwaterFogDistance(1.0F, 0.8334F, 0.05F, 512.0F);
        assertEquals(0.3F, handDist, 1e-5F);

        // Background pixel (handDepth == 1.0) uses waterDepth clamped to [0, 48]
        float skyDist = WaterEnvironmentMath.computeUnderwaterFogDistance(1.0F, 1.0F, 0.05F, 512.0F);
        assertEquals(48.0F, skyDist, 1e-5F);

        float nearTarget = 4.0F;
        float zNdc = (512.05F - (2.0F * 512.0F * 0.05F) / nearTarget) / 511.95F;
        float raw = 0.5F * (zNdc + 1.0F);
        float nearDist = WaterEnvironmentMath.computeUnderwaterFogDistance(raw, 1.0F, 0.05F, 512.0F);
        assertEquals(nearTarget, nearDist, 1e-2F);
    }

    @Test
    void thirdPersonCameraOffsetAndFluidSurfaceCheckMatchBeta173() {
        double[] off = new double[3];

        // Yaw=0 looks toward +Z, so camera sits behind at -Z (baseZ - (+dist))
        WaterEnvironmentMath.computeThirdPersonOffset(0.0F, 0.0F, 4.0, off);
        assertEquals(0.0, off[0], 1e-5);
        assertEquals(0.0, off[1], 1e-5);
        assertEquals(4.0, off[2], 1e-5);

        // Yaw=90 looks toward -X, so camera sits behind at +X (baseX - (-dist))
        WaterEnvironmentMath.computeThirdPersonOffset(90.0F, 0.0F, 4.0, off);
        assertEquals(-4.0, off[0], 1e-5);
        assertEquals(0.0, off[1], 1e-5);
        assertEquals(0.0, off[2], 1e-5);

        // Pitch=+30 looks downward, so camera rises above player (baseY - (-sin(30)*dist))
        WaterEnvironmentMath.computeThirdPersonOffset(0.0F, 30.0F, 4.0, off);
        assertTrue(off[1] < -1.9);

        // Pitch=-30 looks upward, so camera drops below player (baseY - (+sin(30)*dist))
        WaterEnvironmentMath.computeThirdPersonOffset(0.0F, -30.0F, 4.0, off);
        assertTrue(off[1] > 1.9);

        // Fluid surface height: still water (meta=0) top is 63 + 8/9 when air above, 64.0 when water above
        assertEquals(1.0F / 9.0F, WaterEnvironmentMath.computeFluidHeightFromMeta(0), 1e-6F);
        assertEquals(1.0F / 9.0F, WaterEnvironmentMath.computeFluidHeightFromMeta(8), 1e-6F);
        assertEquals(5.0F / 9.0F, WaterEnvironmentMath.computeFluidHeightFromMeta(4), 1e-6F);
        assertEquals(63.0F + 8.0F / 9.0F, WaterEnvironmentMath.computeFluidSurfaceY(63, 0, false), 1e-5F);
        assertEquals(64.0F, WaterEnvironmentMath.computeFluidSurfaceY(63, 0, true), 1e-5F);

        // Camera point submersion check
        assertFalse(WaterEnvironmentMath.isCameraPointInWater(63.95, 63, 0, true, false));
        assertTrue(WaterEnvironmentMath.isCameraPointInWater(63.80, 63, 0, true, false));
        assertTrue(WaterEnvironmentMath.isCameraPointInWater(63.95, 63, 0, true, true));
        assertFalse(WaterEnvironmentMath.isCameraPointInWater(63.50, 63, 0, false, false));
        assertFalse(WaterEnvironmentMath.isCameraPointInWater(63.60, 63, 4, true, false));
        assertTrue(WaterEnvironmentMath.isCameraPointInWater(63.30, 63, 4, true, false));
    }

    @Test
    void caveStaticWaterMaskAndAlphaPreserveOutdoorsAndStayVisibleInCaves() {
        assertEquals(1.0F, WaterEnvironmentMath.computeStillReflEnabledUniform(2, false), 1e-6F);
        assertEquals(0.0F, WaterEnvironmentMath.computeStillReflEnabledUniform(1, false), 1e-6F);
        assertEquals(0.0F, WaterEnvironmentMath.computeStillReflEnabledUniform(2, true), 1e-6F);

        // Open sky & light tree shade (skyExposure >= 0.75) keep caveMask = 0 and baseline 0.0 alpha
        assertEquals(0.0F, WaterEnvironmentMath.computeCaveSurfaceMask(1.0F, true), 1e-6F);
        assertEquals(0.0F, WaterEnvironmentMath.computeCaveSurfaceMask(0.80F, true), 1e-6F);
        assertEquals(0.0F, WaterEnvironmentMath.computeStillSurfaceAlpha(0.1F, 0.35F, 1.0F, true), 1e-6F);

        // Deep cave (skyExposure = 0.0) ramps cleanly to glassy translucent alpha (~0.3325)
        assertEquals(1.0F, WaterEnvironmentMath.computeCaveSurfaceMask(0.0F, true), 1e-6F);
        float caveAlpha = WaterEnvironmentMath.computeStillSurfaceAlpha(0.1F, 0.35F, 0.0F, true);
        assertEquals(0.35F * 0.95F, caveAlpha, 1e-5F);

        // Cave entrance transition is strictly monotonic between 0.75 and 0.05 skyExposure
        float entranceOuter = WaterEnvironmentMath.computeStillSurfaceAlpha(0.1F, 0.35F, 0.60F, true);
        float entranceMid = WaterEnvironmentMath.computeStillSurfaceAlpha(0.1F, 0.35F, 0.40F, true);
        float entranceInner = WaterEnvironmentMath.computeStillSurfaceAlpha(0.1F, 0.35F, 0.20F, true);
        assertTrue(entranceOuter > 0.0F && entranceOuter < entranceMid);
        assertTrue(entranceMid < entranceInner && entranceInner < caveAlpha);

        // Refraction-only / Nether (reflectionEnabled = false) always uses full cave surface alpha
        assertEquals(1.0F, WaterEnvironmentMath.computeCaveSurfaceMask(1.0F, false), 1e-6F);
        assertEquals(caveAlpha, WaterEnvironmentMath.computeStillSurfaceAlpha(0.1F, 0.35F, 1.0F, false), 1e-6F);

        // Higher user-configured water_surface_transparency is never reduced in caves
        assertEquals(0.60F, WaterEnvironmentMath.computeStillSurfaceAlpha(0.70F, 0.35F, 0.0F, true), 1e-5F);
    }

    @Test
    void reflectionEntityFilterSeparatesPlayerFromMobsAndItems() {
        // reflect_player=1, reflective_items=0 -> reflect ONLY player, skip mobs/items
        assertTrue(WaterShaderPipeline.shouldRenderReflectedEntity(true, true, false));
        assertFalse(WaterShaderPipeline.shouldRenderReflectedEntity(false, true, false));

        // reflect_player=0, reflective_items=1 -> skip player, reflect mobs/items
        assertFalse(WaterShaderPipeline.shouldRenderReflectedEntity(true, false, true));
        assertTrue(WaterShaderPipeline.shouldRenderReflectedEntity(false, false, true));

        // Both enabled / both disabled
        assertTrue(WaterShaderPipeline.shouldRenderReflectedEntity(true, true, true));
        assertTrue(WaterShaderPipeline.shouldRenderReflectedEntity(false, true, true));
        assertFalse(WaterShaderPipeline.shouldRenderReflectedEntity(true, false, false));
        assertFalse(WaterShaderPipeline.shouldRenderReflectedEntity(false, false, false));
    }

    @Test
    void overworldCaveSkipAndOpticalV12HelpersBehaveCleanly() {
        // Cave reflection skip: skips in Overworld caves when no sky-exposed water is visible
        assertFalse(WaterEnvironmentMath.shouldExecuteReflectionPass(true, false, false, true, false));
        assertTrue(WaterEnvironmentMath.shouldExecuteReflectionPass(true, false, false, true, true));
        assertTrue(WaterEnvironmentMath.shouldExecuteReflectionPass(true, false, false, false, false));
        assertFalse(WaterEnvironmentMath.shouldExecuteReflectionPass(false, false, false, false, true));
        assertFalse(WaterEnvironmentMath.shouldExecuteReflectionPass(true, true, false, false, true));
        assertFalse(WaterEnvironmentMath.shouldExecuteReflectionPass(true, false, true, false, true));

        // Beer-Lambert depth transmittance & shoreline contact fade
        assertEquals(1.0F, WaterEnvironmentMath.computeDepthTransmittance(0.0F, 1.0F), 1e-6F);
        float shallow = WaterEnvironmentMath.computeDepthTransmittance(1.0F, 1.0F);
        float medium = WaterEnvironmentMath.computeDepthTransmittance(4.0F, 1.0F);
        float deep = WaterEnvironmentMath.computeDepthTransmittance(12.0F, 1.0F);
        assertTrue(shallow > medium && medium > deep && deep > 0.0F);
        assertEquals(0.0F, WaterEnvironmentMath.computeShorelineFade(0.0F), 1e-6F);
        assertEquals(1.0F, WaterEnvironmentMath.computeShorelineFade(0.75F), 1e-6F);

        // Horizon linear fog factor
        assertEquals(0.0F, WaterEnvironmentMath.computeHorizonFogFactor(20.0F, 64.0F, 128.0F), 1e-6F);
        assertEquals(0.5F, WaterEnvironmentMath.computeHorizonFogFactor(96.0F, 64.0F, 128.0F), 1e-5F);
        assertEquals(1.0F, WaterEnvironmentMath.computeHorizonFogFactor(150.0F, 64.0F, 128.0F), 1e-6F);

        // Biome water tint palettes and smooth transitions
        float[] swamp = new float[3];
        float[] rainforest = new float[3];
        float[] tundra = new float[3];
        BiomeWaterColorHelper.resolveBiomePaletteByName("Swampland", swamp);
        BiomeWaterColorHelper.resolveBiomePaletteByName("Rainforest", rainforest);
        BiomeWaterColorHelper.resolveBiomePaletteByName("Tundra", tundra);
        assertTrue(swamp[0] > rainforest[0], "Swamp should be warmer/murkier olive than rainforest");
        assertTrue(rainforest[1] > tundra[1], "Rainforest should be greener turquoise than cold tundra");
        assertTrue(tundra[2] > tundra[1], "Tundra should lean cool steel-blue");

        float[] blended = new float[3];
        BiomeWaterColorHelper.blendWithBaseColor(0.0F, 0.5F, 0.35F, swamp[0], swamp[1], swamp[2], BiomeTintMode.OFF, blended);
        assertArrayEquals(new float[]{0.0F, 0.5F, 0.35F}, blended, 1e-6F);

        BiomeWaterColorHelper.blendWithBaseColor(0.0F, 0.5F, 0.35F, swamp[0], swamp[1], swamp[2], BiomeTintMode.VIVID, blended);
        assertTrue(blended[0] > 0.08F && blended[1] < 0.42F);

        float[] smoothed = new float[]{0.0F, 0.5F, 0.35F};
        for (int i = 0; i < 60; i++) {
            BiomeWaterColorHelper.stepSmoothedColor(smoothed, swamp, 0.05, false);
        }
        assertArrayEquals(swamp, smoothed, 1e-2F);
    }
}
