package net.watershader.config;

import java.util.Locale;

/**
 * Stencil FBO resolution mode selectable in GlassConfigAPI 3.
 */
public enum StencilResolution {
    MATCH_MAIN(false, "Match Main"),
    NATIVE_1X(true, "Native 1x");

    private final boolean native1x;
    private final String displayName;

    StencilResolution(boolean native1x, String displayName) {
        this.native1x = native1x;
        this.displayName = displayName;
    }

    public boolean isNative1x() {
        return native1x;
    }

    public static StencilResolution fromOrdinal(int ordinal) {
        return ordinal >= 1 ? NATIVE_1X : MATCH_MAIN;
    }

    public static int ordinalFromYamlValue(Object raw, StencilResolution fallback) {
        if (raw instanceof StencilResolution res) {
            return res.ordinal();
        }
        if (raw instanceof Number number) {
            return fromOrdinal(number.intValue()).ordinal();
        }
        if (raw instanceof String text) {
            String normalized = text.trim().toLowerCase(Locale.ROOT);
            switch (normalized) {
                case "0", "match", "match_main", "match main", "full" -> {
                    return MATCH_MAIN.ordinal();
                }
                case "1", "native", "native_1x", "native 1x", "1x" -> {
                    return NATIVE_1X.ordinal();
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
