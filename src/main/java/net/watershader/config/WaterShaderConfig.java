package net.watershader.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Bounds-safe runtime facade over {@link WaterShaderGcapiConfig.ConfigFields}.
 * All getters read live from the GlassConfigAPI 3 config object and clamp/sanitize values
 * so hand-edited YAML entries (nulls, NaNs, or out-of-range numbers) can never break rendering.
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
        Integer raw = fields.antiAliasing;
        int val = raw != null ? raw : 2;
        return clampInt(val, 1, 4);
    }

    public int getReflectionResolution() {
        ReflectionResolution resolution = fields.reflectionResolution;
        return resolution != null ? resolution.getDivisor() : ReflectionResolution.FULL.getDivisor();
    }

    public boolean isReflectiveItems() {
        Boolean raw = reflections().reflectiveItems;
        return raw != null ? raw : true;
    }

    public boolean isReflectiveClouds() {
        Boolean raw = reflections().reflectiveClouds;
        return raw != null ? raw : false;
    }

    public boolean isReflectPlayer() {
        Boolean raw = reflections().reflectPlayer;
        return raw != null ? raw : true;
    }

    public boolean isRenderV3() {
        Boolean raw = surface().renderV3;
        return raw != null ? raw : true;
    }

    public float getRenderV3Float() {
        return isRenderV3() ? 1.0F : 0.0F;
    }

    public float getWaterSurfaceTransparency() {
        return clampFloat(surface().waterSurfaceTransparency, 0.1F, 0.0F, 1.0F);
    }

    public float getWaterfallTransparency() {
        return clampFloat(surface().waterfallTransparency, 0.35F, 0.0F, 1.0F);
    }

    public float[] getWaterColor() {
        return new float[]{getWaterColorR(), getWaterColorG(), getWaterColorB()};
    }

    public float getWaterColorR() {
        return clampFloat(colors().waterColorR, 0.0F, 0.0F, 2.0F);
    }

    public float getWaterColorG() {
        return clampFloat(colors().waterColorG, 0.5F, 0.0F, 2.0F);
    }

    public float getWaterColorB() {
        return clampFloat(colors().waterColorB, 0.35F, 0.0F, 2.0F);
    }

    public float[] getWaterfallColor() {
        return new float[]{getWaterfallColorR(), getWaterfallColorG(), getWaterfallColorB()};
    }

    public float getWaterfallColorR() {
        return clampFloat(colors().waterfallColorR, 1.0F, 0.0F, 2.0F);
    }

    public float getWaterfallColorG() {
        return clampFloat(colors().waterfallColorG, 1.0F, 0.0F, 2.0F);
    }

    public float getWaterfallColorB() {
        return clampFloat(colors().waterfallColorB, 1.0F, 0.0F, 2.0F);
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
        return clampFloat(reflections().fresnelStrength, 1.0F, 0.0F, 4.0F);
    }

    public float getRippleStrength() {
        return clampFloat(reflections().rippleStrength, 1.0F, 0.0F, 4.0F);
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
