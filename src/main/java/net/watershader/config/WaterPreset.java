package net.watershader.config;

import java.util.Locale;

/**
 * Curated live style and performance profiles selectable in GlassConfigAPI 3.
 * Selecting {@link #CUSTOM} uses all individual category sliders/toggles as configured.
 */
public enum WaterPreset {
    CUSTOM(0, "Custom"),
    CLASSIC_V4C(1, "Classic v4c"),
    MODERN_BALANCED(2, "Modern Balanced"),
    CRYSTAL_TROPICAL(3, "Crystal Tropical"),
    PERFORMANCE(4, "Performance");

    private final int id;
    private final String displayName;

    WaterPreset(int id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public int getId() {
        return id;
    }

    public static WaterPreset fromOrdinal(int ordinal) {
        WaterPreset[] values = values();
        if (ordinal <= 0) {
            return CUSTOM;
        }
        if (ordinal >= values.length - 1) {
            return PERFORMANCE;
        }
        return values[ordinal];
    }

    public static int ordinalFromYamlValue(Object raw, WaterPreset fallback) {
        if (raw instanceof WaterPreset preset) {
            return preset.ordinal();
        }
        if (raw instanceof Number number) {
            return fromOrdinal(number.intValue()).ordinal();
        }
        if (raw instanceof String text) {
            String normalized = text.trim().toLowerCase(Locale.ROOT);
            switch (normalized) {
                case "0", "custom" -> {
                    return CUSTOM.ordinal();
                }
                case "1", "classic", "classic_v4c", "classic v4c", "v4c" -> {
                    return CLASSIC_V4C.ordinal();
                }
                case "2", "modern", "balanced", "modern_balanced", "modern balanced" -> {
                    return MODERN_BALANCED.ordinal();
                }
                case "3", "tropical", "crystal", "crystal_tropical", "crystal tropical" -> {
                    return CRYSTAL_TROPICAL.ordinal();
                }
                case "4", "perf", "performance", "fast" -> {
                    return PERFORMANCE.ordinal();
                }
                default -> {
                    try {
                        return fromOrdinal(Integer.parseInt(normalized)).ordinal();
                    } catch (NumberFormatException ignored) {
                        return fallback.ordinal();
                    }
                }
            }
        }
        return fallback.ordinal();
    }

    @Override
    public String toString() {
        return displayName;
    }
}
