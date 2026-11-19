package net.watershader.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WaterPlaneFinderTest {

    @Test
    void viewWeightedScanSelectsSmallElevatedPondAheadOverLargeRiverBehind() {
        WaterPlaneFinder.BlockProbe probe = new WaterPlaneFinder.BlockProbe() {
            @Override
            public boolean isStillWater(int x, int y, int z) {
                // Small 2x2 elevated pond in front of camera (+Z direction at y = 68)
                if (y == 68 && x >= 0 && x <= 1 && z >= 5 && z <= 6) {
                    return true;
                }
                // Larger river behind camera (-Z direction at y = 63)
                return y == 63 && x >= -14 && x <= 14 && z >= -14 && z <= -2;
            }

            @Override
            public boolean isOpaqueSolid(int x, int y, int z) {
                if (x >= -1 && x <= 2 && z >= 4 && z <= 7) {
                    return y < 68;
                }
                return y < 63;
            }
        };

        Integer selected = WaterPlaneFinder.findDominantWaterBlockY(
                probe,
                0.5,
                70.62,
                0.5,
                0.0F,
                25.0F);
        assertEquals(Integer.valueOf(68), selected);
    }

    @Test
    void updateSmoothedSnapsInitiallyThenInterpolatesSmoothlyWithoutOneFramePop() {
        WaterPlaneFinder finder = new WaterPlaneFinder();

        WaterPlaneFinder.BlockProbe seaLevelProbe = new WaterPlaneFinder.BlockProbe() {
            @Override
            public boolean isStillWater(int x, int y, int z) {
                return y == 63 && Math.abs(x) <= 6 && z >= 2 && z <= 10;
            }

            @Override
            public boolean isOpaqueSolid(int x, int y, int z) {
                return y < 63;
            }
        };

        // First valid sample snaps directly to sea level (y = 63 -> offset height = 0.0)
        finder.updateSmoothed(seaLevelProbe, 0.5, 66.62, 0.5, 0.0F, 20.0F, false, 0.016);
        assertEquals(0.0, finder.getLastWaterHeight(), 1e-6);

        // Move slightly (within teleport threshold) to a pond at y = 68 -> target = 2 * (63 - 68) = -10.0
        WaterPlaneFinder.BlockProbe elevatedPondProbe = new WaterPlaneFinder.BlockProbe() {
            @Override
            public boolean isStillWater(int x, int y, int z) {
                return y == 68 && x >= 0 && x <= 3 && z >= 3 && z <= 8;
            }

            @Override
            public boolean isOpaqueSolid(int x, int y, int z) {
                return y < 68;
            }
        };

        for (int i = 0; i < WaterPlaneFinder.SAMPLE_INTERVAL_FRAMES; i++) {
            finder.updateSmoothed(elevatedPondProbe, 0.5, 70.62, 1.5, 0.0F, 25.0F, false, 0.016);
        }

        double intermediate = finder.getLastWaterHeight();
        assertTrue(intermediate < -0.1 && intermediate > -9.9,
                "Expected smooth partial transition instead of 1-frame snap, got " + intermediate);

        for (int i = 0; i < 80; i++) {
            finder.updateSmoothed(elevatedPondProbe, 0.5, 70.62, 1.5, 0.0F, 25.0F, false, 0.016);
        }
        assertEquals(-10.0, finder.getLastWaterHeight(), 0.05);
    }

    @Test
    void ignoresIsolatedSingleBlockAndSelectsConnectedTwoBlockPool() {
        // World has a 1x1 elevated block at (0, 70, 3) and a connected 2x1 pool at y = 63
        WaterPlaneFinder.BlockProbe probe = new WaterPlaneFinder.BlockProbe() {
            private boolean rawOpenStill(int x, int y, int z) {
                if (x == 0 && y == 70 && z == 3) {
                    return true; // isolated 1x1 block
                }
                return y == 63 && z == 5 && (x == 0 || x == 1); // 2x1 connected pool
            }

            @Override
            public boolean isStillWater(int x, int y, int z) {
                return rawOpenStill(x, y, z)
                        && (rawOpenStill(x + 1, y, z)
                        || rawOpenStill(x - 1, y, z)
                        || rawOpenStill(x, y, z + 1)
                        || rawOpenStill(x, y, z - 1));
            }

            @Override
            public boolean isOpaqueSolid(int x, int y, int z) {
                return y < 63;
            }
        };

        Integer selected = WaterPlaneFinder.findDominantWaterBlockY(
                probe,
                0.5,
                71.62,
                0.5,
                0.0F,
                20.0F);
        assertEquals(Integer.valueOf(63), selected);
    }
}
