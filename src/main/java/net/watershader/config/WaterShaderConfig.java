package net.watershader.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Bounds-safe runtime facade over {@link WaterShaderGcapiConfig.ConfigFields}.
 * All getters read live from the GlassConfigAPI 3 config object, apply any active
 * {@link WaterPreset} profile without overwriting the user's {@code Custom} values,
 * and clamp/sanitize values so hand-edited YAML entries can never break rendering.
 */
public final class WaterShaderConfig {
    private static volatile Runnable changeListener;

    private final WaterShaderGcapiConfig.ConfigFields fields;

    public WaterShaderConfig() {
        this(WaterShaderGcapiConfig.CONFIG);
    }

    public WaterShaderConfig(WaterShaderGcapiConfig.ConfigFields fields) {
        this.fields = Objects.requireNonNull(fields, "fields");
    }

    public static void setChangeListener(Runnable listener) {
        changeListener = listener;
    }

    public static void notifyChanged() {
        Runnable listener = changeListener;
        if (listener != null) {
            listener.run();
        }
    }

    public WaterShaderGcapiConfig.ConfigFields getRawFields() {
        return fields;
    }

    public WaterPreset getWaterPreset() {
        WaterPreset preset = fields.waterPreset;
        return preset != null ? preset : WaterPreset.CUSTOM;
    }

    private WaterShaderGcapiConfig.PerformanceConfig performance() {
        return fields.performance != null ? fields.performance : new WaterShaderGcapiConfig.PerformanceConfig();
    }

    private WaterShaderGcapiConfig.ReflectionsConfig reflections() {
        return fields.reflections != null ? fields.reflections : new WaterShaderGcapiConfig.ReflectionsConfig();
    }

    private WaterShaderGcapiConfig.SurfaceConfig surface() {
        return fields.surface != null ? fields.surface : new WaterShaderGcapiConfig.SurfaceConfig();
    }

    private WaterShaderGcapiConfig.ColorsConfig colors() {
        return fields.colors != null ? fields.colors : new WaterShaderGcapiConfig.ColorsConfig();
    }

    public int getWaterMode() {
        WaterMode mode = fields.waterMode;
        return mode != null ? mode.getModeId() : WaterMode.REFLECTION_AND_REFRACTION.getModeId();
    }

    public int getAntiAliasing() {
        if (getWaterPreset() == WaterPreset.PERFORMANCE) {
            return 1;
        }
        Integer raw = fields.antiAliasing;
        int val = raw != null ? raw : 2;
        return clampInt(val, 1, 4);
    }

    public int getReflectionResolution() {
        if (getWaterPreset() == WaterPreset.PERFORMANCE) {
            return ReflectionResolution.HALF.getDivisor();
        }
        ReflectionResolution resolution = fields.reflectionResolution;
        return resolution != null ? resolution.getDivisor() : ReflectionResolution.FULL.getDivisor();
    }

    public boolean isFastStencilDepthBlit() {
        if (getWaterPreset() == WaterPreset.PERFORMANCE) {
            return true;
        }
        Boolean raw = performance().fastStencilDepthBlit;
        return raw != null ? raw : true;
    }

    public boolean isSkipCaveReflections() {
        if (getWaterPreset() == WaterPreset.PERFORMANCE) {
            return true;
        }
        Boolean raw = performance().skipCaveReflections;
        return raw != null ? raw : true;
    }

    public boolean isStencilNative1x() {
        if (getWaterPreset() == WaterPreset.PERFORMANCE) {
            return true;
        }
        StencilResolution res = performance().stencilResolution;
        return res != null && res.isNative1x();
    }

    public boolean isReflectiveItems() {
        if (getWaterPreset() == WaterPreset.PERFORMANCE) {
            return false;
        }
        Boolean raw = reflections().reflectiveItems;
        return raw != null ? raw : true;
    }

    public boolean isReflectiveClouds() {
        if (getWaterPreset() == WaterPreset.PERFORMANCE) {
            return false;
        }
        Boolean raw = reflections().reflectiveClouds;
        return raw != null ? raw : false;
    }

    public boolean isReflectPlayer() {
        Boolean raw = reflections().reflectPlayer;
        return raw != null ? raw : true;
    }

    public boolean isRainRipples() {
        return switch (getWaterPreset()) {
            case CLASSIC_V4C, PERFORMANCE -> false;
            case MODERN_BALANCED, CRYSTAL_TROPICAL -> true;
            case CUSTOM -> {
                Boolean raw = reflections().rainRipples;
                yield raw != null ? raw : true;
            }
        };
    }

    public boolean isRenderV3() {
        Boolean raw = surface().renderV3;
        return raw != null ? raw : true;
    }

    public float getRenderV3Float() {
        return isRenderV3() ? 1.0F : 0.0F;
    }

    public float getWaterSurfaceTransparency() {
        return switch (getWaterPreset()) {
            case CLASSIC_V4C, MODERN_BALANCED, PERFORMANCE -> 0.1F;
            case CRYSTAL_TROPICAL -> 0.08F;
            case CUSTOM -> clampFloat(surface().waterSurfaceTransparency, 0.1F, 0.0F, 1.0F);
        };
    }

    public float getWaterfallTransparency() {
        return switch (getWaterPreset()) {
            case CLASSIC_V4C, MODERN_BALANCED, PERFORMANCE -> 0.35F;
            case CRYSTAL_TROPICAL -> 0.28F;
            case CUSTOM -> clampFloat(surface().waterfallTransparency, 0.35F, 0.0F, 1.0F);
        };
    }

    public boolean isDepthAbsorption() {
        return switch (getWaterPreset()) {
            case CLASSIC_V4C -> false;
            case MODERN_BALANCED, CRYSTAL_TROPICAL, PERFORMANCE -> true;
            case CUSTOM -> {
                Boolean raw = surface().depthAbsorption;
                yield raw != null ? raw : true;
            }
        };
    }

    public float getDepthAbsorptionStrength() {
        return switch (getWaterPreset()) {
            case CLASSIC_V4C -> 0.0F;
            case MODERN_BALANCED -> 1.0F;
            case CRYSTAL_TROPICAL -> 0.72F;
            case PERFORMANCE -> 0.90F;
            case CUSTOM -> clampFloat(surface().depthAbsorptionStrength, 1.0F, 0.0F, 3.0F);
        };
    }

    public boolean isHorizonFogFade() {
        return switch (getWaterPreset()) {
            case CLASSIC_V4C -> false;
            case MODERN_BALANCED, CRYSTAL_TROPICAL, PERFORMANCE -> true;
            case CUSTOM -> {
                Boolean raw = surface().horizonFogFade;
                yield raw != null ? raw : true;
            }
        };
    }

    public boolean isModernCaveWater() {
        return switch (getWaterPreset()) {
            case CLASSIC_V4C -> false;
            case MODERN_BALANCED, CRYSTAL_TROPICAL, PERFORMANCE -> true;
            case CUSTOM -> {
                Boolean raw = surface().modernCaveWater;
                yield raw != null ? raw : true;
            }
        };
    }

    public BiomeTintMode getBiomeTintMode() {
        return switch (getWaterPreset()) {
            case CLASSIC_V4C, PERFORMANCE -> BiomeTintMode.OFF;
            case MODERN_BALANCED -> BiomeTintMode.SUBTLE;
            case CRYSTAL_TROPICAL -> BiomeTintMode.SUBTLE;
            case CUSTOM -> {
                BiomeTintMode mode = colors().biomeTintMode;
                yield mode != null ? mode : BiomeTintMode.OFF;
            }
        };
    }

    public float[] getWaterColor() {
        return new float[]{getWaterColorR(), getWaterColorG(), getWaterColorB()};
    }

    public float getWaterColorR() {
        return switch (getWaterPreset()) {
            case CLASSIC_V4C, MODERN_BALANCED, PERFORMANCE -> 0.0F;
            case CRYSTAL_TROPICAL -> 0.0F;
            case CUSTOM -> clampFloat(colors().waterColorR, 0.0F, 0.0F, 2.0F);
        };
    }

    public float getWaterColorG() {
        return switch (getWaterPreset()) {
            case CLASSIC_V4C, MODERN_BALANCED, PERFORMANCE -> 0.5F;
            case CRYSTAL_TROPICAL -> 0.64F;
            case CUSTOM -> clampFloat(colors().waterColorG, 0.5F, 0.0F, 2.0F);
        };
    }

    public float getWaterColorB() {
        return switch (getWaterPreset()) {
            case CLASSIC_V4C, MODERN_BALANCED, PERFORMANCE -> 0.35F;
            case CRYSTAL_TROPICAL -> 0.56F;
            case CUSTOM -> clampFloat(colors().waterColorB, 0.35F, 0.0F, 2.0F);
        };
    }

    public float[] getWaterfallColor() {
        return new float[]{getWaterfallColorR(), getWaterfallColorG(), getWaterfallColorB()};
    }

    public float getWaterfallColorR() {
        return switch (getWaterPreset()) {
            case CLASSIC_V4C, MODERN_BALANCED, CRYSTAL_TROPICAL, PERFORMANCE -> 1.0F;
            case CUSTOM -> clampFloat(colors().waterfallColorR, 1.0F, 0.0F, 2.0F);
        };
    }

    public float getWaterfallColorG() {
        return switch (getWaterPreset()) {
            case CLASSIC_V4C, MODERN_BALANCED, CRYSTAL_TROPICAL, PERFORMANCE -> 1.0F;
            case CUSTOM -> clampFloat(colors().waterfallColorG, 1.0F, 0.0F, 2.0F);
        };
    }

    public float getWaterfallColorB() {
        return switch (getWaterPreset()) {
            case CLASSIC_V4C, MODERN_BALANCED, CRYSTAL_TROPICAL, PERFORMANCE -> 1.0F;
            case CUSTOM -> clampFloat(colors().waterfallColorB, 1.0F, 0.0F, 2.0F);
        };
    }

    public boolean isWaterfallFog() {
        Boolean raw = surface().waterfallFog;
        return raw != null ? raw : true;
    }

    public List<String> getExcludeList() {
        String[] raw = surface().excludeList;
        if (raw == null || raw.length == 0) {
            return Collections.emptyList();
        }
        List<String> items = new ArrayList<>(raw.length);
        for (String entry : raw) {
            if (entry == null) {
                continue;
            }
            for (String token : entry.split("[,\\s]+")) {
                if (!token.isEmpty()) {
                    items.add(token);
                }
            }
        }
        return items.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(items);
    }

    public float getFresnelStrength() {
        return switch (getWaterPreset()) {
            case CLASSIC_V4C, MODERN_BALANCED, PERFORMANCE -> 1.0F;
            case CRYSTAL_TROPICAL -> 0.90F;
            case CUSTOM -> clampFloat(reflections().fresnelStrength, 1.0F, 0.0F, 4.0F);
        };
    }

    public float getRippleStrength() {
        return switch (getWaterPreset()) {
            case CLASSIC_V4C, MODERN_BALANCED -> 1.0F;
            case CRYSTAL_TROPICAL -> 0.85F;
            case PERFORMANCE -> 0.90F;
            case CUSTOM -> clampFloat(reflections().rippleStrength, 1.0F, 0.0F, 4.0F);
        };
    }

    public boolean isUnderwaterEffect() {
        Boolean raw = fields.underwaterEffect;
        return raw != null ? raw : true;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clampFloat(Float raw, float fallback, float min, float max) {
        if (raw == null || Float.isNaN(raw) || Float.isInfinite(raw)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, raw));
    }
}
