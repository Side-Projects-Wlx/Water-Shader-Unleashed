package net.watershader.config;

import java.util.Locale;

/**
 * Water rendering pipeline mode selectable in GlassConfigAPI 3.
 */
public enum WaterMode {
    OFF(0, "Off"),
    REFRACTION_ONLY(1, "Refraction Only"),
    REFLECTION_AND_REFRACTION(2, "Reflect & Refract");

    private final int modeId;
    private final String displayName;

    WaterMode(int modeId, String displayName) {
        this.modeId = modeId;
        this.displayName = displayName;
    }

    public int getModeId() {
        return modeId;
    }

    public static WaterMode fromOrdinal(int ordinal) {
        WaterMode[] values = values();
        if (ordinal <= 0) {
            return OFF;
        }
        if (ordinal >= values.length - 1) {
            return REFLECTION_AND_REFRACTION;
        }
        return values[ordinal];
    }

    public static int ordinalFromYamlValue(Object raw, WaterMode fallback) {
        if (raw instanceof WaterMode mode) {
            return mode.ordinal();
        }
        if (raw instanceof Number number) {
            return fromOrdinal(number.intValue()).ordinal();
        }
        if (raw instanceof String text) {
            String normalized = text.trim().toLowerCase(Locale.ROOT);
            switch (normalized) {
                case "0", "off", "disabled" -> {
                    return OFF.ordinal();
                }
                case "1", "refraction", "refraction_only", "refraction only" -> {
                    return REFRACTION_ONLY.ordinal();
                }
                case "2", "full", "reflection_and_refraction", "reflect & refract" -> {
                    return REFLECTION_AND_REFRACTION.ordinal();
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
