package net.watershader.math;

import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.watershader.config.BiomeTintMode;

/**
 * Pure biome-aware water palette resolver and EMA smoother for Beta 1.7.3 biomes.
 * Keeps classic global {@code water_color} untouched when {@link BiomeTintMode#OFF} is active.
 */
public final class BiomeWaterColorHelper {
    private static final double TRANSITION_TAU_SECONDS = 0.75;
    private static final int CROSS_OFFSET_BLOCKS = 12;

    private BiomeWaterColorHelper() {}

    /**
     * Resolves a curated Beta 1.7.3 biome water RGB palette into {@code outRgb[0..2]}
     * (matching the shader's {@code water_color} tint range).
     */
    public static void resolveBiomePalette(Biome biome, float[] outRgb) {
        if (biome == Biome.SWAMPLAND) {
            outRgb[0] = 0.14F;
            outRgb[1] = 0.36F;
            outRgb[2] = 0.22F;
        } else if (biome == Biome.RAINFOREST) {
            outRgb[0] = 0.00F;
            outRgb[1] = 0.64F;
            outRgb[2] = 0.52F;
        } else if (biome == Biome.DESERT || biome == Biome.SAVANNA) {
            outRgb[0] = 0.04F;
            outRgb[1] = 0.56F;
            outRgb[2] = 0.48F;
        } else if (biome == Biome.TAIGA || biome == Biome.TUNDRA || biome == Biome.ICE_DESERT) {
            outRgb[0] = 0.02F;
            outRgb[1] = 0.34F;
            outRgb[2] = 0.48F;
        } else if (biome == Biome.HELL) {
            outRgb[0] = 0.22F;
            outRgb[1] = 0.18F;
            outRgb[2] = 0.20F;
        } else {
            // Forest, Seasonal Forest, Plains, Shrubland, Sky, or modded fallback
            outRgb[0] = 0.00F;
            outRgb[1] = 0.50F;
            outRgb[2] = 0.38F;
        }
    }

    /**
     * Resolves a biome water RGB palette from a biome name/key string for pure unit testing
     * without requiring static {@link Biome} initialization.
     */
    public static void resolveBiomePaletteByName(String biomeName, float[] outRgb) {
        String key = biomeName == null ? "" : biomeName.trim().toLowerCase(java.util.Locale.ROOT);
        if (key.contains("swamp")) {
            outRgb[0] = 0.14F;
            outRgb[1] = 0.36F;
            outRgb[2] = 0.22F;
        } else if (key.contains("rainforest") || key.contains("jungle")) {
            outRgb[0] = 0.00F;
            outRgb[1] = 0.64F;
            outRgb[2] = 0.52F;
        } else if (key.contains("desert") && !key.contains("ice")) {
            outRgb[0] = 0.04F;
            outRgb[1] = 0.56F;
            outRgb[2] = 0.48F;
        } else if (key.contains("savanna")) {
            outRgb[0] = 0.04F;
            outRgb[1] = 0.56F;
            outRgb[2] = 0.48F;
        } else if (key.contains("taiga") || key.contains("tundra") || key.contains("ice")) {
            outRgb[0] = 0.02F;
            outRgb[1] = 0.34F;
            outRgb[2] = 0.48F;
        } else if (key.contains("hell") || key.contains("nether")) {
            outRgb[0] = 0.22F;
            outRgb[1] = 0.18F;
            outRgb[2] = 0.20F;
        } else {
            outRgb[0] = 0.00F;
            outRgb[1] = 0.50F;
            outRgb[2] = 0.38F;
        }
    }

    /**
     * Averages a 5-point cross around {@code (camX, camZ)} in {@code biomeSource} so biome water
     * color transitions softly as the player crosses biome borders.
     */
    public static void sampleAreaBiomePalette(BiomeSource biomeSource, int camX, int camZ, float[] outRgb) {
        if (biomeSource == null) {
            outRgb[0] = 0.00F;
            outRgb[1] = 0.50F;
            outRgb[2] = 0.35F;
            return;
        }
        float[] tmp = new float[3];
        float sumR = 0.0F;
        float sumG = 0.0F;
        float sumB = 0.0F;

        // Center carries weight 2, four cardinal points +/-12 blocks carry weight 1 each (total = 6)
        resolveBiomePalette(biomeSource.getBiome(camX, camZ), tmp);
        sumR += tmp[0] * 2.0F;
        sumG += tmp[1] * 2.0F;
        sumB += tmp[2] * 2.0F;

        resolveBiomePalette(biomeSource.getBiome(camX + CROSS_OFFSET_BLOCKS, camZ), tmp);
        sumR += tmp[0];
        sumG += tmp[1];
        sumB += tmp[2];

        resolveBiomePalette(biomeSource.getBiome(camX - CROSS_OFFSET_BLOCKS, camZ), tmp);
        sumR += tmp[0];
        sumG += tmp[1];
        sumB += tmp[2];

        resolveBiomePalette(biomeSource.getBiome(camX, camZ + CROSS_OFFSET_BLOCKS), tmp);
        sumR += tmp[0];
        sumG += tmp[1];
        sumB += tmp[2];

        resolveBiomePalette(biomeSource.getBiome(camX, camZ - CROSS_OFFSET_BLOCKS), tmp);
        sumR += tmp[0];
        sumG += tmp[1];
        sumB += tmp[2];

        outRgb[0] = sumR / 6.0F;
        outRgb[1] = sumG / 6.0F;
        outRgb[2] = sumB / 6.0F;
    }

    /**
     * Blends user-configured {@code baseRgb} toward {@code biomeRgb} according to {@code mode}.
     */
    public static void blendWithBaseColor(
            float baseR,
            float baseG,
            float baseB,
            float biomeR,
            float biomeG,
            float biomeB,
            BiomeTintMode mode,
            float[] outRgb) {
        float w = (mode != null) ? mode.getBlendWeight() : 0.0F;
        outRgb[0] = WaterEnvironmentMath.mix(baseR, biomeR, w);
        outRgb[1] = WaterEnvironmentMath.mix(baseG, biomeG, w);
        outRgb[2] = WaterEnvironmentMath.mix(baseB, biomeB, w);
    }

    /**
     * Exponentially smooths current RGB toward target RGB with time constant {@code 0.75s}.
     */
    public static void stepSmoothedColor(float[] currentRgb, float[] targetRgb, double dtSeconds, boolean snap) {
        if (snap || !Double.isFinite(dtSeconds) || dtSeconds <= 0.0) {
            currentRgb[0] = targetRgb[0];
            currentRgb[1] = targetRgb[1];
            currentRgb[2] = targetRgb[2];
            return;
        }
        double clampedDt = Math.min(dtSeconds, 0.25);
        float alpha = (float) (1.0 - Math.exp(-clampedDt / TRANSITION_TAU_SECONDS));
        for (int i = 0; i < 3; i++) {
            float next = currentRgb[i] + (targetRgb[i] - currentRgb[i]) * alpha;
            currentRgb[i] = Math.abs(targetRgb[i] - next) < 1e-4F ? targetRgb[i] : next;
        }
    }
}
