package net.watershader.config;

import java.util.Locale;

/**
 * Reflection FBO resolution scale tier selectable in GlassConfigAPI 3.
 */
public enum ReflectionResolution {
    FULL(1, "Full"),
    HALF(2, "Half"),
    QUARTER(4, "Quarter");

    private final int divisor;
    private final String displayName;

    ReflectionResolution(int divisor, String displayName) {
        this.divisor = divisor;
        this.displayName = displayName;
    }

    public int getDivisor() {
        return divisor;
    }

    public static ReflectionResolution fromOrdinal(int ordinal) {
        ReflectionResolution[] values = values();
        if (ordinal <= 0) {
            return FULL;
        }
        if (ordinal >= values.length - 1) {
            return QUARTER;
        }
        return values[ordinal];
    }

    public static ReflectionResolution fromDivisor(int divisor) {
        if (divisor <= 1) {
            return FULL;
        }
        if (divisor <= 2) {
            return HALF;
        }
        return QUARTER;
    }

    public static int ordinalFromYamlValue(Object raw, ReflectionResolution fallback) {
        if (raw instanceof ReflectionResolution resolution) {
            return resolution.ordinal();
        }
        if (raw instanceof Number number) {
            return fromOrdinal(number.intValue()).ordinal();
        }
        if (raw instanceof String text) {
            String normalized = text.trim().toLowerCase(Locale.ROOT);
            switch (normalized) {
                case "full", "1/1", "1x" -> {
                    return FULL.ordinal();
                }
                case "half", "1/2", "0.5", "0.5x" -> {
                    return HALF.ordinal();
                }
                case "quarter", "1/4", "0.25", "0.25x" -> {
                    return QUARTER.ordinal();
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
