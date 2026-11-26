package net.watershader.math;

/**
 * Pure OpenGL-free environment, rain/cave reflection scaling, and underwater transition math for
 * Phase 6 features 6.2 (rain-aware reflection + modern rain + no cave glow) and 6.4 (underwater).
 */
public final class WaterEnvironmentMath {
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private WaterEnvironmentMath() {}

    /**
     * Computes normalized {@code [0, 1]} Beta 1.7.3 ambient skylight intensity from
     * {@code world.getTime(tickDelta)} ({@code 0.0} = noon, {@code 0.5} = midnight) strictly for
     * 6.4 underwater tint brightness so deep/submerged ocean at night is not bright daytime cyan.
     */
    public static float computeSkyAmbientLight(float timeOfDay) {
        if (!Float.isFinite(timeOfDay)) {
            return 1.0F;
        }
        float c = (float) Math.cos(timeOfDay * TWO_PI);
        return clamp01(c * 2.0F + 0.5F);
    }

    /**
     * Computes the global reflection brightness multiplier for rain/thunder and ceilinged/Nether
     * dimensions (keeping strong horizon reflection clarity in rain, without day/night attenuation).
     */
    public static float computeReflectionBrightness(
            float rainGradient,
            float thunderGradient,
            boolean hasCeiling) {
        if (hasCeiling) {
            return 0.0F;
        }
        float rain = clamp01(rainGradient);
        float thunder = clamp01(thunderGradient);
        return clamp01(1.0F - 0.20F * rain - 0.08F * thunder);
    }

    /**
     * Computes the global sky-light factor passed to {@code transparency.frag} to scale
     * {@code setting_v3} lift cleanly across clear weather, rain, and Nether dimensions.
     */
    public static float computeTransparencySkyLight(
            float rainGradient,
            float thunderGradient,
            boolean hasCeiling) {
        if (hasCeiling) {
            return 0.0F;
        }
        float rain = clamp01(rainGradient);
        float thunder = clamp01(thunderGradient);
        return clamp01(1.0F - 0.12F * rain - 0.05F * thunder);
    }

    /**
     * Returns {@code 1.0} when planar reflections can contribute to open-sky static water
     * ({@code water_mode >= 2} in non-ceilinged dimensions), or {@code 0.0} otherwise so static
     * water falls back cleanly to its calm translucent surface rendering.
     */
    public static float computeStillReflEnabledUniform(int waterMode, boolean hasCeiling) {
        return (waterMode >= 2 && !hasCeiling) ? 1.0F : 0.0F;
    }

    /**
     * Computes the {@code [0, 1]} cave/non-reflective static-water mask matching
     * {@code transparency.frag} and {@code water.frag}: {@code 0.0} outdoors under open sky or
     * light tree canopies ({@code skyExposure >= 0.75}), smoothly ramping to {@code 1.0} inside
     * caves, enclosed rooms, ceilinged dimensions, or refraction-only mode.
     */
    public static float computeCaveSurfaceMask(float skyExposure, boolean reflectionEnabled) {
        float effectiveReflSky = reflectionEnabled ? clamp01(skyExposure) : 0.0F;
        return 1.0F - smoothstep(0.05F, 0.75F, effectiveReflSky);
    }

    /**
     * Computes the effective Pass 0 alpha for static water matching {@code transparency.frag}:
     * preserves {@code max(0, water_transparency - 0.1)} outdoors while ramping smoothly to a
     * flowing-matched translucent alpha in caves/non-reflective spaces so static pools never vanish.
     */
    public static float computeStillSurfaceAlpha(
            float waterTransparency,
            float waterfallTransparency,
            float skyExposure,
            boolean reflectionEnabled) {
        float openStillAlpha = Math.max(0.0F, clamp01(waterTransparency) - 0.1F);
        float caveMask = computeCaveSurfaceMask(skyExposure, reflectionEnabled);
        float caveStillAlpha = Math.max(openStillAlpha, clamp(clamp01(waterfallTransparency) * 0.95F, 0.30F, 0.48F));
        return mix(openStillAlpha, caveStillAlpha, caveMask);
    }

    /**
     * Determines whether Pass 2 ({@code REFLECTION}) should execute {@code WorldRenderer.renderSky}
     * rather than clearing {@code reflectedColorMap} to {@code (0, 0, 0, 0)}.
     */
    public static boolean shouldRenderReflectionSky(boolean hasCeiling, int effectiveCameraSkyLight) {
        return !hasCeiling && effectiveCameraSkyLight > 0;
    }

    /**
     * Normalizes Beta 1.7.3 {@code LightType.SKY} {@code [0..15]} into {@code [0..1]}.
     */
    public static float normalizeSkyLight(int skyLight) {
        return clamp01(skyLight / 15.0F);
    }

    /**
     * Computes ambient brightness for the underwater view tint so underwater caves stay dark while
     * sunlit open-sky water keeps a clear teal-aqua hue.
     */
    public static float computeUnderwaterBrightness(
            float skyAmbientLight,
            float rainGradient,
            int effectiveSkyLight,
            boolean hasCeiling) {
        if (hasCeiling) {
            return 0.25F;
        }
        float skyExp = normalizeSkyLight(effectiveSkyLight);
        float day = mix(0.22F, 1.0F, clamp01(skyAmbientLight));
        float rain = 1.0F - 0.30F * clamp01(rainGradient);
        float sky = mix(0.20F, 1.0F, skyExp);
        return clamp(day * rain * sky + 0.10F, 0.12F, 1.0F);
    }

    /**
     * Smoothly steps the underwater blend factor {@code [0, 1]} toward {@code eyeInWater ? 1 : 0}.
     */
    public static float stepUnderwaterFactor(float current, boolean eyeInWater, double dtSeconds) {
        float target = eyeInWater ? 1.0F : 0.0F;
        if (!Double.isFinite(dtSeconds) || dtSeconds <= 0.0) {
            return clamp01(current);
        }
        double clampedDt = Math.min(dtSeconds, 0.25);
        double tau = eyeInWater ? 0.08 : 0.10;
        double alpha = 1.0 - Math.exp(-clampedDt / tau);
        float next = (float) (clamp01(current) + (target - clamp01(current)) * alpha);
        if (Math.abs(target - next) < 1e-3F) {
            return target;
        }
        return clamp01(next);
    }

    /**
     * Linearizes an OpenGL depth buffer value {@code [0, 1]} into view-space distance (blocks).
     */
    public static float linearizeDepth(float depthSample, float zNear, float zFar) {
        double safeNear = Math.max(1e-4, zNear);
        double safeFar = Math.max(safeNear + 1e-3, zFar);
        double zNdc = clamp01(depthSample) * 2.0 - 1.0;
        double denom = Math.max(1e-4, safeFar + safeNear - zNdc * (safeFar - safeNear));
        return (float) ((2.0 * safeNear * safeFar) / denom);
    }

    /**
     * Physical camera submersion uniform passed to {@code water.frag} when looking up from
     * underneath water, independently of {@code underwater_effect}.
     */
    public static float computeCameraInWaterUniform(boolean cameraEyeInWater) {
        return cameraEyeInWater ? 1.0F : 0.0F;
    }

    /**
     * Underwater tint/wobble intensity uniform passed to {@code water.frag}, returning {@code 0.0}
     * whenever {@code underwater_effect} is disabled in config.
     */
    public static float computeUnderwaterEffectUniform(boolean underwaterEffectEnabled, float underwaterFactor) {
        return underwaterEffectEnabled ? clamp01(underwaterFactor) : 0.0F;
    }

    /**
     * Returns {@code true} when a texel in {@code handDepthMap} (captured after the pre-hand depth
     * clear in {@code GameRenderer.renderFrame}) was written by the first-person hand or held item.
     */
    public static boolean isFirstPersonHandDepth(float handDepthSample) {
        return Float.isFinite(handDepthSample) && handDepthSample < 0.9999F;
    }

    /**
     * Computes the effective underwater fog distance (blocks), pinning first-person hand pixels to
     * {@code ~0.3m} so the held hand/item is not over-fogged by background sky or seabed depth.
     */
    public static float computeUnderwaterFogDistance(
            float waterDepthSample,
            float handDepthSample,
            float zNear,
            float zFar) {
        if (isFirstPersonHandDepth(handDepthSample)) {
            return 0.3F;
        }
        return clamp(linearizeDepth(clamp01(waterDepthSample), zNear, zFar), 0.0F, 48.0F);
    }

    /**
     * Computes the Beta 1.7.3 third-person camera offset vector {@code (offX, offY, offZ)} matching
     * {@code GameRenderer.applyCameraTransform(float)} so the candidate world camera position is
     * {@code (baseX - offX, baseY - offY, baseZ - offZ)}.
     */
    public static void computeThirdPersonOffset(
            float yawDeg,
            float pitchDeg,
            double distance,
            double[] outOffsetXyz) {
        if (outOffsetXyz == null || outOffsetXyz.length < 3) {
            throw new IllegalArgumentException("outOffsetXyz must have at least 3 doubles");
        }
        if (!Float.isFinite(yawDeg) || !Float.isFinite(pitchDeg) || !Double.isFinite(distance)) {
            outOffsetXyz[0] = 0.0;
            outOffsetXyz[1] = 0.0;
            outOffsetXyz[2] = 0.0;
            return;
        }
        float yawRad = (yawDeg / 180.0F) * (float) Math.PI;
        float pitchRad = (pitchDeg / 180.0F) * (float) Math.PI;
        double cosPitch = Math.cos(pitchRad);
        outOffsetXyz[0] = -Math.sin(yawRad) * cosPitch * distance;
        outOffsetXyz[1] = -Math.sin(pitchRad) * distance;
        outOffsetXyz[2] = Math.cos(yawRad) * cosPitch * distance;
    }

    /**
     * Matches Beta 1.7.3 {@code LiquidBlock.getFluidHeightFromMeta(int)}: returns the empty gap from
     * the top of the block ({@code 1/9} for still/falling water, up to {@code 8/9} for shallow flow).
     */
    public static float computeFluidHeightFromMeta(int meta) {
        int clamped = (meta < 0 || meta >= 8) ? 0 : meta;
        return (clamped + 1) / 9.0F;
    }

    /**
     * Computes the world Y coordinate of the water surface inside block {@code blockY}.
     */
    public static float computeFluidSurfaceY(int blockY, int meta, boolean waterAbove) {
        if (waterAbove) {
            return blockY + 1.0F;
        }
        return (blockY + 1.0F) - computeFluidHeightFromMeta(meta);
    }

    /**
     * Returns {@code true} when a world camera point {@code camY} lies inside a water block below
     * its surface height.
     */
    public static boolean isCameraPointInWater(
            double camY,
            int blockY,
            int meta,
            boolean waterBlockAtPos,
            boolean waterAbove) {
        if (!waterBlockAtPos || !Double.isFinite(camY)) {
            return false;
        }
        return camY < (double) computeFluidSurfaceY(blockY, meta, waterAbove);
    }

    /**
     * Determines whether Pass 2 ({@code REFLECTION}) should execute this frame, allowing safe
     * skipping in Overworld caves when no sky-exposed water chunks are visible.
     */
    public static boolean shouldExecuteReflectionPass(
            boolean withReflection,
            boolean hasCeiling,
            boolean debugStencilView,
            boolean skipCaveReflections,
            boolean skyExposedWaterVisible) {
        if (!withReflection || hasCeiling || debugStencilView) {
            return false;
        }
        return !skipCaveReflections || skyExposedWaterVisible;
    }

    /**
     * Computes Beer-Lambert water transmittance {@code exp(-thickness * 0.24 * strength)} matching
     * {@code water.frag}, returning {@code 1.0} at the shoreline and decaying smoothly in deep water.
     */
    public static float computeDepthTransmittance(float waterThicknessBlocks, float absorptionStrength) {
        if (!Float.isFinite(waterThicknessBlocks) || !Float.isFinite(absorptionStrength)) {
            return 1.0F;
        }
        float thickness = clamp(waterThicknessBlocks, 0.0F, 24.0F);
        float strength = clamp(absorptionStrength, 0.0F, 3.0F);
        return clamp01((float) Math.exp(-thickness * 0.24F * strength));
    }

    /**
     * Computes shoreline edge feather {@code [0, 1]} over the first {@code 0.65} blocks of water
     * thickness so refracted/reflected edges dissolve cleanly into sand/dirt shorelines.
     */
    public static float computeShorelineFade(float waterThicknessBlocks) {
        if (!Float.isFinite(waterThicknessBlocks)) {
            return 1.0F;
        }
        return smoothstep(0.0F, 0.65F, Math.max(0.0F, waterThicknessBlocks));
    }

    /**
     * Computes linear distance fog blend {@code [0, 1]} matching Minecraft's {@code GL_LINEAR} fog
     * range {@code [fogStart, fogEnd]}.
     */
    public static float computeHorizonFogFactor(float surfaceDist, float fogStart, float fogEnd) {
        if (!Float.isFinite(surfaceDist) || !Float.isFinite(fogStart) || !Float.isFinite(fogEnd)) {
            return 0.0F;
        }
        float span = Math.max(0.001F, fogEnd - fogStart);
        return clamp01((surfaceDist - fogStart) / span);
    }

    public static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0F - 2.0F * t);
    }

    public static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static float clamp01(float v) {
        return clamp(v, 0.0F, 1.0F);
    }

    public static float clamp(float v, float min, float max) {
        if (!Float.isFinite(v)) {
            return min;
        }
        if (v < min) {
            return min;
        }
        if (v > max) {
            return max;
        }
        return v;
    }
}
