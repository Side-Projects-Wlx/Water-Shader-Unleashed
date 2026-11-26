package net.watershader.config;

import com.google.common.collect.ImmutableMap;
import net.glasslauncher.mods.gcapi3.api.ConfigCategory;
import net.glasslauncher.mods.gcapi3.api.ConfigEntry;
import net.glasslauncher.mods.gcapi3.api.ConfigRoot;
import net.glasslauncher.mods.gcapi3.impl.SeptFunction;
import net.glasslauncher.mods.gcapi3.impl.object.ConfigEntryHandler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class WaterShaderConfigTest {
    @Test
    void defaultGcapiFieldsMatchBeta173Defaults() {
        WaterShaderGcapiConfig.ConfigFields raw = new WaterShaderGcapiConfig.ConfigFields();
        WaterShaderConfig cfg = new WaterShaderConfig(raw);

        assertEquals(WaterPreset.CUSTOM, cfg.getWaterPreset());
        assertEquals(2, cfg.getWaterMode());
        assertEquals(2, cfg.getAntiAliasing());
        assertEquals(1, cfg.getReflectionResolution());
        assertTrue(cfg.isFastStencilDepthBlit());
        assertTrue(cfg.isSkipCaveReflections());
        assertFalse(cfg.isStencilNative1x());
        assertTrue(cfg.isReflectiveItems());
        assertFalse(cfg.isReflectiveClouds());
        assertTrue(cfg.isReflectPlayer());
        assertTrue(cfg.isRainRipples());
        assertTrue(cfg.isRenderV3());
        assertEquals(1.0F, cfg.getRenderV3Float(), 1e-6F);
        assertEquals(0.1F, cfg.getWaterSurfaceTransparency(), 1e-6F);
        assertEquals(0.35F, cfg.getWaterfallTransparency(), 1e-6F);
        assertTrue(cfg.isDepthAbsorption());
        assertEquals(1.0F, cfg.getDepthAbsorptionStrength(), 1e-6F);
        assertTrue(cfg.isHorizonFogFade());
        assertTrue(cfg.isModernCaveWater());
        assertEquals(BiomeTintMode.OFF, cfg.getBiomeTintMode());
        assertArrayEquals(new float[]{0.0F, 0.5F, 0.35F}, cfg.getWaterColor(), 1e-6F);
        assertArrayEquals(new float[]{1.0F, 1.0F, 1.0F}, cfg.getWaterfallColor(), 1e-6F);
        assertTrue(cfg.isWaterfallFog());
        assertTrue(cfg.getExcludeList().isEmpty());
        assertEquals(1.0F, cfg.getFresnelStrength(), 1e-6F);
        assertEquals(1.0F, cfg.getRippleStrength(), 1e-6F);
        assertTrue(cfg.isUnderwaterEffect());
    }

    @Test
    void reflectsLiveCustomValuesAcrossAllCategories() {
        WaterShaderGcapiConfig.ConfigFields raw = new WaterShaderGcapiConfig.ConfigFields();
        raw.waterMode = WaterMode.REFRACTION_ONLY;
        raw.reflectionResolution = ReflectionResolution.HALF;
        raw.antiAliasing = 4;
        raw.underwaterEffect = false;

        raw.reflections.reflectiveItems = false;
        raw.reflections.reflectPlayer = false;
        raw.reflections.reflectiveClouds = true;
        raw.reflections.fresnelStrength = 1.5F;
        raw.reflections.rippleStrength = 0.75F;

        raw.surface.renderV3 = false;
        raw.surface.waterSurfaceTransparency = 0.25F;
        raw.surface.waterfallTransparency = 0.6F;
        raw.surface.waterfallFog = false;
        raw.surface.excludeList = new String[]{"8", " 9, 79 ", "tile.water"};

        raw.colors.waterColorR = 0.1F;
        raw.colors.waterColorG = 0.2F;
        raw.colors.waterColorB = 0.3F;
        raw.colors.waterfallColorR = 0.8F;
        raw.colors.waterfallColorG = 0.9F;
        raw.colors.waterfallColorB = 1.0F;

        WaterShaderConfig cfg = new WaterShaderConfig(raw);
        assertEquals(1, cfg.getWaterMode());
        assertEquals(4, cfg.getAntiAliasing());
        assertEquals(2, cfg.getReflectionResolution());
        assertFalse(cfg.isUnderwaterEffect());
        assertFalse(cfg.isReflectiveItems());
        assertTrue(cfg.isReflectiveClouds());
        assertFalse(cfg.isReflectPlayer());
        assertEquals(1.5F, cfg.getFresnelStrength(), 1e-6F);
        assertEquals(0.75F, cfg.getRippleStrength(), 1e-6F);
        assertFalse(cfg.isRenderV3());
        assertEquals(0.0F, cfg.getRenderV3Float(), 1e-6F);
        assertEquals(0.25F, cfg.getWaterSurfaceTransparency(), 1e-6F);
        assertEquals(0.6F, cfg.getWaterfallTransparency(), 1e-6F);
        assertFalse(cfg.isWaterfallFog());
        assertEquals(List.of("8", "9", "79", "tile.water"), cfg.getExcludeList());
        assertArrayEquals(new float[]{0.1F, 0.2F, 0.3F}, cfg.getWaterColor(), 1e-6F);
        assertArrayEquals(new float[]{0.8F, 0.9F, 1.0F}, cfg.getWaterfallColor(), 1e-6F);
    }

    @Test
    void clampsOutOfRangeNullAndNonFiniteValuesSafely() {
        WaterShaderGcapiConfig.ConfigFields raw = new WaterShaderGcapiConfig.ConfigFields();
        raw.waterMode = null;
        raw.reflectionResolution = null;
        raw.antiAliasing = 0;
        raw.underwaterEffect = null;

        raw.reflections.fresnelStrength = -5.0F;
        raw.reflections.rippleStrength = Float.NaN;
        raw.surface.waterSurfaceTransparency = -0.5F;
        raw.surface.waterfallTransparency = 2.5F;
        raw.surface.excludeList = new String[]{null, "   ", "8"};
        raw.colors.waterColorR = Float.POSITIVE_INFINITY;
        raw.colors.waterColorG = 5.0F;
        raw.colors.waterColorB = -1.0F;
        raw.colors.waterfallColorR = Float.NaN;

        WaterShaderConfig cfg = new WaterShaderConfig(raw);
        assertEquals(2, cfg.getWaterMode());
        assertEquals(1, cfg.getReflectionResolution());
        assertEquals(1, cfg.getAntiAliasing());
        assertTrue(cfg.isUnderwaterEffect());
        assertEquals(0.0F, cfg.getFresnelStrength(), 1e-6F);
        assertEquals(1.0F, cfg.getRippleStrength(), 1e-6F);
        assertEquals(0.0F, cfg.getWaterSurfaceTransparency(), 1e-6F);
        assertEquals(1.0F, cfg.getWaterfallTransparency(), 1e-6F);
        assertEquals(List.of("8"), cfg.getExcludeList());
        assertArrayEquals(new float[]{0.0F, 2.0F, 0.0F}, cfg.getWaterColor(), 1e-6F);
        assertArrayEquals(new float[]{1.0F, 1.0F, 1.0F}, cfg.getWaterfallColor(), 1e-6F);

        raw.antiAliasing = 99;
        assertEquals(4, cfg.getAntiAliasing());

        raw.reflections = null;
        raw.surface = null;
        raw.colors = null;
        assertTrue(cfg.isReflectiveItems());
        assertTrue(cfg.isRenderV3());
        assertArrayEquals(new float[]{0.0F, 0.5F, 0.35F}, cfg.getWaterColor(), 1e-6F);
    }

    @Test
    void enumMappingAndYamlParsingCoverAllTiers() {
        assertEquals(0, WaterMode.OFF.getModeId());
        assertEquals(1, WaterMode.REFRACTION_ONLY.getModeId());
        assertEquals(2, WaterMode.REFLECTION_AND_REFRACTION.getModeId());
        assertEquals(0, WaterMode.ordinalFromYamlValue(-5, WaterMode.REFLECTION_AND_REFRACTION));
        assertEquals(1, WaterMode.ordinalFromYamlValue(1, WaterMode.REFLECTION_AND_REFRACTION));
        assertEquals(2, WaterMode.ordinalFromYamlValue(99, WaterMode.OFF));
        assertEquals(1, WaterMode.ordinalFromYamlValue("refraction_only", WaterMode.OFF));
        assertEquals(2, WaterMode.ordinalFromYamlValue("invalid", WaterMode.REFLECTION_AND_REFRACTION));

        assertEquals(1, ReflectionResolution.FULL.getDivisor());
        assertEquals(2, ReflectionResolution.HALF.getDivisor());
        assertEquals(4, ReflectionResolution.QUARTER.getDivisor());
        assertEquals(ReflectionResolution.FULL, ReflectionResolution.fromDivisor(1));
        assertEquals(ReflectionResolution.HALF, ReflectionResolution.fromDivisor(2));
        assertEquals(ReflectionResolution.QUARTER, ReflectionResolution.fromDivisor(4));
        assertEquals(ReflectionResolution.QUARTER, ReflectionResolution.fromDivisor(8));
        assertEquals(0, ReflectionResolution.ordinalFromYamlValue("full", ReflectionResolution.QUARTER));
        assertEquals(1, ReflectionResolution.ordinalFromYamlValue("1/2", ReflectionResolution.FULL));
        assertEquals(2, ReflectionResolution.ordinalFromYamlValue("quarter", ReflectionResolution.FULL));
        assertEquals(2, ReflectionResolution.ordinalFromYamlValue(99, ReflectionResolution.FULL));
    }

    @Test
    void waterPresetsApplyCuratedProfilesWithoutDestroyingCustomSettings() {
        WaterShaderGcapiConfig.ConfigFields raw = new WaterShaderGcapiConfig.ConfigFields();
        raw.antiAliasing = 3;
        raw.colors.waterColorR = 0.25F;
        raw.colors.biomeTintMode = BiomeTintMode.VIVID;
        WaterShaderConfig cfg = new WaterShaderConfig(raw);

        // Classic v4c disables modern depth/fog/cave/rain-ripples/biome-tint
        raw.waterPreset = WaterPreset.CLASSIC_V4C;
        assertFalse(cfg.isDepthAbsorption());
        assertFalse(cfg.isHorizonFogFade());
        assertFalse(cfg.isModernCaveWater());
        assertFalse(cfg.isRainRipples());
        assertEquals(BiomeTintMode.OFF, cfg.getBiomeTintMode());
        assertEquals(0.0F, cfg.getWaterColorR(), 1e-6F);

        // Modern Balanced enables full visual suite + subtle biome tinting
        raw.waterPreset = WaterPreset.MODERN_BALANCED;
        assertTrue(cfg.isDepthAbsorption());
        assertTrue(cfg.isHorizonFogFade());
        assertTrue(cfg.isModernCaveWater());
        assertEquals(BiomeTintMode.SUBTLE, cfg.getBiomeTintMode());

        // Crystal Tropical uses clearer tropical turquoise palette
        raw.waterPreset = WaterPreset.CRYSTAL_TROPICAL;
        assertEquals(0.64F, cfg.getWaterColorG(), 1e-6F);
        assertEquals(0.56F, cfg.getWaterColorB(), 1e-6F);
        assertEquals(0.72F, cfg.getDepthAbsorptionStrength(), 1e-6F);

        // Performance drops AA to 1x, reflections to Half, stencil to Native 1x
        raw.waterPreset = WaterPreset.PERFORMANCE;
        assertEquals(1, cfg.getAntiAliasing());
        assertEquals(2, cfg.getReflectionResolution());
        assertTrue(cfg.isStencilNative1x());
        assertTrue(cfg.isFastStencilDepthBlit());
        assertTrue(cfg.isSkipCaveReflections());
        assertFalse(cfg.isReflectiveItems());

        // Switching back to Custom restores user settings intact
        raw.waterPreset = WaterPreset.CUSTOM;
        assertEquals(3, cfg.getAntiAliasing());
        assertEquals(0.25F, cfg.getWaterColorR(), 1e-6F);
        assertEquals(BiomeTintMode.VIVID, cfg.getBiomeTintMode());
    }

    @Test
    void gcapiAnnotationsBoxedTypesFactoriesAndChangeListenerAreValid() throws Exception {
        Field rootField = WaterShaderGcapiConfig.class.getDeclaredField("CONFIG");
        ConfigRoot rootAnnotation = rootField.getAnnotation(ConfigRoot.class);
        assertNotNull(rootAnnotation);
        assertEquals("config", rootAnnotation.value());
        assertFalse(rootAnnotation.visibleName().isEmpty());

        assertAllConfigFieldsAreNonPrimitive(WaterShaderGcapiConfig.ConfigFields.class);
        assertAllConfigFieldsAreNonPrimitive(WaterShaderGcapiConfig.PerformanceConfig.class);
        assertAllConfigFieldsAreNonPrimitive(WaterShaderGcapiConfig.ReflectionsConfig.class);
        assertAllConfigFieldsAreNonPrimitive(WaterShaderGcapiConfig.SurfaceConfig.class);
        assertAllConfigFieldsAreNonPrimitive(WaterShaderGcapiConfig.ColorsConfig.class);

        WaterShaderConfigFactories factories = new WaterShaderConfigFactories();
        ImmutableMap.Builder<Type, SeptFunction<String, ConfigEntry, Field, Object, Boolean, Object, Object, ConfigEntryHandler<?>>> loadBuilder = ImmutableMap.builder();
        ImmutableMap.Builder<Type, Function<Object, Object>> saveBuilder = ImmutableMap.builder();
        factories.provideLoadFactories(loadBuilder);
        factories.provideSaveFactories(saveBuilder);
        var loadMap = loadBuilder.build();
        var saveMap = saveBuilder.build();
        assertTrue(loadMap.containsKey(WaterPreset.class));
        assertTrue(loadMap.containsKey(WaterMode.class));
        assertTrue(loadMap.containsKey(ReflectionResolution.class));
        assertTrue(loadMap.containsKey(StencilResolution.class));
        assertTrue(loadMap.containsKey(BiomeTintMode.class));
        assertTrue(saveMap.containsKey(WaterPreset.class));
        assertTrue(saveMap.containsKey(WaterMode.class));
        assertTrue(saveMap.containsKey(ReflectionResolution.class));
        assertTrue(saveMap.containsKey(StencilResolution.class));
        assertTrue(saveMap.containsKey(BiomeTintMode.class));

        assertEquals(4, WaterPreset.ordinalFromYamlValue("performance", WaterPreset.CUSTOM));
        assertEquals(1, StencilResolution.ordinalFromYamlValue("1x", StencilResolution.MATCH_MAIN));
        assertEquals(2, BiomeTintMode.ordinalFromYamlValue("vivid", BiomeTintMode.OFF));

        AtomicInteger notifications = new AtomicInteger();
        WaterShaderConfig.setChangeListener(notifications::incrementAndGet);
        try {
            WaterShaderConfigListener listener = new WaterShaderConfigListener();
            listener.PostConfigLoaded(2);
            listener.onPreConfigSaved(1, null, null);
            assertEquals(2, notifications.get());
        } finally {
            WaterShaderConfig.setChangeListener(null);
        }
    }

    private static void assertAllConfigFieldsAreNonPrimitive(Class<?> clazz) {
        int annotatedCount = 0;
        for (Field field : clazz.getDeclaredFields()) {
            boolean isEntry = field.isAnnotationPresent(ConfigEntry.class);
            boolean isCategory = field.isAnnotationPresent(ConfigCategory.class);
            if (isEntry || isCategory) {
                annotatedCount++;
                assertFalse(
                    field.getType().isPrimitive(),
                    () -> "GCAPI 3 requires boxed types, not primitive on " + clazz.getSimpleName() + "." + field.getName()
                );
            }
        }
        assertTrue(annotatedCount > 0, () -> "Expected annotated GCAPI fields in " + clazz.getSimpleName());
    }
}
