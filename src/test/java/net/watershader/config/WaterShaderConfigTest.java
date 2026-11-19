package net.watershader.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WaterShaderConfigTest {
    private static final Logger LOGGER = LogManager.getLogger("watershader-test");

    @Test
    void autoCreatesDefaultConfigAndRoundTrips(@TempDir Path tempDir) {
        Path configPath = tempDir.resolve("config").resolve("watershader.txt");
        assertTrue(Files.notExists(configPath));

        WaterShaderConfig created = WaterShaderConfig.loadOrCreate(configPath, LOGGER);
        assertTrue(Files.isRegularFile(configPath));
        assertBeta173Defaults(created);

        WaterShaderConfig reloaded = WaterShaderConfig.loadOrCreate(configPath, LOGGER);
        assertBeta173Defaults(reloaded);
    }

    @Test
    void parsesCustomValuesCommentsAndEmptyExcludeList() throws IOException {
        String raw = """
            // Leading comment
            # Hash comment
            water_mode 1 // inline comment
            anti_aliasing 4
            reflection_resolution 2
            reflective_items 0
            reflective_clouds 1
            reflect_player off
            render_v3 false
            water_surface_transparency 0.25
            waterfall_transparency 0.6
            water_color 0.1/0.2/0.3
            waterfall_color 0.8/0.9/1.0
            waterfall_fog no
            exclude_list
            ssr_enabled 0
            ssr_quality 2
            fresnel_strength 1.5
            ripple_strength 0.75
            underwater_effect 0
            """;

        WaterShaderConfig cfg = WaterShaderConfig.parse(new BufferedReader(new StringReader(raw)), LOGGER);
        assertEquals(1, cfg.getWaterMode());
        assertEquals(4, cfg.getAntiAliasing());
        assertEquals(2, cfg.getReflectionResolution());
        assertFalse(cfg.isReflectiveItems());
        assertTrue(cfg.isReflectiveClouds());
        assertFalse(cfg.isReflectPlayer());
        assertFalse(cfg.isRenderV3());
        assertEquals(0.0F, cfg.getRenderV3Float(), 1e-6F);
        assertEquals(0.25F, cfg.getWaterSurfaceTransparency(), 1e-6F);
        assertEquals(0.6F, cfg.getWaterfallTransparency(), 1e-6F);
        assertArrayEquals(new float[]{0.1F, 0.2F, 0.3F}, cfg.getWaterColor(), 1e-6F);
        assertEquals(0.1F, cfg.getWaterColorR(), 1e-6F);
        assertEquals(0.2F, cfg.getWaterColorG(), 1e-6F);
        assertEquals(0.3F, cfg.getWaterColorB(), 1e-6F);
        assertArrayEquals(new float[]{0.8F, 0.9F, 1.0F}, cfg.getWaterfallColor(), 1e-6F);
        assertEquals(0.8F, cfg.getWaterfallColorR(), 1e-6F);
        assertEquals(0.9F, cfg.getWaterfallColorG(), 1e-6F);
        assertEquals(1.0F, cfg.getWaterfallColorB(), 1e-6F);
        assertFalse(cfg.isWaterfallFog());
        assertTrue(cfg.getExcludeList().isEmpty());
        assertFalse(cfg.isSsrEnabled());
        assertEquals(2, cfg.getSsrQuality());
        assertEquals(1.5F, cfg.getFresnelStrength(), 1e-6F);
        assertEquals(0.75F, cfg.getRippleStrength(), 1e-6F);
        assertFalse(cfg.isUnderwaterEffect());
    }

    @Test
    void existingV1ConfigWithoutPhase6KeysUsesBalancedDefaults() throws IOException {
        String v1Raw = """
            water_mode 2
            reflection_resolution 1
            water_color 0.0/0.5/0.35
            """;
        WaterShaderConfig cfg = WaterShaderConfig.parse(new BufferedReader(new StringReader(v1Raw)), LOGGER);
        assertBeta173Defaults(cfg);
    }

    @Test
    void clampsOutOfRangeAndIgnoresMalformedEntries() throws IOException {
        String raw = """
            water_mode 99
            anti_aliasing 0
            reflection_resolution -3
            water_surface_transparency -0.5
            waterfall_transparency 2.5
            water_color bad_value
            waterfall_color 1.0/NaN/0.5
            ssr_quality -1
            fresnel_strength -5.0
            exclude_list 8, 9 79
            unknown_future_key some_val
            """;

        WaterShaderConfig cfg = WaterShaderConfig.parse(new BufferedReader(new StringReader(raw)), LOGGER);
        assertEquals(2, cfg.getWaterMode());
        assertEquals(1, cfg.getAntiAliasing());
        assertEquals(1, cfg.getReflectionResolution());
        assertEquals(0.0F, cfg.getWaterSurfaceTransparency(), 1e-6F);
        assertEquals(1.0F, cfg.getWaterfallTransparency(), 1e-6F);
        assertArrayEquals(new float[]{0.0F, 0.5F, 0.35F}, cfg.getWaterColor(), 1e-6F);
        assertArrayEquals(new float[]{1.0F, 1.0F, 1.0F}, cfg.getWaterfallColor(), 1e-6F);
        assertEquals(0, cfg.getSsrQuality());
        assertEquals(0.0F, cfg.getFresnelStrength(), 1e-6F);
        assertEquals(List.of("8", "9", "79"), cfg.getExcludeList());
    }

    @Test
    void supportsUnlockedReflectionResolutionTiersCyclingAndInPlaceSave(@TempDir Path tempDir) throws IOException {
        assertEquals(1, new WaterShaderConfig().getReflectionResolution());
        assertEquals(1, WaterShaderConfig.parse(new BufferedReader(new StringReader("reflection_resolution 1\n")), LOGGER).getReflectionResolution());
        assertEquals(1, WaterShaderConfig.parse(new BufferedReader(new StringReader("reflection_resolution full\n")), LOGGER).getReflectionResolution());
        assertEquals(1, WaterShaderConfig.parse(new BufferedReader(new StringReader("reflection_resolution 1/1\n")), LOGGER).getReflectionResolution());
        assertEquals(2, WaterShaderConfig.parse(new BufferedReader(new StringReader("reflection_resolution half\n")), LOGGER).getReflectionResolution());
        assertEquals(2, WaterShaderConfig.parse(new BufferedReader(new StringReader("reflection_resolution 2\n")), LOGGER).getReflectionResolution());
        assertEquals(2, WaterShaderConfig.parse(new BufferedReader(new StringReader("reflection_resolution 1/2\n")), LOGGER).getReflectionResolution());
        assertEquals(4, WaterShaderConfig.parse(new BufferedReader(new StringReader("reflection_resolution quarter\n")), LOGGER).getReflectionResolution());
        assertEquals(4, WaterShaderConfig.parse(new BufferedReader(new StringReader("reflection_resolution 4\n")), LOGGER).getReflectionResolution());
        assertEquals(4, WaterShaderConfig.parse(new BufferedReader(new StringReader("reflection_resolution 1/4\n")), LOGGER).getReflectionResolution());
        assertEquals(4, WaterShaderConfig.parse(new BufferedReader(new StringReader("reflection_resolution 8\n")), LOGGER).getReflectionResolution());

        WaterShaderConfig cfg = new WaterShaderConfig();
        assertEquals("Full (1/1)", WaterShaderConfig.formatReflectionResolutionLabel(cfg.getReflectionResolution()));
        assertEquals(2, cfg.cycleReflectionResolution());
        assertEquals("Half (1/2)", WaterShaderConfig.formatReflectionResolutionLabel(cfg.getReflectionResolution()));
        assertEquals(4, cfg.cycleReflectionResolution());
        assertEquals("Quarter (1/4)", WaterShaderConfig.formatReflectionResolutionLabel(cfg.getReflectionResolution()));
        assertEquals(1, cfg.cycleReflectionResolution());

        // In-place save preserves comments and other custom keys while updating reflection_resolution
        Path configPath = tempDir.resolve("watershader.txt");
        Files.writeString(configPath, "// Custom user comment\nwater_mode 2\nreflection_resolution 1\nwater_color 0.1/0.4/0.3\n");
        assertTrue(WaterShaderConfig.saveOption(configPath, "reflection_resolution", "2", LOGGER));
        String savedText = Files.readString(configPath);
        assertTrue(savedText.contains("// Custom user comment"));
        assertTrue(savedText.contains("reflection_resolution 2"));
        assertTrue(savedText.contains("water_color 0.1/0.4/0.3"));

        WaterShaderConfig reloaded = WaterShaderConfig.loadOrCreate(configPath, LOGGER);
        assertEquals(2, reloaded.getReflectionResolution());
        assertArrayEquals(new float[]{0.1F, 0.4F, 0.3F}, reloaded.getWaterColor(), 1e-6F);
    }

    @Test
    void defaultConfigTextOmitsDeadNoOpKeysAndDevHeaders() {
        String text = WaterShaderConfig.defaultConfigText();
        assertFalse(text.contains("ssr_enabled"));
        assertFalse(text.contains("ssr_quality"));
        assertFalse(text.contains("PHASE 6"));
        assertTrue(text.contains("water_mode 2"));
        assertTrue(text.contains("underwater_effect 1"));
    }

    private static void assertBeta173Defaults(WaterShaderConfig cfg) {
        assertEquals(2, cfg.getWaterMode());
        assertEquals(2, cfg.getAntiAliasing());
        assertEquals(1, cfg.getReflectionResolution());
        assertTrue(cfg.isReflectiveItems());
        assertFalse(cfg.isReflectiveClouds());
        assertTrue(cfg.isReflectPlayer());
        assertTrue(cfg.isRenderV3());
        assertEquals(1.0F, cfg.getRenderV3Float(), 1e-6F);
        assertEquals(0.1F, cfg.getWaterSurfaceTransparency(), 1e-6F);
        assertEquals(0.35F, cfg.getWaterfallTransparency(), 1e-6F);
        assertArrayEquals(new float[]{0.0F, 0.5F, 0.35F}, cfg.getWaterColor(), 1e-6F);
        assertArrayEquals(new float[]{1.0F, 1.0F, 1.0F}, cfg.getWaterfallColor(), 1e-6F);
        assertTrue(cfg.isWaterfallFog());
        assertTrue(cfg.getExcludeList().isEmpty());
        assertTrue(cfg.isSsrEnabled());
        assertEquals(1, cfg.getSsrQuality());
        assertEquals(1.0F, cfg.getFresnelStrength(), 1e-6F);
        assertEquals(1.0F, cfg.getRippleStrength(), 1e-6F);
        assertTrue(cfg.isUnderwaterEffect());
    }
}
