package net.watershader.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReflectionMathTest {

    @Test
    void inverseOrthSatisfiesRightAndLeftIdentityAcrossRotationsAndTranslations() {
        float[] rx = rotationX((float) Math.toRadians(37.5));
        float[] ry = rotationY((float) Math.toRadians(-123.0));
        float[] rz = rotationZ((float) Math.toRadians(19.25));
        float[] r = new float[16];
        float[] tmp = new float[16];
        ReflectionMath.multiply(tmp, ry, rx);
        ReflectionMath.multiply(r, tmp, rz);

        // Add non-zero translation in column 3
        r[12] = 14.5F;
        r[13] = -68.25F;
        r[14] = 3.75F;

        float[] inv = new float[16];
        ReflectionMath.inverseOrth(inv, r);

        float[] prodRight = new float[16];
        float[] prodLeft = new float[16];
        ReflectionMath.multiply(prodRight, r, inv);
        ReflectionMath.multiply(prodLeft, inv, r);

        assertIdentity(prodRight, 1e-5F);
        assertIdentity(prodLeft, 1e-5F);
    }

    @Test
    void reflectionTransformMapsWaterSurfaceToItselfAndClipsOnlyBelowWater() {
        double camY = 66.62;
        WaterPlaneFinder finder = new WaterPlaneFinder();
        double waterToOrigin = finder.computeWaterHeightOffset(camY);
        float planeH = WaterPlaneFinder.computeClipPlaneH(waterToOrigin);

        double surfaceY = WaterPlaneFinder.DEFAULT_SEA_BLOCK_Y + WaterPlaneFinder.WATER_SURFACE_FRACTION;
        float[] waterPtObj = {12.0F, (float) (surfaceY - camY), -8.0F, 1.0F};
        float[] abovePtObj = {12.0F, (float) (78.0 - camY), -8.0F, 1.0F};
        float[] belowPtObj = {12.0F, (float) (52.0 - camY), -8.0F, 1.0F};

        float[] mirror = buildMirrorMatrix(waterToOrigin);

        // Fixed point on water surface: mirrored Y equals original Y
        float[] mirroredWater = new float[4];
        ReflectionMath.transformVec4(mirroredWater, mirror, waterPtObj);
        assertEquals(waterPtObj[1], mirroredWater[1], 1e-4F);

        // Test several camera pitches: looking down (+30), level (0), looking up (-40), straight up (-89.5)
        float[] pitchesDeg = {30.0F, 0.0F, -40.0F, -89.5F};
        for (float pitch : pitchesDeg) {
            float[] cam = buildCameraMatrix(25.0F, pitch);
            float[] mv = new float[16];
            ReflectionMath.multiply(mv, cam, mirror);

            float[] invMv = new float[16];
            ReflectionMath.inverseOrth(invMv, mv);

            float[] planeEye = new float[4];
            ReflectionMath.transformPlaneTransposeInverse(planeEye, invMv, new float[]{0.0F, 1.0F, 0.0F, planeH});

            float[] proj = buildPerspective((float) Math.toRadians(70.0), 16.0F / 9.0F, 0.05F, 256.0F);
            assertTrue(ReflectionMath.modifyProjectionMatrix(proj, planeEye));

            // Point in front of reflected camera & above water (world Y = 78, right in front along camera look ray?
            // Regardless of horizontal offsets, test Z_ndc relative to -1: points above water in front of camera
            // satisfy ndc_z > -1 while points below water in front of camera satisfy ndc_z < -1!)
            float[] mvp = new float[16];
            ReflectionMath.multiply(mvp, proj, mv);

            // Place sample points directly in front of reflected eye so w_clip > 0
            // Note: cam looks down -Z in eye space; pick (0, waterHeightRel +/- delta, -15) before camera yaw or inverse-rotate
            float[] invCam = new float[16];
            ReflectionMath.inverseOrth(invCam, cam);

            // Reflected object point in eye space: check planeEye . v_eye matches above/below water,
            // including tight clipping of submerged swimming entities (e.g. pig belly at y = 63.60)
            float[] aboveInFrontEye = findPointInFrontOfEyeWithWorldHeight(mv, (float) (80.0 - camY), -20.0F);
            float[] swimBackEye = findPointInFrontOfEyeWithWorldHeight(mv, (float) (64.05 - camY), -20.0F);
            float[] swimBellyEye = findPointInFrontOfEyeWithWorldHeight(mv, (float) (63.60 - camY), -20.0F);
            float[] belowInFrontEye = findPointInFrontOfEyeWithWorldHeight(mv, (float) (50.0 - camY), -20.0F);

            if (aboveInFrontEye != null) {
                float zNdc = projectZ(proj, aboveInFrontEye);
                assertTrue(zNdc > -1.0F, "Above-water point should stay unclipped at pitch " + pitch + " (z_ndc=" + zNdc + ")");
            }
            if (swimBackEye != null) {
                float zNdc = projectZ(proj, swimBackEye);
                assertTrue(zNdc > -1.0F, "Swimming entity above-water back should stay unclipped at pitch " + pitch + " (z_ndc=" + zNdc + ")");
            }
            if (swimBellyEye != null) {
                float zNdc = projectZ(proj, swimBellyEye);
                assertTrue(zNdc < -1.0F, "Submerged entity belly should be clipped at pitch " + pitch + " (z_ndc=" + zNdc + ")");
            }
            if (belowInFrontEye != null) {
                float zNdc = projectZ(proj, belowInFrontEye);
                assertTrue(zNdc < -1.0F, "Below-water point should be clipped at pitch " + pitch + " (z_ndc=" + zNdc + ")");
            }
        }
    }

    @Test
    void orthographicProjectionIsLeftUntouchedByObliqueClip() {
        float[] ortho = {
                2.0F / 1280.0F, 0, 0, 0,
                0, 2.0F / 720.0F, 0, 0,
                0, 0, -2.0F / 200.0F, 0,
                -1, -1, -1, 1
        };
        float[] original = ortho.clone();
        assertFalse(ReflectionMath.modifyProjectionMatrix(ortho, new float[]{0.0F, -1.0F, 0.0F, -2.5F}));
        assertArrayEquals(original, ortho);
    }

    @Test
    void extractNearFarParsesPerspectiveAndRejectsOrthographic() {
        float[] proj = buildPerspective((float) Math.toRadians(70.0), 16.0F / 9.0F, 0.05F, 256.0F);
        float[] nearFar = new float[2];
        assertTrue(ReflectionMath.extractNearFar(proj, nearFar));
        assertEquals(0.05F, nearFar[0], 1e-5F);
        assertEquals(256.0F, nearFar[1], 0.15F);

        float[] ortho = {
                2.0F / 1280.0F, 0, 0, 0,
                0, 2.0F / 720.0F, 0, 0,
                0, 0, -2.0F / 200.0F, 0,
                -1, -1, -1, 1
        };
        assertFalse(ReflectionMath.extractNearFar(ortho, nearFar));
        assertEquals(0.05F, nearFar[0], 1e-6F);
        assertEquals(512.0F, nearFar[1], 1e-6F);
    }

    @Test
    void computeInvViewProjReconstructsWorldRelativePointFromNdcDepth() {
        float[] cam = buildCameraMatrix(42.0F, 24.0F);
        float[] proj = buildPerspective((float) Math.toRadians(70.0), 16.0F / 9.0F, 0.05F, 256.0F);
        float[] vp = new float[16];
        float[] invVp = new float[16];
        float[] tmp = new float[16];
        ReflectionMath.multiply(vp, proj, cam);
        assertTrue(ReflectionMath.computeInvViewProj(invVp, proj, cam, tmp));

        float[] worldRel = {4.5F, -2.25F, -9.0F, 1.0F};
        float[] clip = new float[4];
        ReflectionMath.transformVec4(clip, vp, worldRel);
        float[] ndc = {clip[0] / clip[3], clip[1] / clip[3], clip[2] / clip[3], 1.0F};

        float[] unproj = new float[4];
        ReflectionMath.transformVec4(unproj, invVp, ndc);
        assertEquals(worldRel[0], unproj[0] / unproj[3], 1e-3F);
        assertEquals(worldRel[1], unproj[1] / unproj[3], 1e-3F);
        assertEquals(worldRel[2], unproj[2] / unproj[3], 1e-3F);

        float[] singular = new float[16];
        assertFalse(ReflectionMath.invert4x4(invVp, singular));
    }

    @Test
    void waterPlaneFinderRespectsSolidTerrainAndUpdatesMountainLakes() {
        WaterPlaneFinder finder = new WaterPlaneFinder();
        assertEquals(0.0, finder.getLastWaterHeight(), 1e-9);

        // Underground water at y=20 buried under opaque solid grass at y=65 should NOT hijack plane
        WaterPlaneFinder.BlockProbe buriedCaveProbe = new WaterPlaneFinder.BlockProbe() {
            @Override
            public boolean isStillWater(int x, int y, int z) {
                return y == 20;
            }

            @Override
            public boolean isOpaqueSolid(int x, int y, int z) {
                return y == 65;
            }
        };

        for (int i = 0; i < WaterPlaneFinder.SAMPLE_INTERVAL_FRAMES; i++) {
            finder.updateSmoothed(buriedCaveProbe, 0.0, 67.0, 0.0, 0.0F, 15.0F, false, 0.016);
        }
        assertEquals(0.0, finder.getLastWaterHeight(), 1e-9);

        // Surface mountain lake at y=80 should update lastWaterHeight to 2 * (63 - 80) = -34.0
        WaterPlaneFinder.BlockProbe mountainLakeProbe = new WaterPlaneFinder.BlockProbe() {
            @Override
            public boolean isStillWater(int x, int y, int z) {
                return y == 80 && Math.abs(x) <= 8 && Math.abs(z) <= 8;
            }

            @Override
            public boolean isOpaqueSolid(int x, int y, int z) {
                return y < 80;
            }
        };

        // Submerged updates must not fire
        for (int i = 0; i < WaterPlaneFinder.SAMPLE_INTERVAL_FRAMES * 2; i++) {
            finder.updateSmoothed(mountainLakeProbe, 0.0, 81.62, 0.0, 0.0F, 15.0F, true, 0.016);
        }
        assertEquals(0.0, finder.getLastWaterHeight(), 1e-9);

        // First valid above-water sample snaps cleanly to mountain lake height (-34.0)
        finder.updateSmoothed(mountainLakeProbe, 0.0, 82.62, 0.0, 0.0F, 15.0F, false, 0.016);
        assertEquals(-34.0, finder.getLastWaterHeight(), 1e-9);

        double offset = finder.computeWaterHeightOffset(82.62);
        assertEquals(2.0 * (80.0 + WaterPlaneFinder.WATER_SURFACE_FRACTION - 82.62), offset, 1e-6);
        assertEquals((float) (82.62 - 80.875), WaterPlaneFinder.computeClipPlaneH(offset), 1e-4F);
    }

    private static float[] findPointInFrontOfEyeWithWorldHeight(float[] mv, float objY, float eyeZ) {
        // Test simple object points (x, objY, z) and keep one that lands at z_eye < -1
        for (int x = -40; x <= 40; x += 5) {
            for (int z = -40; z <= 40; z += 5) {
                float[] eye = new float[4];
                ReflectionMath.transformVec4(eye, mv, new float[]{x, objY, z, 1.0F});
                if (eye[2] < eyeZ) {
                    return eye;
                }
            }
        }
        return null;
    }

    private static float projectZ(float[] proj, float[] eyePt) {
        float[] clip = new float[4];
        ReflectionMath.transformVec4(clip, proj, eyePt);
        assertTrue(clip[3] > 0.0F, "Point must be in front of camera (w_clip > 0)");
        return clip[2] / clip[3];
    }

    private static float[] buildMirrorMatrix(double waterToOrigin) {
        return new float[]{
                1, 0, 0, 0,
                0, -1, 0, 0,
                0, 0, 1, 0,
                0, (float) waterToOrigin, 0, 1
        };
    }

    private static float[] buildCameraMatrix(float yawDeg, float pitchDeg) {
        float[] rx = rotationX((float) Math.toRadians(pitchDeg));
        float[] ry = rotationY((float) Math.toRadians(yawDeg + 180.0F));
        float[] out = new float[16];
        ReflectionMath.multiply(out, rx, ry);
        return out;
    }

    private static float[] buildPerspective(float fovRad, float aspect, float near, float far) {
        float f = 1.0F / (float) Math.tan(fovRad * 0.5);
        return new float[]{
                f / aspect, 0, 0, 0,
                0, f, 0, 0,
                0, 0, (far + near) / (near - far), -1.0F,
                0, 0, (2.0F * far * near) / (near - far), 0
        };
    }

    private static float[] rotationX(float rad) {
        float c = (float) Math.cos(rad);
        float s = (float) Math.sin(rad);
        return new float[]{
                1, 0, 0, 0,
                0, c, s, 0,
                0, -s, c, 0,
                0, 0, 0, 1
        };
    }

    private static float[] rotationY(float rad) {
        float c = (float) Math.cos(rad);
        float s = (float) Math.sin(rad);
        return new float[]{
                c, 0, -s, 0,
                0, 1, 0, 0,
                s, 0, c, 0,
                0, 0, 0, 1
        };
    }

    private static float[] rotationZ(float rad) {
        float c = (float) Math.cos(rad);
        float s = (float) Math.sin(rad);
        return new float[]{
                c, s, 0, 0,
                -s, c, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1
        };
    }

    private static void assertIdentity(float[] m, float eps) {
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                float expected = (col == row) ? 1.0F : 0.0F;
                assertEquals(expected, m[col * 4 + row], eps, "Mismatch at (" + row + "," + col + ")");
            }
        }
    }
}
