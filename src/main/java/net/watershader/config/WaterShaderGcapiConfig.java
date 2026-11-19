package net.watershader.config;

import net.glasslauncher.mods.gcapi3.api.ConfigCategory;
import net.glasslauncher.mods.gcapi3.api.ConfigEntry;
import net.glasslauncher.mods.gcapi3.api.ConfigRoot;

/**
 * GlassConfigAPI 3 configuration schema for Water Shader Unleashed.
 * Managed automatically in {@code config/watershader/config.yml} and exposed in ModMenu.
 */
public final class WaterShaderGcapiConfig {
    @ConfigRoot(value = "config", visibleName = "Water Shader Unleashed")
    public static final ConfigFields CONFIG = new ConfigFields();

    public static final class ConfigFields {
        @ConfigEntry(
            name = "Water Mode",
            description = "Off, Refraction Only, or Reflect & Refract",
            comment = "0 = Off, 1 = Refraction Only, 2 = Reflect & Refract"
        )
        public WaterMode waterMode = WaterMode.REFLECTION_AND_REFRACTION;

        @ConfigEntry(
            name = "Reflection Resolution",
            description = "Reflection FBO scale: Full (1/1), Half (1/2), or Quarter (1/4)",
            comment = "0 = Full (1/1), 1 = Half (1/2), 2 = Quarter (1/4)"
        )
        public ReflectionResolution reflectionResolution = ReflectionResolution.FULL;

        @ConfigEntry(
            name = "Anti-Aliasing",
            description = "Internal supersampling multiplier (1 to 4; 2 matches classic 4x pixel count)",
            comment = "Internal supersampling multiplier (1..4)",
            minValue = 1.0,
            maxValue = 4.0
        )
        public Integer antiAliasing = 2;

        @ConfigEntry(
            name = "Underwater Effect",
            description = "Enable shader underwater fog tint and gentle refraction wobble",
            comment = "Enable shader underwater tint and wobble"
        )
        public Boolean underwaterEffect = true;

        @ConfigCategory(
            name = "Reflections",
            description = "Planar reflection entities, clouds, Fresnel curve, and surface ripples"
        )
        public ReflectionsConfig reflections = new ReflectionsConfig();

        @ConfigCategory(
            name = "Surface & Waterfalls",
            description = "Transparency, v3 brightness lift, waterfall fog, and excluded blocks"
        )
        public SurfaceConfig surface = new SurfaceConfig();

        @ConfigCategory(
            name = "Colors",
            description = "RGB tint multipliers for deep water and waterfalls (0.0 to 2.0)"
        )
        public ColorsConfig colors = new ColorsConfig();
    }

    public static final class ReflectionsConfig {
        @ConfigEntry(
            name = "Reflective Entities",
            description = "Reflect mobs, dropped items, and other non-player entities in water",
            comment = "Reflect entities/items/mobs in water"
        )
        public Boolean reflectiveItems = true;

        @ConfigEntry(
            name = "Reflect Local Player",
            description = "Reflect the local player model in water",
            comment = "Reflect local player in water"
        )
        public Boolean reflectPlayer = true;

        @ConfigEntry(
            name = "Reflective Clouds",
            description = "Reflect clouds in water (off matches classic v4c default)",
            comment = "Reflect clouds in water"
        )
        public Boolean reflectiveClouds = false;

        @ConfigEntry(
            name = "Fresnel Strength",
            description = "Viewing-angle reflection & Fresnel curve multiplier (0.0 to 4.0)",
            comment = "Fresnel reflection curve multiplier (0.0..4.0)",
            minValue = 0.0,
            maxValue = 4.0
        )
        public Float fresnelStrength = 1.0F;

        @ConfigEntry(
            name = "Ripple Strength",
            description = "Surface wave distortion multiplier (0.0 to 4.0)",
            comment = "Surface ripple distortion multiplier (0.0..4.0)",
            minValue = 0.0,
            maxValue = 4.0
        )
        public Float rippleStrength = 1.0F;
    }

    public static final class SurfaceConfig {
        @ConfigEntry(
            name = "Classic v3 Brightness Lift",
            description = "Enable Water Shader v3 open-sky brightness lift on water surfaces",
            comment = "Enable v3 open-sky brightness lift"
        )
        public Boolean renderV3 = true;

        @ConfigEntry(
            name = "Water Surface Transparency",
            description = "Still-water surface alpha from 0.0 (clear) to 1.0 (opaque)",
            comment = "Still-water surface alpha (0.0..1.0)",
            minValue = 0.0,
            maxValue = 1.0
        )
        public Float waterSurfaceTransparency = 0.1F;

        @ConfigEntry(
            name = "Waterfall Transparency",
            description = "Flowing water & waterfall alpha from 0.0 (clear) to 1.0 (opaque)",
            comment = "Flowing slope / waterfall alpha (0.0..1.0)",
            minValue = 0.0,
            maxValue = 1.0
        )
        public Float waterfallTransparency = 0.35F;

        @ConfigEntry(
            name = "Waterfall & Cave Fog",
            description = "Enable fog attenuation on waterfalls and cave water",
            comment = "Enable fog handling on waterfalls and cave water"
        )
        public Boolean waterfallFog = true;

        @ConfigEntry(
            name = "Excluded Blocks",
            description = "Block IDs or translation keys excluded from water shader effects",
            comment = "Block IDs or translation keys excluded from water shader effects",
            maxValue = 128.0
        )
        public String[] excludeList = new String[0];
    }

    public static final class ColorsConfig {
        @ConfigEntry(
            name = "Water Color - Red",
            description = "Deep water tint Red channel (0.0 to 2.0, default 0.0)",
            comment = "Deep water tint Red channel (0.0..2.0)",
            minValue = 0.0,
            maxValue = 2.0
        )
        public Float waterColorR = 0.0F;

        @ConfigEntry(
            name = "Water Color - Green",
            description = "Deep water tint Green channel (0.0 to 2.0, default 0.5)",
            comment = "Deep water tint Green channel (0.0..2.0)",
            minValue = 0.0,
            maxValue = 2.0
        )
        public Float waterColorG = 0.5F;

        @ConfigEntry(
            name = "Water Color - Blue",
            description = "Deep water tint Blue channel (0.0 to 2.0, default 0.35)",
            comment = "Deep water tint Blue channel (0.0..2.0)",
            minValue = 0.0,
            maxValue = 2.0
        )
        public Float waterColorB = 0.35F;

        @ConfigEntry(
            name = "Waterfall Color - Red",
            description = "Multiplicative waterfall tint Red channel (0.0 to 2.0, default 1.0)",
            comment = "Multiplicative waterfall tint Red channel (0.0..2.0)",
            minValue = 0.0,
            maxValue = 2.0
        )
        public Float waterfallColorR = 1.0F;

        @ConfigEntry(
            name = "Waterfall Color - Green",
            description = "Multiplicative waterfall tint Green channel (0.0 to 2.0, default 1.0)",
            comment = "Multiplicative waterfall tint Green channel (0.0..2.0)",
            minValue = 0.0,
            maxValue = 2.0
        )
        public Float waterfallColorG = 1.0F;

        @ConfigEntry(
            name = "Waterfall Color - Blue",
            description = "Multiplicative waterfall tint Blue channel (0.0 to 2.0, default 1.0)",
            comment = "Multiplicative waterfall tint Blue channel (0.0..2.0)",
            minValue = 0.0,
            maxValue = 2.0
        )
        public Float waterfallColorB = 1.0F;
    }
}
