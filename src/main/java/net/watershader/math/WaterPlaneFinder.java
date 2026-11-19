package net.watershader.math;

import java.util.Arrays;

/**
 * Tracks the active planar reflection water height and derives reflection transform / clip plane
 * constants matching Beta 1.7.3 still-water surface geometry ({@code y = 63 -> 63 + 8/9}).
 */
public final class WaterPlaneFinder {
    public static final int DEFAULT_SEA_BLOCK_Y = 63;
    public static final double WATER_SURFACE_FRACTION = 1.0 - 1.0 / 9.0; // exact still water top (0.8888889)
    public static final double BASE_WATER_DOUBLE_Y = 2.0 * (DEFAULT_SEA_BLOCK_Y + WATER_SURFACE_FRACTION);
    public static final double CLIP_PLANE_OFFSET = DEFAULT_SEA_BLOCK_Y + 0.875; // 0.014 blocks below surface
    public static final int DEFAULT_SCAN_RADIUS = 16;
    public static final int DEFAULT_SCAN_ABOVE = 2;
    public static final int DEFAULT_SCAN_BELOW = 20;
    public static final int SAMPLE_INTERVAL_FRAMES = 4;
    public static final double SMOOTHING_SPEED = 8.0;
    private static final int WORLD_HEIGHT = 128;

    /**
     * Minimal world query abstraction so plane scanning is testable without a Minecraft instance.
     */
    public interface BlockProbe {
        boolean isStillWater(int x, int y, int z);

        boolean isOpaqueSolid(int x, int y, int z);
    }

    private final double[] heightScoresScratch = new double[WORLD_HEIGHT];
    private double lastWaterHeight = 0.0; // 2.0 * (63 - activeWaterBlockY)
    private double targetWaterHeight = 0.0;
    private boolean hasInitialSample = false;
    private double lastSampleCamX = Double.NaN;
    private double lastSampleCamZ = Double.NaN;
    private int sampleFrameCounter = 0;

    public double getLastWaterHeight() {
        return this.lastWaterHeight;
    }

    public void setLastWaterHeight(double lastWaterHeight) {
        this.lastWaterHeight = lastWaterHeight;
        this.targetWaterHeight = lastWaterHeight;
        this.hasInitialSample = true;
    }

    public void reset() {
        this.lastWaterHeight = 0.0;
        this.targetWaterHeight = 0.0;
        this.hasInitialSample = false;
        this.lastSampleCamX = Double.NaN;
        this.lastSampleCamZ = Double.NaN;
        this.sampleFrameCounter = 0;
    }

    /**
     * Computes the Y translation applied before {@code glScalef(1, -1, 1)} in the reflection pass:
     * {@code BASE_WATER_DOUBLE_Y - 2.0 * camY - lastWaterHeight} ({@code 2 * (63 + 8/9) = 127.7778}).
     */
    public double computeWaterHeightOffset(double camY) {
        return computeWaterHeightOffset(camY, this.lastWaterHeight);
    }

    public static double computeWaterHeightOffset(double camY, double lastWaterHeight) {
        return BASE_WATER_DOUBLE_Y - 2.0 * camY - lastWaterHeight;
    }

    /**
     * Computes the fourth component {@code pw} of the world-relative horizontal clip plane
     * {@code (0, 1, 0, pw)} corresponding to {@code waterToOrigin}.
     */
    public static float computeClipPlaneH(double waterToOrigin) {
        return (float) ((waterToOrigin - BASE_WATER_DOUBLE_Y) / -2.0 - CLIP_PLANE_OFFSET);
    }

    /**
     * Continuously samples gaze-weighted water heights around the camera and exponentially
     * smooths transitions between different water planes (snapping only on initial sample or
     * teleport).
     */
    public void updateSmoothed(
            BlockProbe probe,
            double camX,
            double camY,
            double camZ,
            float yawDegrees,
            float pitchDegrees,
            boolean submerged,
            double dtSeconds) {
        if (submerged || probe == null) {
            return;
        }
        boolean teleported = !Double.isNaN(this.lastSampleCamX)
                && Math.hypot(camX - this.lastSampleCamX, camZ - this.lastSampleCamZ) > 32.0;
        this.sampleFrameCounter++;
        if (!this.hasInitialSample || teleported || this.sampleFrameCounter >= SAMPLE_INTERVAL_FRAMES) {
            this.sampleFrameCounter = 0;
            this.lastSampleCamX = camX;
            this.lastSampleCamZ = camZ;
            Integer foundY = findDominantWaterBlockY(
                    probe, camX, camY, camZ, yawDegrees, pitchDegrees, this.heightScoresScratch);
            if (foundY != null) {
                this.targetWaterHeight = 2.0 * (DEFAULT_SEA_BLOCK_Y - foundY);
                if (!this.hasInitialSample || teleported) {
                    this.lastWaterHeight = this.targetWaterHeight;
                    this.hasInitialSample = true;
                }
            }
        }
        if (this.hasInitialSample && dtSeconds > 0.0) {
            double dt = Math.min(dtSeconds, 0.25);
            double alpha = 1.0 - Math.exp(-dt * SMOOTHING_SPEED);
            this.lastWaterHeight += (this.targetWaterHeight - this.lastWaterHeight) * alpha;
            if (Math.abs(this.targetWaterHeight - this.lastWaterHeight) < 1e-4) {
                this.lastWaterHeight = this.targetWaterHeight;
            }
        }
    }

    /**
     * Finds the dominant still-water block Y using a direct view-ray march combined with a
     * proximity and view-frustum weighted column scan so small/elevated ponds in front of the
     * camera take priority over pools behind the camera.
     */
    public static Integer findDominantWaterBlockY(
            BlockProbe probe,
            double camX,
            double camY,
            double camZ,
            float yawDegrees,
            float pitchDegrees) {
        return findDominantWaterBlockY(probe, camX, camY, camZ, yawDegrees, pitchDegrees, new double[WORLD_HEIGHT]);
    }

    private static Integer findDominantWaterBlockY(
            BlockProbe probe,
            double camX,
            double camY,
            double camZ,
            float yawDegrees,
            float pitchDegrees,
            double[] scores) {
        Arrays.fill(scores, 0.0);

        double yawRad = Math.toRadians(yawDegrees);
        double pitchRad = Math.toRadians(pitchDegrees);
        double cosPitch = Math.cos(pitchRad);
        double lookX = -Math.sin(yawRad) * cosPitch;
        double lookY = -Math.sin(pitchRad);
        double lookZ = Math.cos(yawRad) * cosPitch;
        double fwdX = -Math.sin(yawRad);
        double fwdZ = Math.cos(yawRad);

        boolean anyHit = false;

        // 1. Direct View-Ray March (t = 0.5 to 20.0 step 0.5)
        for (double t = 0.5; t <= 20.0; t += 0.5) {
            int rx = (int) Math.floor(camX + t * lookX);
            int ry = (int) Math.floor(camY + t * lookY);
            int rz = (int) Math.floor(camZ + t * lookZ);
            if (ry >= 0 && ry < WORLD_HEIGHT && probe.isStillWater(rx, ry, rz)) {
                scores[ry] += 45.0 / (1.0 + 0.05 * t);
                anyHit = true;
                break;
            }
            if (ry > 0 && ry - 1 < WORLD_HEIGHT && probe.isStillWater(rx, ry - 1, rz) && !probe.isOpaqueSolid(rx, ry, rz)) {
                scores[ry - 1] += 35.0 / (1.0 + 0.05 * t);
                anyHit = true;
                break;
            }
            if (ry >= 0 && ry < WORLD_HEIGHT && probe.isOpaqueSolid(rx, ry, rz)) {
                break;
            }
        }

        // 2. Proximity & Frustum-Weighted Column Scan
        int centerX = (int) Math.floor(camX);
        int centerY = (int) Math.floor(camY);
        int centerZ = (int) Math.floor(camZ);
        int maxY = Math.min(WORLD_HEIGHT - 1, centerY + DEFAULT_SCAN_ABOVE);
        int minY = Math.max(0, centerY - DEFAULT_SCAN_BELOW);

        for (int dx = -DEFAULT_SCAN_RADIUS; dx <= DEFAULT_SCAN_RADIUS; dx++) {
            boolean inInnerX = Math.abs(dx) <= 8;
            for (int dz = -DEFAULT_SCAN_RADIUS; dz <= DEFAULT_SCAN_RADIUS; dz++) {
                boolean inner = inInnerX && Math.abs(dz) <= 8;
                if (!inner && ((dx & 1) != 0 || (dz & 1) != 0)) {
                    continue;
                }
                int wx = centerX + dx;
                int wz = centerZ + dz;
                for (int wy = maxY; wy >= minY; wy--) {
                    if (probe.isStillWater(wx, wy, wz)) {
                        double ox = (wx + 0.5) - camX;
                        double oz = (wz + 0.5) - camZ;
                        double horizDist = Math.hypot(ox, oz);
                        double proxWeight = 1.0 / (1.0 + 0.15 * horizDist);
                        double dot = (horizDist > 1e-4) ? ((ox * fwdX + oz * fwdZ) / horizDist) : 1.0;
                        double viewWeight = (dot >= 0.15)
                                ? (1.0 + 3.5 * dot * dot)
                                : ((dot >= -0.15) ? 0.6 : 0.15);
                        double stepCompensation = inner ? 1.0 : 2.0;
                        scores[wy] += proxWeight * viewWeight * stepCompensation;
                        anyHit = true;
                        break;
                    }
                    if (probe.isOpaqueSolid(wx, wy, wz)) {
                        break;
                    }
                }
            }
        }

        if (!anyHit) {
            return null;
        }

        int bestY = Integer.MIN_VALUE;
        double bestScore = 0.0;
        int bestDist = Integer.MAX_VALUE;
        for (int y = 0; y < WORLD_HEIGHT; y++) {
            double score = scores[y];
            if (score <= 0.0) {
                continue;
            }
            int dist = Math.abs(y - centerY);
            if (score > bestScore
                    || (Double.compare(score, bestScore) == 0 && (dist < bestDist || (dist == bestDist && y > bestY)))) {
                bestY = y;
                bestScore = score;
                bestDist = dist;
            }
        }
        return bestY == Integer.MIN_VALUE ? null : bestY;
    }
}
