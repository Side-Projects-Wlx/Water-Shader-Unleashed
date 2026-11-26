package net.watershader.config;

import java.util.Locale;

/**
 * Biome-aware water tint blending mode selectable in GlassConfigAPI 3.
 */
public enum BiomeTintMode {
    OFF(0.0F, "Off"),
    SUBTLE(0.38F, "Subtle"),
    VIVID(0.72F, "Vivid");

    private final float blendWeight;
    private final String displayName;

    BiomeTintMode(float blendWeight, String displayName) {
        this.blendWeight = blendWeight;
        this.displayName = displayName;
    }

    public float getBlendWeight() {
        return blendWeight;
    }

    public static BiomeTintMode fromOrdinal(int ordinal) {
        BiomeTintMode[] values = values();
        if (ordinal <= 0) {
            return OFF;
        }
        if (ordinal >= values.length - 1) {
            return VIVID;
        }
        return values[ordinal];
    }

    public static int ordinalFromYamlValue(Object raw, BiomeTintMode fallback) {
        if (raw instanceof BiomeTintMode mode) {
            return mode.ordinal();
        }
        if (raw instanceof Number number) {
            return fromOrdinal(number.intValue()).ordinal();
        }
        if (raw instanceof String text) {
            String normalized = text.trim().toLowerCase(Locale.ROOT);
            switch (normalized) {
                case "0", "off", "disabled", "none" -> {
                    return OFF.ordinal();
                }
                case "1", "subtle", "low" -> {
                    return SUBTLE.ordinal();
                }
                case "2", "vivid", "strong", "high" -> {
                    return VIVID.ordinal();
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
