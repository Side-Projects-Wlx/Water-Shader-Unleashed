package net.watershader.config;

import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Configuration for Water Shader, preserving original v4c option keys and
 * Beta 1.7.3 defaults alongside v1 enhancement settings in config/watershader.txt.
 */
public final class WaterShaderConfig {
    public static final String CONFIG_FILE_NAME = "watershader.txt";

    private int waterMode = 2;
    private int antiAliasing = 2;
    private volatile int reflectionResolution = 1;
    private boolean reflectiveItems = true;
    private boolean reflectiveClouds = false;
    private boolean reflectPlayer = true;

    private boolean renderV3 = true;
    private float waterSurfaceTransparency = 0.1F;
    private float waterfallTransparency = 0.35F;
    private float[] waterColor = new float[]{0.0F, 0.5F, 0.35F};
    private float[] waterfallColor = new float[]{1.0F, 1.0F, 1.0F};
    private boolean waterfallFog = true;
    private List<String> excludeList = Collections.emptyList();

    private boolean ssrEnabled = true;
    private int ssrQuality = 1;
    private float fresnelStrength = 1.0F;
    private float rippleStrength = 1.0F;
    private boolean underwaterEffect = true;

    /**
     * Loads config from {@code configPath}, writing defaults first if missing.
     */
    public static WaterShaderConfig loadOrCreate(Path configPath, Logger logger) {
        try {
            if (Files.notExists(configPath)) {
                Path parent = configPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(configPath, defaultConfigText(), StandardCharsets.UTF_8);
                logger.info("Created default Water Shader config at {}", configPath);
            }
            try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                WaterShaderConfig config = parse(reader, logger);
                logger.info(
                    "Loaded config ({}): water_mode={}, aa={}, refl_res={}, items={}, clouds={}, player={}, v3={}, water_alpha={}, fall_alpha={}, ssr={} (q={})",
                    configPath.getFileName(),
                    config.waterMode,
                    config.antiAliasing,
                    config.reflectionResolution,
                    config.reflectiveItems,
                    config.reflectiveClouds,
                    config.reflectPlayer,
                    config.renderV3,
                    config.waterSurfaceTransparency,
                    config.waterfallTransparency,
                    config.ssrEnabled,
                    config.ssrQuality
                );
                return config;
            }
        } catch (IOException ex) {
            logger.error("Failed reading config at {}, falling back to defaults", configPath, ex);
            return new WaterShaderConfig();
        }
    }

    /**
     * Parses a Water Shader text config from {@code reader} using defaults for omitted or invalid entries.
     */
    public static WaterShaderConfig parse(BufferedReader reader, Logger logger) throws IOException {
        WaterShaderConfig config = new WaterShaderConfig();
        String rawLine;
        int lineNumber = 0;
        while ((rawLine = reader.readLine()) != null) {
            lineNumber++;
            String cleaned = stripComments(rawLine).trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            int splitIdx = findWhitespaceIndex(cleaned);
            String key = splitIdx < 0 ? cleaned : cleaned.substring(0, splitIdx);
            String value = splitIdx < 0 ? "" : cleaned.substring(splitIdx + 1).trim();
            config.applyOption(key, value, lineNumber, logger);
        }
        return config;
    }

    /**
     * Renders the default commented config file matching Beta 1.7.3 Water Shader v4c keys.
     */
    public static String defaultConfigText() {
        return String.join(
            System.lineSeparator(),
            "// Water Shader Configuration (reloaded on game restart)",
            "",
            "// Water rendering mode (0: off, 1: refraction only, 2: reflection and refraction)",
            "water_mode 2",
            "",
            "// PERFORMANCE SETTINGS",
            "// Anti-aliasing multiplier on internal buffers (>= 1; 2 matches original 4x pixel count)",
            "anti_aliasing 2",
            "",
            "// Reflection FBO resolution divisor (1 = Full 1/1, 2 = Half 1/2, 4 = Quarter 1/4)",
            "reflection_resolution 1",
            "",
            "// Reflect entities/items/mobs in water (1 = on, 0 = off)",
            "reflective_items 1",
            "",
            "// Reflect clouds in water (1 = on, 0 = off)",
            "reflective_clouds 0",
            "",
            "// Reflect local player in water (1 = on, 0 = off)",
            "reflect_player 1",
            "",
            "// CLASSIC RENDER SETTINGS",
            "// v3 brightness lift setting (1 = on, 0 = off)",
            "render_v3 1",
            "",
            "// Water surface alpha between 0.0 and 1.0",
            "water_surface_transparency 0.1",
            "",
            "// Waterfall / flowing slope alpha between 0.0 and 1.0",
            "waterfall_transparency 0.35",
            "",
            "// Deep water tint color (R/G/B floats)",
            "water_color 0.0/0.5/0.35",
            "",
            "// Multiplicative waterfall tint color (R/G/B floats)",
            "waterfall_color 1.0/1.0/1.0",
            "",
            "// Enable fog handling on waterfalls (1 = on, 0 = off)",
            "waterfall_fog 1",
            "",
            "// Comma- or space-separated block IDs or translation keys to exclude from water effects",
            "exclude_list",
            "",
            "// SURFACE & UNDERWATER SETTINGS",
            "// Fresnel reflection curve multiplier (>= 0.0)",
            "fresnel_strength 1.0",
            "",
            "// Surface ripple distortion multiplier (>= 0.0)",
            "ripple_strength 1.0",
            "",
            "// Enable shader underwater tint and wobble (1 = on, 0 = off)",
            "underwater_effect 1",
            ""
        );
    }

    private void applyOption(String key, String value, int lineNumber, Logger logger) {
        switch (key) {
            case "water_mode" ->
                this.waterMode = clampInt(parseInt(key, value, this.waterMode, lineNumber, logger), 0, 2, key, logger);
            case "anti_aliasing" ->
                this.antiAliasing = clampMinInt(parseInt(key, value, this.antiAliasing, lineNumber, logger), 1, key, logger);
            case "reflection_resolution" ->
                this.reflectionResolution = parseReflectionResolution(key, value, this.reflectionResolution, lineNumber, logger);
            case "reflective_items" ->
                this.reflectiveItems = parseBoolean(key, value, this.reflectiveItems, lineNumber, logger);
            case "reflective_clouds" ->
                this.reflectiveClouds = parseBoolean(key, value, this.reflectiveClouds, lineNumber, logger);
            case "reflect_player" ->
                this.reflectPlayer = parseBoolean(key, value, this.reflectPlayer, lineNumber, logger);
            case "render_v3" ->
                this.renderV3 = parseBoolean(key, value, this.renderV3, lineNumber, logger);
            case "water_surface_transparency" ->
                this.waterSurfaceTransparency = clampFloat(parseFloat(key, value, this.waterSurfaceTransparency, lineNumber, logger), 0.0F, 1.0F, key, logger);
            case "waterfall_transparency" ->
                this.waterfallTransparency = clampFloat(parseFloat(key, value, this.waterfallTransparency, lineNumber, logger), 0.0F, 1.0F, key, logger);
            case "water_color" ->
                this.waterColor = parseRgb(key, value, this.waterColor, lineNumber, logger);
            case "waterfall_color" ->
                this.waterfallColor = parseRgb(key, value, this.waterfallColor, lineNumber, logger);
            case "waterfall_fog" ->
                this.waterfallFog = parseBoolean(key, value, this.waterfallFog, lineNumber, logger);
            case "exclude_list" ->
                this.excludeList = parseList(value);
            case "ssr_enabled" ->
                this.ssrEnabled = parseBoolean(key, value, this.ssrEnabled, lineNumber, logger);
            case "ssr_quality" ->
                this.ssrQuality = clampInt(parseInt(key, value, this.ssrQuality, lineNumber, logger), 0, 2, key, logger);
            case "fresnel_strength" ->
                this.fresnelStrength = clampMinFloat(parseFloat(key, value, this.fresnelStrength, lineNumber, logger), 0.0F, key, logger);
            case "ripple_strength" ->
                this.rippleStrength = clampMinFloat(parseFloat(key, value, this.rippleStrength, lineNumber, logger), 0.0F, key, logger);
            case "underwater_effect" ->
                this.underwaterEffect = parseBoolean(key, value, this.underwaterEffect, lineNumber, logger);
            default -> logger.warn("Ignoring unknown config key '{}' at line {}", key, lineNumber);
        }
    }

    private static String stripComments(String line) {
        if (line.startsWith("//") || line.startsWith("#")) {
            return "";
        }
        int slashComment = findInlineComment(line, "//");
        int hashComment = findInlineComment(line, "#");
        int cut = Integer.MAX_VALUE;
        if (slashComment >= 0) {
            cut = slashComment;
        }
        if (hashComment >= 0 && hashComment < cut) {
            cut = hashComment;
        }
        return cut == Integer.MAX_VALUE ? line : line.substring(0, cut);
    }

    private static int findInlineComment(String line, String marker) {
        int idx = line.indexOf(marker);
        while (idx > 0 && !Character.isWhitespace(line.charAt(idx - 1))) {
            idx = line.indexOf(marker, idx + 1);
        }
        return idx;
    }

    private static int findWhitespaceIndex(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int parseInt(String key, String value, int fallback, int line, Logger logger) {
        if (value.isEmpty()) {
            logger.warn("Missing integer value for '{}' at line {}; keeping {}", key, line, fallback);
            return fallback;
        }
        try {
            return Integer.parseInt(value.split("\\s+")[0]);
        } catch (NumberFormatException ex) {
            logger.warn("Invalid integer '{}' for '{}' at line {}; keeping {}", value, key, line, fallback);
            return fallback;
        }
    }

    private static float parseFloat(String key, String value, float fallback, int line, Logger logger) {
        if (value.isEmpty()) {
            logger.warn("Missing float value for '{}' at line {}; keeping {}", key, line, fallback);
            return fallback;
        }
        try {
            float parsed = Float.parseFloat(value.split("\\s+")[0]);
            if (Float.isNaN(parsed) || Float.isInfinite(parsed)) {
                logger.warn("Non-finite float '{}' for '{}' at line {}; keeping {}", value, key, line, fallback);
                return fallback;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            logger.warn("Invalid float '{}' for '{}' at line {}; keeping {}", value, key, line, fallback);
            return fallback;
        }
    }

    private static boolean parseBoolean(String key, String value, boolean fallback, int line, Logger logger) {
        if (value.isEmpty()) {
            logger.warn("Missing boolean value for '{}' at line {}; keeping {}", key, line, fallback);
            return fallback;
        }
        String token = value.split("\\s+")[0].toLowerCase(Locale.ROOT);
        return switch (token) {
            case "1", "true", "on", "yes" -> true;
            case "0", "false", "off", "no" -> false;
            default -> {
                logger.warn("Invalid boolean '{}' for '{}' at line {}; keeping {}", value, key, line, fallback);
                yield fallback;
            }
        };
    }

    private static float[] parseRgb(String key, String value, float[] fallback, int line, Logger logger) {
        if (value.isEmpty()) {
            logger.warn("Missing R/G/B value for '{}' at line {}; keeping {}", key, line, Arrays.toString(fallback));
            return fallback;
        }
        String token = value.split("\\s+")[0];
        String[] parts = token.split("/");
        if (parts.length != 3) {
            logger.warn("Expected R/G/B triplet for '{}' at line {} (got '{}'); keeping {}", key, line, token, Arrays.toString(fallback));
            return fallback;
        }
        float[] parsed = new float[3];
        for (int i = 0; i < 3; i++) {
            try {
                float c = Float.parseFloat(parts[i]);
                if (Float.isNaN(c) || Float.isInfinite(c)) {
                    logger.warn("Non-finite color component in '{}' for '{}' at line {}", token, key, line);
                    return fallback;
                }
                parsed[i] = c;
            } catch (NumberFormatException ex) {
                logger.warn("Invalid color component in '{}' for '{}' at line {}", token, key, line);
                return fallback;
            }
        }
        return parsed;
    }

    private static List<String> parseList(String value) {
        if (value.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> items = new ArrayList<>();
        for (String token : value.split("[,\\s]+")) {
            if (!token.isEmpty()) {
                items.add(token);
            }
        }
        return Collections.unmodifiableList(items);
    }

    private static int clampInt(int v, int min, int max, String key, Logger logger) {
        if (v < min || v > max) {
            int clamped = Math.max(min, Math.min(max, v));
            logger.warn("Clamped '{}' from {} to {} (valid range {}..{})", key, v, clamped, min, max);
            return clamped;
        }
        return v;
    }

    private static int clampMinInt(int v, int min, String key, Logger logger) {
        if (v < min) {
            logger.warn("Clamped '{}' from {} to minimum {}", key, v, min);
            return min;
        }
        return v;
    }

    private static float clampFloat(float v, float min, float max, String key, Logger logger) {
        if (v < min || v > max) {
            float clamped = Math.max(min, Math.min(max, v));
            logger.warn("Clamped '{}' from {} to {} (valid range {}..{})", key, v, clamped, min, max);
            return clamped;
        }
        return v;
    }

    private static float clampMinFloat(float v, float min, String key, Logger logger) {
        if (v < min) {
            logger.warn("Clamped '{}' from {} to minimum {}", key, v, min);
            return min;
        }
        return v;
    }

    public int getWaterMode() {
        return waterMode;
    }

    public int getAntiAliasing() {
        return antiAliasing;
    }

    private static int parseReflectionResolution(String key, String value, int fallback, int line, Logger logger) {
        if (value == null || value.isEmpty()) {
            logger.warn("Missing value for '{}' at line {}; keeping {}", key, line, fallback);
            return fallback;
        }
        String token = value.split("\\s+")[0];
        String normalized = token.toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "1", "full", "1/1", "1x", "1.0", "1.0x" -> {
                return 1;
            }
            case "2", "half", "1/2", "0.5", "0.5x" -> {
                return 2;
            }
            case "4", "quarter", "1/4", "0.25", "0.25x" -> {
                return 4;
            }
            default -> {
                try {
                    int parsed = Integer.parseInt(token);
                    int snapped = normalizeReflectionResolution(parsed);
                    if (snapped != parsed) {
                        logger.warn("Clamped '{}' from {} to {} (valid divisors: 1, 2, 4)", key, parsed, snapped);
                    }
                    return snapped;
                } catch (NumberFormatException ex) {
                    logger.warn("Invalid reflection resolution '{}' for '{}' at line {}; keeping {}", value, key, line, fallback);
                    return fallback;
                }
            }
        }
    }

    /**
     * Snaps any integer divisor to the supported reflection FBO resolution tiers ({@code 1}, {@code 2}, {@code 4}).
     */
    public static int normalizeReflectionResolution(int divisor) {
        if (divisor <= 1) {
            return 1;
        }
        if (divisor <= 2) {
            return 2;
        }
        return 4;
    }

    public static String formatReflectionResolutionLabel(int divisor) {
        return switch (normalizeReflectionResolution(divisor)) {
            case 2 -> "Half (1/2)";
            case 4 -> "Quarter (1/4)";
            default -> "Full (1/1)";
        };
    }

    public int getReflectionResolution() {
        return reflectionResolution;
    }

    public void setReflectionResolution(int divisor) {
        this.reflectionResolution = normalizeReflectionResolution(divisor);
    }

    /**
     * Cycles reflection FBO resolution across {@code 1 (Full) -> 2 (Half) -> 4 (Quarter) -> 1 (Full)}
     * and returns the new divisor. Takes effect on the next frame via {@code FboManager.ensureReady}.
     */
    public int cycleReflectionResolution() {
        int next = switch (normalizeReflectionResolution(this.reflectionResolution)) {
            case 1 -> 2;
            case 2 -> 4;
            default -> 1;
        };
        this.reflectionResolution = next;
        return next;
    }

    /**
     * Updates a single key in {@code configPath} in-place (preserving user comments and other keys),
     * creating the default file first if absent or appending the key if omitted.
     */
    public static boolean saveOption(Path configPath, String key, String value, Logger logger) {
        if (configPath == null || key == null || key.isEmpty() || value == null) {
            return false;
        }
        try {
            if (Files.notExists(configPath)) {
                Path parent = configPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(configPath, defaultConfigText(), StandardCharsets.UTF_8);
            }
            List<String> lines = Files.readAllLines(configPath, StandardCharsets.UTF_8);
            List<String> updated = new ArrayList<>(lines.size() + 1);
            boolean replaced = false;
            for (String rawLine : lines) {
                String cleaned = stripComments(rawLine).trim();
                if (!cleaned.isEmpty()) {
                    int splitIdx = findWhitespaceIndex(cleaned);
                    String lineKey = splitIdx < 0 ? cleaned : cleaned.substring(0, splitIdx);
                    if (lineKey.equals(key)) {
                        if (!replaced) {
                            updated.add(key + " " + value);
                            replaced = true;
                        }
                        continue;
                    }
                }
                updated.add(rawLine);
            }
            if (!replaced) {
                updated.add(key + " " + value);
            }
            Files.write(configPath, updated, StandardCharsets.UTF_8);
            return true;
        } catch (IOException ex) {
            if (logger != null) {
                logger.error("Failed saving '{}' to {}", key, configPath, ex);
            }
            return false;
        }
    }

    public boolean isReflectiveItems() {
        return reflectiveItems;
    }

    public boolean isReflectiveClouds() {
        return reflectiveClouds;
    }

    public boolean isReflectPlayer() {
        return reflectPlayer;
    }

    public boolean isRenderV3() {
        return renderV3;
    }

    public float getRenderV3Float() {
        return renderV3 ? 1.0F : 0.0F;
    }

    public float getWaterSurfaceTransparency() {
        return waterSurfaceTransparency;
    }

    public float getWaterfallTransparency() {
        return waterfallTransparency;
    }

    public float[] getWaterColor() {
        return Arrays.copyOf(waterColor, waterColor.length);
    }

    public float getWaterColorR() {
        return waterColor[0];
    }

    public float getWaterColorG() {
        return waterColor[1];
    }

    public float getWaterColorB() {
        return waterColor[2];
    }

    public float[] getWaterfallColor() {
        return Arrays.copyOf(waterfallColor, waterfallColor.length);
    }

    public float getWaterfallColorR() {
        return waterfallColor[0];
    }

    public float getWaterfallColorG() {
        return waterfallColor[1];
    }

    public float getWaterfallColorB() {
        return waterfallColor[2];
    }

    public boolean isWaterfallFog() {
        return waterfallFog;
    }

    public List<String> getExcludeList() {
        return excludeList;
    }

    public boolean isSsrEnabled() {
        return ssrEnabled;
    }

    public int getSsrQuality() {
        return ssrQuality;
    }

    public float getFresnelStrength() {
        return fresnelStrength;
    }

    public float getRippleStrength() {
        return rippleStrength;
    }

    public boolean isUnderwaterEffect() {
        return underwaterEffect;
    }
}
