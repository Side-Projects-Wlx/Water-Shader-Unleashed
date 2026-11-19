package net.watershader.render;

import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.RainSplashParticle;
import net.minecraft.client.particle.WaterBubbleParticle;
import net.minecraft.client.particle.WaterSplashParticle;
import net.minecraft.client.render.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.watershader.mixin.ParticleAccessor;
import net.watershader.mixin.ParticleManagerAccessor;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Renders sleek, camera-facing, wind-slanted rain streaks and crisp OG-style square splash and
 * bubble droplets (water-color harmonized) so precipitation, splashes, and bubbles match the
 * Water Shader aesthetic cleanly without bending over surface waves.
 */
public final class ModernWeatherRenderer {
    public static final int STREAK_TEX_WIDTH = 64;
    public static final int STREAK_TEX_HEIGHT = 128;
    public static final int SPLASH_ATLAS_WIDTH = 32;
    public static final int SPLASH_ATLAS_HEIGHT = 16;
    public static final int SPLASH_TILE_COUNT = 4;

    private static final double OUTER_SPLASH_INNER_FADE_START = 5.5;
    private static final double OUTER_SPLASH_INNER_FADE_END = 9.5;
    private static final double OUTER_SPLASH_FAR_FADE_START = 18.0;
    private static final double OUTER_SPLASH_MAX_FANCY = 36.0;
    private static final double OUTER_SPLASH_MAX_FAST = 20.0;

    private static final float[] TMP_SPLASH_RGB = new float[3];

    private ModernWeatherRenderer() {}

    /**
     * Generates an anti-aliased procedural RGBA raindrop streak texture with a fine Gaussian
     * cross-section, gentle motion-blur tail, and glistening leading droplet tip.
     */
    public static byte[] generateRainStreakRgba(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Texture dimensions must be positive");
        }
        byte[] rgba = new byte[width * height * 4];
        for (int y = 0; y < height; y++) {
            // v = 0 at top trailing tail, v = 1 at bottom leading tip
            double v = (y + 0.5) / height;
            double headFade = 1.0 - smoothstep(0.84, 1.0, v);
            double tailCurve = Math.pow(v, 0.80) * headFade;
            // Subtle high-frequency droplet shimmer near the leading core
            double coreBoost = 0.72 + 0.28 * Math.exp(-Math.pow((v - 0.80) * 6.5, 2.0));
            double vert = Math.max(0.0, Math.min(1.0, tailCurve * coreBoost));

            for (int x = 0; x < width; x++) {
                double u = (x + 0.5) / width;
                double dx = Math.abs(u - 0.5) * 2.0; // 0 at center, 1 at edge
                double edgeMask = 1.0 - smoothstep(0.70, 0.96, dx);
                double lateral = Math.exp(-dx * dx * 8.5) * edgeMask;
                double alphaD = Math.max(0.0, Math.min(1.0, lateral * vert));
                int alpha = (int) Math.round(alphaD * 255.0);

                // Cool silver-white droplet filament color
                int idx = (y * width + x) * 4;
                rgba[idx] = (byte) 232;
                rgba[idx + 1] = (byte) 242;
                rgba[idx + 2] = (byte) 255;
                rgba[idx + 3] = (byte) alpha;
            }
        }
        return rgba;
    }

    /**
     * Populates a {@link ByteBuffer} with procedural raindrop streak texels.
     */
    public static void writeRainStreakPixels(ByteBuffer dst, int width, int height) {
        byte[] pixels = generateRainStreakRgba(width, height);
        dst.put(pixels);
    }

    /**
     * Generates a 2-row 4-column {@code 32x16} nearest-neighbor water particle atlas:
     * <ul>
     *   <li>Top row ({@code y=0..7}): 4 sparse 8x8 droplet clusters for rain and splash particles</li>
     *   <li>Bottom row ({@code y=8..15}): 4 compact 8x8 hollow pixel bubble/foam tiles for water bubbles</li>
     * </ul>
     */
    public static byte[] generateSplashAtlasRgba(int width, int height) {
        if (width < SPLASH_TILE_COUNT || height < 16 || width % SPLASH_TILE_COUNT != 0) {
            throw new IllegalArgumentException("Splash atlas dimensions invalid");
        }
        int tileW = width / SPLASH_TILE_COUNT;
        byte[] rgba = new byte[width * height * 4];
        if (tileW >= 8) {
            // Row 0 (y = 0..7): RainSplashParticle / WaterSplashParticle crisp pixel clusters
            setSplashPixel(rgba, width, 0 * tileW + 2, 6, 248, 252, 255, 255);
            setSplashPixel(rgba, width, 0 * tileW + 4, 5, 218, 232, 238, 240);
            setSplashPixel(rgba, width, 0 * tileW + 3, 7, 188, 208, 218, 225);

            setSplashPixel(rgba, width, 1 * tileW + 2, 6, 248, 252, 255, 255);
            setSplashPixel(rgba, width, 1 * tileW + 4, 7, 204, 222, 230, 235);

            setSplashPixel(rgba, width, 2 * tileW + 4, 6, 242, 248, 252, 250);
            setSplashPixel(rgba, width, 2 * tileW + 2, 7, 198, 218, 226, 230);

            setSplashPixel(rgba, width, 3 * tileW + 2, 6, 248, 252, 255, 255);
            setSplashPixel(rgba, width, 3 * tileW + 2, 7, 192, 212, 222, 230);

            // Row 1 (y = 8..15): WaterBubbleParticle compact hollow pixel bubbles + foam specks
            int by = 8;
            // Bubble variant 0 & 2: 4x4 rounded-corner hollow square bubble + specular dot
            writeHollowBubble4x4(rgba, width, 0 * tileW, by);
            writeHollowBubble4x4(rgba, width, 2 * tileW, by);
            setSplashPixel(rgba, width, 2 * tileW + 6, by + 6, 220, 236, 244, 210);

            // Bubble variant 1 & 3: 3x3 hollow micro-bubble + floating pixel bead
            writeMicroBubblePair(rgba, width, 1 * tileW, by, 1);
            writeMicroBubblePair(rgba, width, 3 * tileW, by, 0);
        }
        return rgba;
    }

    private static void writeHollowBubble4x4(byte[] rgba, int atlasW, int ox, int oy) {
        setSplashPixel(rgba, atlasW, ox + 3, oy + 2, 224, 240, 248, 230);
        setSplashPixel(rgba, atlasW, ox + 4, oy + 2, 210, 230, 240, 220);
        setSplashPixel(rgba, atlasW, ox + 2, oy + 3, 228, 242, 248, 235);
        setSplashPixel(rgba, atlasW, ox + 5, oy + 3, 196, 218, 230, 215);
        setSplashPixel(rgba, atlasW, ox + 2, oy + 4, 206, 226, 236, 220);
        setSplashPixel(rgba, atlasW, ox + 5, oy + 4, 186, 210, 222, 205);
        setSplashPixel(rgba, atlasW, ox + 3, oy + 5, 192, 214, 226, 210);
        setSplashPixel(rgba, atlasW, ox + 4, oy + 5, 186, 210, 222, 205);
        setSplashPixel(rgba, atlasW, ox + 3, oy + 3, 250, 254, 255, 255);
    }

    private static void writeMicroBubblePair(byte[] rgba, int atlasW, int ox, int oy, int shiftX) {
        setSplashPixel(rgba, atlasW, ox + 3 + shiftX, oy + 2, 232, 244, 250, 235);
        setSplashPixel(rgba, atlasW, ox + 2 + shiftX, oy + 3, 250, 254, 255, 255);
        setSplashPixel(rgba, atlasW, ox + 4 + shiftX, oy + 3, 196, 218, 230, 215);
        setSplashPixel(rgba, atlasW, ox + 3 + shiftX, oy + 4, 188, 212, 224, 210);
        setSplashPixel(rgba, atlasW, ox + 2 + (1 - shiftX) * 3, oy + 6, 224, 240, 248, 220);
    }

    /**
     * Populates a {@link ByteBuffer} with crisp OG-style square splash and bubble atlas texels.
     */
    public static void writeSplashAtlasPixels(ByteBuffer dst, int width, int height) {
        dst.put(generateSplashAtlasRgba(width, height));
    }

    /**
     * Computes a muted, water-harmonized RGB tint for splash particles so rain and entity-bobbing
     * splashes match the Water Shader surface palette instead of vanilla cobalt blue.
     */
    public static void computeSplashTintRgb(
            float[] waterColor,
            float worldLight,
            float thunderGradient,
            boolean swimSplash,
            float[] outRgb) {
        computeWaterParticleTintRgb(waterColor, worldLight, thunderGradient, swimSplash ? 1 : 0, outRgb);
    }

    /**
     * Computes a muted, airy seafoam-aqua tint for {@link WaterBubbleParticle} sprites, sharing the
     * water-harmonized family of splashes while staying slightly lighter so bubbles stay distinct.
     */
    public static void computeBubbleTintRgb(
            float[] waterColor,
            float worldLight,
            float thunderGradient,
            float[] outRgb) {
        computeWaterParticleTintRgb(waterColor, worldLight, thunderGradient, 2, outRgb);
    }

    private static void computeWaterParticleTintRgb(
            float[] waterColor,
            float worldLight,
            float thunderGradient,
            int kind,
            float[] outRgb) {
        float wcR = (waterColor != null && waterColor.length >= 3) ? waterColor[0] : 0.0F;
        float wcG = (waterColor != null && waterColor.length >= 3) ? waterColor[1] : 0.5F;
        float wcB = (waterColor != null && waterColor.length >= 3) ? waterColor[2] : 0.35F;
        computeWaterParticleTintRgb(wcR, wcG, wcB, worldLight, thunderGradient, kind, outRgb);
    }

    private static void computeWaterParticleTintRgb(
            float rawWcR,
            float rawWcG,
            float rawWcB,
            float worldLight,
            float thunderGradient,
            int kind,
            float[] outRgb) {
        if (outRgb == null || outRgb.length < 3) {
            throw new IllegalArgumentException("outRgb must have at least 3 floats");
        }
        float wcR = clamp01(rawWcR);
        float wcG = clamp01(rawWcG);
        float wcB = clamp01(rawWcB);

        float waterBiasR = 0.38F + 0.52F * wcR;
        float waterBiasG = 0.42F + 0.52F * wcG;
        float waterBiasB = 0.44F + 0.52F * wcB;

        float stormDim = 1.0F - 0.14F * clamp01(thunderGradient);
        float mistR;
        float mistG;
        float mistB;
        float waterBlend;
        float lit;
        if (kind == 2) {
            // Bubbles: slightly lighter airy sky-aqua foam
            mistR = 0.78F * stormDim;
            mistG = 0.88F * stormDim;
            mistB = 0.91F;
            waterBlend = 0.30F;
            lit = 0.30F + 0.70F * clamp01(worldLight);
        } else if (kind == 1) {
            // Entity swim splashes
            mistR = 0.78F * stormDim;
            mistG = 0.84F * stormDim;
            mistB = 0.87F;
            waterBlend = 0.34F;
            lit = 0.24F + 0.76F * clamp01(worldLight);
        } else {
            // Pouring rain splashes
            mistR = 0.72F * stormDim;
            mistG = 0.79F * stormDim;
            mistB = 0.83F;
            waterBlend = 0.44F;
            lit = 0.24F + 0.76F * clamp01(worldLight);
        }

        outRgb[0] = clamp01((mistR + (waterBiasR * stormDim - mistR) * waterBlend) * lit);
        outRgb[1] = clamp01((mistG + (waterBiasG * stormDim - mistG) * waterBlend) * lit);
        outRgb[2] = clamp01((mistB + (waterBiasB - mistB) * waterBlend) * lit);
    }

    /**
     * Returns {@code true} if {@code ParticleManager} currently holds any live {@link RainSplashParticle}
     * or {@link WaterSplashParticle} instances to render in the isolated weather pass.
     */
    public static boolean hasSplashParticles(Minecraft client) {
        return hasParticleOfType(client, false);
    }

    /**
     * Returns {@code true} if {@code ParticleManager} currently holds any live {@link WaterBubbleParticle}
     * instances to render before Layer 1 water.
     */
    public static boolean hasBubbleParticles(Minecraft client) {
        return hasParticleOfType(client, true);
    }

    private static boolean hasParticleOfType(Minecraft client, boolean bubbles) {
        List<?> bucket0 = getParticleBucket0(client);
        if (bucket0 == null || bucket0.isEmpty()) {
            return false;
        }
        for (int i = 0, n = bucket0.size(); i < n; i++) {
            Object obj = bucket0.get(i);
            boolean match = bubbles ? (obj instanceof WaterBubbleParticle) : (obj instanceof RainSplashParticle);
            if (match && !((Particle) obj).dead) {
                return true;
            }
        }
        return false;
    }

    /**
     * Smoothly fades outer procedural rain splashes from {@code innerFadeStart..innerFadeEnd} (seamless
     * handoff with close vanilla splashes) and tapers out softly across {@code farFadeStart..maxDist}.
     */
    public static float computeOuterSplashDistanceFade(
            double horizDist,
            double innerFadeStart,
            double innerFadeEnd,
            double farFadeStart,
            double maxDist) {
        if (!Double.isFinite(horizDist) || horizDist > maxDist) {
            return 0.0F;
        }
        double nearFade = smoothstep(innerFadeStart, innerFadeEnd, horizDist);
        double farFade = 1.0 - smoothstep(farFadeStart, maxDist, horizDist);
        return clamp01((float) (nearFade * farFade));
    }

    /**
     * Smoothly fades splash alpha across near eye clearance and far horizon falloff.
     */
    public static float computeSplashDistanceFade(double horizDist, double fadeStart, double maxDist) {
        return computeOuterSplashDistanceFade(horizDist, 0.20, 0.55, fadeStart, maxDist);
    }

    /**
     * Computes perspective-preserving splash billboard scale so pixel squares remain legible out in the
     * 15–36 block mid/far field rather than vanishing into sub-pixel dropout.
     */
    public static double computeSplashPerspectiveScale(double horizDist) {
        if (!Double.isFinite(horizDist) || horizDist <= 6.0) {
            return 1.0;
        }
        return 1.0 + 0.055 * (horizDist - 6.0);
    }

    /**
     * Returns {@code true} when a weather column at horizontal offset {@code (dx, dz)} lies outside
     * the immediate surround radius ({@code > 4} blocks) and strictly behind the camera half-space.
     */
    public static boolean isBehindCameraHalfSpace(
            double dx,
            double dz,
            double fwdX,
            double fwdZ,
            double backMargin) {
        return (dx * dx + dz * dz) > 16.0 && (dx * fwdX + dz * fwdZ) < -backMargin;
    }

    /**
     * Renders modern rain streaks and splashes sharing a single biome area lookup and interpolated
     * camera orientation/culling pass.
     */
    public static void renderModernRainAndSplashes(
            Minecraft client,
            float tickDelta,
            float rainGradient,
            float thunderGradient,
            double animSeconds,
            boolean hasCloseSplashes,
            float waterColorR,
            float waterColorG,
            float waterColorB,
            int rainStreakTexId,
            int rainSplashTexId) {
        if (client == null || client.world == null || client.camera == null) {
            return;
        }
        World world = client.world;
        boolean allowRain = rainGradient > 0.005F
                && (world.dimension == null || (!world.dimension.isNether && !world.dimension.hasCeiling));
        if (!allowRain && !hasCloseSplashes) {
            return;
        }

        LivingEntity cam = client.camera;
        double camX = cam.lastTickX + (cam.x - cam.lastTickX) * tickDelta;
        double camY = cam.lastTickY + (cam.y - cam.lastTickY) * tickDelta;
        double camZ = cam.lastTickZ + (cam.z - cam.lastTickZ) * tickDelta;
        float yaw = cam.prevYaw + (cam.yaw - cam.prevYaw) * tickDelta;
        float pitch = cam.prevPitch + (cam.pitch - cam.prevPitch) * tickDelta;
        float yawRad = yaw * (float) Math.PI / 180.0F;
        float pitchRad = pitch * (float) Math.PI / 180.0F;
        double fwdX = -Math.sin(yawRad);
        double fwdZ = Math.cos(yawRad);
        double backMargin = (client.options != null && client.options.thirdPerson) ? 6.5 : 2.5;

        boolean fancy = client.options != null && client.options.fancyGraphics;
        int outerRInt = (int) Math.ceil(fancy ? OUTER_SPLASH_MAX_FANCY : OUTER_SPLASH_MAX_FAST);
        int camBlockX = MathHelper.floor(camX);
        int camBlockY = MathHelper.floor(camY);
        int camBlockZ = MathHelper.floor(camZ);
        int biomeOriginX = camBlockX - outerRInt;
        int biomeOriginZ = camBlockZ - outerRInt;
        int biomeDiam = outerRInt * 2 + 1;
        Biome[] sharedBiomes = null;
        if (allowRain) {
            sharedBiomes = world.method_1781().getBiomesInArea(
                    biomeOriginX,
                    biomeOriginZ,
                    biomeDiam,
                    biomeDiam);
            if (sharedBiomes != null && sharedBiomes.length < biomeDiam * biomeDiam) {
                sharedBiomes = null;
            }
        }

        if (allowRain && rainStreakTexId != 0 && sharedBiomes != null) {
            renderModernRainInternal(
                    world, camX, camY, camZ, camBlockX, camBlockY, camBlockZ,
                    fwdX, fwdZ, backMargin, fancy, rainGradient, thunderGradient,
                    animSeconds, rainStreakTexId, sharedBiomes, biomeOriginX, biomeOriginZ, biomeDiam);
        }

        if ((hasCloseSplashes || (allowRain && sharedBiomes != null)) && rainSplashTexId != 0) {
            renderModernSplashesInternal(
                    client, world, tickDelta, camX, camY, camZ, camBlockX, camBlockY, camBlockZ,
                    yawRad, pitchRad, fwdX, fwdZ, backMargin, fancy, allowRain ? rainGradient : 0.0F,
                    thunderGradient, animSeconds, hasCloseSplashes,
                    waterColorR, waterColorG, waterColorB, rainSplashTexId,
                    sharedBiomes, biomeOriginX, biomeOriginZ, biomeDiam);
        }
    }

    private static void renderModernRainInternal(
            World world,
            double camX,
            double camY,
            double camZ,
            int camBlockX,
            int camBlockY,
            int camBlockZ,
            double fwdX,
            double fwdZ,
            double backMargin,
            boolean fancy,
            float rainGradient,
            float thunderGradient,
            double animSeconds,
            int rainStreakTexId,
            Biome[] biomes,
            int biomeOriginX,
            int biomeOriginZ,
            int biomeDiam) {
        int radius = fancy ? 24 : 11;
        double windX = 0.16 + 0.12 * clamp01(thunderGradient);
        double windZ = 0.08 + 0.06 * clamp01(thunderGradient);
        float stormTint = 1.0F - 0.18F * clamp01(thunderGradient);

        beginWeatherQuadBatch(rainStreakTexId, camX, camY, camZ);
        Tessellator tess = Tessellator.INSTANCE;
        boolean started = false;
        try {
            tess.startQuads();
            started = true;
            int quadCount = 0;

            for (int bx = camBlockX - radius; bx <= camBlockX + radius; bx++) {
                int dxCol = bx - camBlockX;
                int rowOffset = (bx - biomeOriginX) * biomeDiam;
                for (int bz = camBlockZ - radius; bz <= camBlockZ + radius; bz++) {
                    Biome biome = biomes[rowOffset + (bz - biomeOriginZ)];
                    if (biome == null || !biome.canRain()) {
                        continue;
                    }
                    int dzCol = bz - camBlockZ;
                    int distSq = dxCol * dxCol + dzCol * dzCol;
                    if (distSq > radius * radius) {
                        continue;
                    }
                    if (isBehindCameraHalfSpace(dxCol, dzCol, fwdX, fwdZ, backMargin)) {
                        continue;
                    }

                    int topSolid = Math.max(0, world.getTopY(bx, bz));
                    int minYInt = Math.max(topSolid, camBlockY - radius);
                    int maxYInt = camBlockY + radius;
                    if (minYInt >= maxYInt) {
                        continue;
                    }

                    int lightY = Math.max(0, Math.min(127, Math.max(topSolid, camBlockY)));
                    float worldLight = world.method_1782(bx, lightY, bz);
                    float lit = computeWeatherLight(worldLight);
                    float colR = computeRainRed(lit, stormTint);
                    float colG = computeRainGreen(lit, stormTint);
                    float colB = computeRainBlue(lit);

                    int tracks = (distSq <= 49) ? 3 : ((distSq <= 121) ? 2 : 1);
                    double maxR = radius;
                    double minVisY = topSolid + 0.05;
                    double maxVisY = maxYInt;

                    for (int t = 0; t < tracks; t++) {
                        int h0 = hashInt(bx, bz, t * 3 + 1);
                        int h1 = hashInt(bx, bz, t * 3 + 2);
                        int h2 = hashInt(bx, bz, t * 3 + 3);

                        double subX = 0.10 + 0.80 * hashToUnit(h0);
                        double subZ = 0.10 + 0.80 * hashToUnit(h1);
                        double baseX = bx + subX;
                        double baseZ = bz + subZ;

                        double dxCam = baseX - camX;
                        double dzCam = baseZ - camZ;
                        double horizDist = Math.sqrt(dxCam * dxCam + dzCam * dzCam);
                        if (horizDist < 0.40 || horizDist > maxR) {
                            continue;
                        }

                        float nearFade = (float) smoothstep(0.45, 1.65, horizDist);
                        float farFade = (float) (1.0 - smoothstep(maxR * 0.45, maxR, horizDist));
                        float distFade = nearFade * farFade;
                        if (distFade <= 0.01F) {
                            continue;
                        }

                        double periodY = 4.2 + 2.4 * hashToUnit(h2);
                        double speed = 19.0 + 7.0 * hashToUnit(h0 ^ h2);
                        double dropLen = 1.15 + 0.75 * hashToUnit(h1 ^ h2);
                        double halfW = 0.052 + 0.0022 * horizDist;
                        float dropIntensity = 0.70F + 0.30F * (float) hashToUnit(h0 ^ h1);

                        double tx = (-dzCam / horizDist) * halfW;
                        double tz = (dxCam / horizDist) * halfW;

                        double phaseOffset = hashToUnit(h2 ^ (h0 >>> 3)) * periodY;
                        double scroll = animSeconds * speed + phaseOffset;

                        int kMin = (int) Math.floor((minVisY + scroll - dropLen) / periodY);
                        int kMax = (int) Math.floor((maxVisY + scroll) / periodY);

                        for (int k = kMin; k <= kMax; k++) {
                            double rawBot = k * periodY - scroll;
                            double rawTop = rawBot + dropLen;
                            double botY = Math.max(minVisY, rawBot);
                            double topY = Math.min(maxVisY, rawTop);
                            if (topY - botY < 0.18) {
                                continue;
                            }

                            // Soften drops touching water/roof surfaces
                            float roofFade = (float) smoothstep(topSolid, topSolid + 0.70, botY);
                            float alpha = clamp01(
                                    rainGradient * distFade * roofFade * dropIntensity
                                            * (0.28F + 0.12F * clamp01(thunderGradient)));
                            if (alpha <= 0.01F) {
                                continue;
                            }

                            double hDiff = topY - botY;
                            double swayShift = (rawBot - minVisY) * 0.04;
                            double botX = baseX + windX * swayShift;
                            double botZ = baseZ + windZ * swayShift;
                            double topX = botX + windX * hDiff;
                            double topZ = botZ + windZ * hDiff;

                            double vTop = 1.0 - (topY - rawBot) / dropLen;
                            double vBot = 1.0 - (botY - rawBot) / dropLen;

                            tess.color(colR, colG, colB, alpha);
                            tess.vertex(botX - tx, botY, botZ - tz, 0.0, vBot);
                            tess.vertex(botX + tx, botY, botZ + tz, 1.0, vBot);
                            tess.vertex(topX + tx, topY, topZ + tz, 1.0, vTop);
                            tess.vertex(topX - tx, topY, topZ - tz, 0.0, vTop);

                            quadCount++;
                            if (quadCount >= 2048) {
                                tess.draw();
                                tess.startQuads();
                                quadCount = 0;
                            }
                        }
                    }
                }
            }

            tess.draw();
            started = false;
        } finally {
            endWeatherQuadBatch(tess, started);
        }
    }

    private static void renderModernSplashesInternal(
            Minecraft client,
            World world,
            float tickDelta,
            double camX,
            double camY,
            double camZ,
            int camBlockX,
            int camBlockY,
            int camBlockZ,
            float yawRad,
            float pitchRad,
            double fwdX,
            double fwdZ,
            double backMargin,
            boolean fancy,
            float rainGradient,
            float thunderGradient,
            double animSeconds,
            boolean hasCloseParticles,
            float waterColorR,
            float waterColorG,
            float waterColorB,
            int rainSplashTexId,
            Biome[] biomes,
            int biomeOriginX,
            int biomeOriginZ,
            int biomeDiam) {
        List<?> bucket0 = hasCloseParticles ? getParticleBucket0(client) : null;
        boolean renderClose = hasCloseParticles && bucket0 != null && !bucket0.isEmpty();
        boolean renderProceduralOuter = rainGradient > 0.005F && biomes != null;
        if (!renderClose && !renderProceduralOuter) {
            return;
        }

        float rotX = MathHelper.cos(yawRad);
        float rotZ = MathHelper.sin(yawRad);
        float rotYZ = -rotZ * MathHelper.sin(pitchRad);
        float rotXY = rotX * MathHelper.sin(pitchRad);
        float rotXZ = MathHelper.cos(pitchRad);

        float thunder = clamp01(thunderGradient);

        beginWeatherQuadBatch(rainSplashTexId, camX, camY, camZ);
        Tessellator tess = Tessellator.INSTANCE;
        boolean started = false;
        try {
            tess.startQuads();
            started = true;
            int quadCount = 0;

            if (renderClose) {
                for (int i = 0, n = bucket0.size(); i < n; i++) {
                    Object obj = bucket0.get(i);
                    if (!(obj instanceof RainSplashParticle p) || p.dead) {
                        continue;
                    }

                    boolean swimSplash = obj instanceof WaterSplashParticle;
                    double px = p.prevX + (p.x - p.prevX) * tickDelta;
                    double py = p.prevY + (p.y - p.prevY) * tickDelta + (swimSplash ? 0.05 : 0.015);
                    double pz = p.prevZ + (p.z - p.prevZ) * tickDelta;

                    double dxCam = px - camX;
                    double dzCam = pz - camZ;
                    double horizDist = Math.sqrt(dxCam * dxCam + dzCam * dzCam);
                    double fadeStart = swimSplash ? 16.0 : 7.5;
                    double maxDist = swimSplash ? 26.0 : 11.5;
                    float distFade = computeSplashDistanceFade(horizDist, fadeStart, maxDist);
                    if (distFade <= 0.01F) {
                        continue;
                    }

                    ParticleAccessor pa = (ParticleAccessor) p;
                    int maxAge = Math.max(1, pa.watershader$getMaxParticleAge());
                    float ageRatio = clamp01((pa.watershader$getParticleAge() + tickDelta) / maxAge);
                    float lifeFade = (float) (1.0 - smoothstep(0.55, 1.0, ageRatio));
                    float baseAlpha = swimSplash ? 0.86F : (0.76F + 0.10F * thunder);
                    float alpha = clamp01(distFade * lifeFade * baseAlpha);
                    if (alpha <= 0.01F) {
                        continue;
                    }

                    float scale = Math.max(0.2F, pa.watershader$getScale());
                    double distScale = swimSplash ? 1.0 : computeSplashPerspectiveScale(horizDist);
                    double r = (swimSplash ? 0.11 : 0.10) * scale * distScale;
                    if (!swimSplash && distScale > 1.0) {
                        py += r * 0.45 * (distScale - 1.0);
                    }

                    computeWaterParticleTintRgb(
                            waterColorR, waterColorG, waterColorB,
                            p.getBrightnessAtEyes(tickDelta), thunder, swimSplash ? 1 : 0, TMP_SPLASH_RGB);
                    int variant = Math.floorMod(pa.watershader$getTextureId(), SPLASH_TILE_COUNT);
                    quadCount = emitAtlasBillboardQuad(
                            tess, px, py, pz, r, rotX, rotXZ, rotZ, rotYZ, rotXY,
                            variant, 0.001, 0.499, TMP_SPLASH_RGB, alpha, quadCount);
                }
            }

            if (renderProceduralOuter) {
                double maxOuterDist = fancy ? OUTER_SPLASH_MAX_FANCY : OUTER_SPLASH_MAX_FAST;
                int outerRInt = (int) Math.ceil(maxOuterDist);
                int vertSpan = fancy ? 20 : 14;
                int maxOuterSq = outerRInt * outerRInt;
                for (int bx = camBlockX - outerRInt; bx <= camBlockX + outerRInt; bx++) {
                    int dxCol = bx - camBlockX;
                    int rowOffset = (bx - biomeOriginX) * biomeDiam;
                    for (int bz = camBlockZ - outerRInt; bz <= camBlockZ + outerRInt; bz++) {
                        Biome biome = biomes[rowOffset + (bz - biomeOriginZ)];
                        if (biome == null || !biome.canRain()) {
                            continue;
                        }
                        int dzCol = bz - camBlockZ;
                        int distSq = dxCol * dxCol + dzCol * dzCol;
                        if (distSq < 30 || distSq > maxOuterSq) {
                            continue;
                        }
                        if (isBehindCameraHalfSpace(dxCol, dzCol, fwdX, fwdZ, backMargin)) {
                            continue;
                        }

                        int topY = world.getTopY(bx, bz);
                        if (topY <= 0 || topY > camBlockY + vertSpan || topY < camBlockY - vertSpan) {
                            continue;
                        }
                        if (world.getMaterial(bx, topY - 1, bz) == Material.LAVA) {
                            continue;
                        }

                        float worldLight = world.method_1782(bx, Math.min(127, topY), bz);
                        computeWaterParticleTintRgb(
                                waterColorR, waterColorG, waterColorB,
                                worldLight, thunder, 0, TMP_SPLASH_RGB);
                        int tracks = (distSq <= 225) ? 2 : 1;

                        for (int t = 0; t < tracks; t++) {
                            int h0 = hashInt(bx, bz, t * 7 + 53);
                            int h1 = hashInt(bx, bz, t * 7 + 59);
                            int h2 = hashInt(bx, bz, t * 7 + 61);

                            double speed = 1.65 + 1.35 * hashToUnit(h0);
                            double phase = animSeconds * speed + hashToUnit(h1);
                            double frac = phase - Math.floor(phase);
                            double activeWindow = (tracks == 2) ? 0.64 : 0.72;
                            if (frac >= activeWindow) {
                                continue;
                            }
                            double age = frac / activeWindow;

                            double px = bx + 0.12 + 0.76 * hashToUnit(h2);
                            double pz = bz + 0.12 + 0.76 * hashToUnit(h0 ^ h1);
                            double dxCam = px - camX;
                            double dzCam = pz - camZ;
                            double horizDist = Math.sqrt(dxCam * dxCam + dzCam * dzCam);
                            float distFade = computeOuterSplashDistanceFade(
                                    horizDist,
                                    OUTER_SPLASH_INNER_FADE_START,
                                    OUTER_SPLASH_INNER_FADE_END,
                                    OUTER_SPLASH_FAR_FADE_START,
                                    maxOuterDist);
                            if (distFade <= 0.01F) {
                                continue;
                            }

                            float lifeFade = (float) (1.0 - smoothstep(0.55, 1.0, age));
                            float alpha = clamp01(rainGradient * distFade * lifeFade * (0.78F + 0.12F * thunder));
                            if (alpha <= 0.01F) {
                                continue;
                            }

                            double scale = 1.0 + 0.9 * hashToUnit(h1 ^ h2);
                            double r = 0.10 * scale * computeSplashPerspectiveScale(horizDist);
                            // Lift quad center proportionally to radius so lower-row pixel droplets stay above surface depth
                            double py = topY + 0.05 + r * 0.72 + 0.22 * (4.0 * age * (1.0 - age));
                            int variant = h2 & 3;
                            quadCount = emitAtlasBillboardQuad(
                                    tess, px, py, pz, r, rotX, rotXZ, rotZ, rotYZ, rotXY,
                                    variant, 0.001, 0.499, TMP_SPLASH_RGB, alpha, quadCount);
                        }
                    }
                }
            }

            tess.draw();
            started = false;
        } finally {
            endWeatherQuadBatch(tess, started);
        }
    }

    /**
     * Renders deferred {@link WaterBubbleParticle} instances at the end of {@code ParticleManager.render}
     * (before Layer 1 water so submerged bubbles remain visible from above water and refract naturally)
     * using the crisp seafoam bubble row of the water-harmonized particle atlas.
     */
    public static void renderModernWaterBubbles(
            Minecraft client,
            Entity camera,
            float tickDelta,
            float thunderGradient,
            float waterColorR,
            float waterColorG,
            float waterColorB,
            int splashAtlasTexId) {
        if (client == null || camera == null || splashAtlasTexId == 0) {
            return;
        }
        List<?> bucket0 = getParticleBucket0(client);
        if (bucket0 == null || bucket0.isEmpty()) {
            return;
        }

        double camX = (camera instanceof LivingEntity le)
                ? le.lastTickX + (le.x - le.lastTickX) * tickDelta
                : camera.prevX + (camera.x - camera.prevX) * tickDelta;
        double camY = (camera instanceof LivingEntity le)
                ? le.lastTickY + (le.y - le.lastTickY) * tickDelta
                : camera.prevY + (camera.y - camera.prevY) * tickDelta;
        double camZ = (camera instanceof LivingEntity le)
                ? le.lastTickZ + (le.z - le.lastTickZ) * tickDelta
                : camera.prevZ + (camera.z - camera.prevZ) * tickDelta;

        float yaw = camera.prevYaw + (camera.yaw - camera.prevYaw) * tickDelta;
        float pitch = camera.prevPitch + (camera.pitch - camera.prevPitch) * tickDelta;
        float yawRad = yaw * (float) Math.PI / 180.0F;
        float pitchRad = pitch * (float) Math.PI / 180.0F;
        float rotX = MathHelper.cos(yawRad);
        float rotZ = MathHelper.sin(yawRad);
        float rotYZ = -rotZ * MathHelper.sin(pitchRad);
        float rotXY = rotX * MathHelper.sin(pitchRad);
        float rotXZ = MathHelper.cos(pitchRad);

        float thunder = clamp01(thunderGradient);

        beginWeatherQuadBatch(splashAtlasTexId, camX, camY, camZ);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        Tessellator tess = Tessellator.INSTANCE;
        boolean started = false;
        try {
            tess.startQuads();
            started = true;
            int quadCount = 0;

            for (int i = 0, n = bucket0.size(); i < n; i++) {
                Object obj = bucket0.get(i);
                if (!(obj instanceof WaterBubbleParticle p) || p.dead) {
                    continue;
                }

                double px = p.prevX + (p.x - p.prevX) * tickDelta;
                double py = p.prevY + (p.y - p.prevY) * tickDelta;
                double pz = p.prevZ + (p.z - p.prevZ) * tickDelta;

                double dxCam = px - camX;
                double dzCam = pz - camZ;
                double horizDist = Math.sqrt(dxCam * dxCam + dzCam * dzCam);
                if (horizDist > 24.0) {
                    continue;
                }

                float farFade = (float) (1.0 - smoothstep(14.0, 24.0, horizDist));
                if (farFade <= 0.01F) {
                    continue;
                }

                ParticleAccessor pa = (ParticleAccessor) p;
                int remaining = pa.watershader$getMaxParticleAge();
                float lifeFade = (remaining <= 6) ? clamp01(remaining / 6.0F) : 1.0F;
                float alpha = clamp01(farFade * lifeFade * 0.78F);
                if (alpha <= 0.01F) {
                    continue;
                }

                float scale = Math.max(0.2F, pa.watershader$getScale());
                double r = 0.095 * scale;

                computeWaterParticleTintRgb(
                        waterColorR, waterColorG, waterColorB,
                        p.getBrightnessAtEyes(tickDelta), thunder, 2, TMP_SPLASH_RGB);
                int variant = (System.identityHashCode(p) >>> 4) & 3;
                quadCount = emitAtlasBillboardQuad(
                        tess, px, py, pz, r, rotX, rotXZ, rotZ, rotYZ, rotXY,
                        variant, 0.501, 0.999, TMP_SPLASH_RGB, alpha, quadCount);
            }

            tess.draw();
            started = false;
        } finally {
            endWeatherQuadBatch(tess, started);
        }
    }

    private static int emitAtlasBillboardQuad(
            Tessellator tess,
            double px,
            double py,
            double pz,
            double r,
            float rotX,
            float rotXZ,
            float rotZ,
            float rotYZ,
            float rotXY,
            int variant,
            double v0,
            double v1,
            float[] rgb,
            float alpha,
            int quadCount) {
        double u0 = variant * (1.0 / SPLASH_TILE_COUNT) + 0.001;
        double u1 = (variant + 1) * (1.0 / SPLASH_TILE_COUNT) - 0.001;
        double rx = rotX * r;
        double rz = rotZ * r;
        double udx = rotYZ * r;
        double udy = rotXZ * r;
        double udz = rotXY * r;

        tess.color(rgb[0], rgb[1], rgb[2], alpha);
        tess.vertex(px - rx - udx, py - udy, pz - rz - udz, u0, v1);
        tess.vertex(px - rx + udx, py + udy, pz - rz + udz, u0, v0);
        tess.vertex(px + rx + udx, py + udy, pz + rz + udz, u1, v0);
        tess.vertex(px + rx - udx, py - udy, pz + rz - udz, u1, v1);

        quadCount++;
        if (quadCount >= 2048) {
            tess.draw();
            tess.startQuads();
            return 0;
        }
        return quadCount;
    }

    private static void setSplashPixel(byte[] rgba, int atlasW, int x, int y, int r, int g, int b, int a) {
        int idx = (y * atlasW + x) * 4;
        rgba[idx] = (byte) r;
        rgba[idx + 1] = (byte) g;
        rgba[idx + 2] = (byte) b;
        rgba[idx + 3] = (byte) a;
    }

    private static List<?> getParticleBucket0(Minecraft client) {
        if (client == null || client.particleManager == null) {
            return null;
        }
        List<?>[] buckets = ((ParticleManagerAccessor) client.particleManager).watershader$getParticles();
        return (buckets != null && buckets.length > 0) ? buckets[0] : null;
    }

    private static void beginWeatherQuadBatch(int textureId, double camX, double camY, double camZ) {
        GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT
                        | GL11.GL_COLOR_BUFFER_BIT
                        | GL11.GL_DEPTH_BUFFER_BIT
                        | GL11.GL_TEXTURE_BIT
                        | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        WaterShaderPipeline.applyWeatherBlendFunc();
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.005F);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glNormal3f(0.0F, 1.0F, 0.0F);
        Tessellator.INSTANCE.setOffset(-camX, -camY, -camZ);
    }

    private static void endWeatherQuadBatch(Tessellator tess, boolean started) {
        if (started) {
            tess.draw();
        }
        tess.setOffset(0.0, 0.0, 0.0);
        GL11.glPopAttrib();
    }

    private static float computeWeatherLight(float worldLight) {
        return 0.22F + 0.78F * clamp01(worldLight);
    }

    private static float computeRainRed(float lit, float stormTint) {
        return clamp01(0.84F * lit * stormTint);
    }

    private static float computeRainGreen(float lit, float stormTint) {
        return clamp01(0.90F * lit * stormTint);
    }

    private static float computeRainBlue(float lit) {
        return clamp01(0.98F * lit);
    }

    private static int hashInt(int x, int z, int salt) {
        int h = x * 0x27d4eb2d ^ z * 0x165667b1 ^ salt * 0x9e3779b9;
        h = (h ^ (h >>> 15)) * 0x85ebca6b;
        h = (h ^ (h >>> 13)) * 0xc2b2ae35;
        return h ^ (h >>> 16);
    }

    private static double hashToUnit(int hash) {
        return ((hash >>> 8) & 0xFFFFFF) / (double) 0x1000000;
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = Math.max(0.0, Math.min(1.0, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }

    private static float clamp01(float v) {
        if (!Float.isFinite(v)) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, v));
    }
}
