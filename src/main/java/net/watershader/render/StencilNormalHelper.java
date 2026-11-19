package net.watershader.render;

/**
 * Normal-vector encoding and water-surface classification helpers for the Pass 1 stencil shader.
 *
 * <p>Contract with {@code assets/watershader/shaders/color_white.vert} / {@code color_white.frag}
 * and {@code transparency.vert} / {@code transparency.frag}:
 * <ul>
 *   <li>{@code gl_Normal.x} ({@code [0, 1]}) carries normalized sky-light exposure.</li>
 *   <li>{@code gl_Normal.y} in {@code [-1, 0]} encodes the continuous still-to-flowing surface
 *       weight ({@code -1} = calm still water, {@code 0} = flowing water, slopes, sides, waterfalls),
 *       interpolated smoothly across block boundaries.</li>
 *   <li>{@code gl_Normal.y >= +0.5} ({@code (0, +1, 0)}) marks non-water translucent Layer 1
 *       blocks (Ice, Portals, stained/custom blocks, excluded blocks) so they evaluate to black
 *       in {@code stencilMap}.</li>
 *   <li>{@code gl_Normal.z} ({@code +1} for isolated 1x1 water blocks, {@code 0} otherwise)
 *       suppresses planar reflections in {@code color_white} while preserving calm water
 *       transparency in {@code transparency.frag}.</li>
 * </ul>
 */
public final class StencilNormalHelper {
    public static final int NORMAL_EXCLUDED_NON_WATER = packNormal(0.0F, 1.0F, 0.0F);
    public static final int NORMAL_STILL_WATER = packNormal(0.0F, -1.0F, 0.0F);
    public static final int NORMAL_FLOWING_OR_SIDE_WATER = packNormal(0.0F, 0.0F, 0.0F);

    public static final double STILL_HEIGHT_MIN = 0.88;
    public static final double STILL_HEIGHT_MAX = 0.89;
    public static final double STILL_WATER_SURFACE_OFFSET = 8.0 / 9.0;
    public static final double UPPER_CONTACT_CLAMP_THRESHOLD = 0.90;

    private StencilNormalHelper() {}

    /**
     * Packs an XYZ normal into OpenGL {@code GL_BYTE} (little-endian {@code [bx, by, bz, 0]}),
     * clamping to {@code [-1, 1]}, scaling by {@code 127.0F} (avoiding vanilla's {@code 128 -> -128}
     * overflow on +1.0 X), and masking each signed byte with {@code 0xFF} so negative components
     * never sign-extend into upper bytes.
     */
    public static int packNormal(float x, float y, float z) {
        byte bx = (byte) (int) (clampUnit(x) * 127.0F);
        byte by = (byte) (int) (clampUnit(y) * 127.0F);
        byte bz = (byte) (int) (clampUnit(z) * 127.0F);
        return (bx & 0xFF) | ((by & 0xFF) << 8) | ((bz & 0xFF) << 16);
    }

    /**
     * Packs normalized {@code [0, 1]} sky exposure into {@code gl_Normal.x} alongside stencil
     * classification {@code normalY} into {@code gl_Normal.y} (leaving {@code gl_Normal.z == 0}).
     */
    public static int packWaterNormal(float skyExposure, float normalY) {
        return packNormal(clamp01(skyExposure), normalY, 0.0F);
    }

    /**
     * Unpacks the signed {@code y} byte component (normalized back to roughly {@code [-1, +1]})
     * from a packed 32-bit normal int.
     */
    public static byte unpackNormalYByte(int packedNormal) {
        return (byte) ((packedNormal >>> 8) & 0xFF);
    }

    public static byte unpackNormalXByte(int packedNormal) {
        return (byte) (packedNormal & 0xFF);
    }

    public static byte unpackNormalZByte(int packedNormal) {
        return (byte) ((packedNormal >>> 16) & 0xFF);
    }

    /**
     * Classifies a top-face fluid vertex passed to {@code Tessellator.vertex(vx, vy, vz, u, v)}
     * inside {@code BlockRenderManager.renderFluid(block, bx, by, bz)} into stencil normal Y
     * ({@code -1.0F} for still water top, {@code 0.0F} for flowing/sloped water).
     */
    public static float classifyTopFluidVertex(double vertexY, int blockY) {
        double height = vertexY - (double) blockY;
        return (height >= STILL_HEIGHT_MIN && height <= STILL_HEIGHT_MAX) ? -1.0F : 0.0F;
    }

    /**
     * Classifies a pre-computed corner fluid height into stencil normal Y.
     */
    public static float classifyCornerHeight(float height) {
        return (height >= 0.88F && height <= 0.89F) ? -1.0F : 0.0F;
    }

    /**
     * Classifies a top-face fluid corner vertex using surrounding reflecting still-water neighbor
     * counts at {@code blockY} (with zero adjacent flowing neighbors).
     */
    public static float classifyCornerNormalY(
            double vertexY,
            int blockY,
            int stillNeighborsAtY,
            int flowingWaterAtY) {
        double height = vertexY - (double) blockY;
        if (height > STILL_HEIGHT_MAX) {
            return 0.0F;
        }
        if (stillNeighborsAtY > 0) {
            int totalWater = stillNeighborsAtY + Math.max(0, flowingWaterAtY);
            return -clamp01((float) stillNeighborsAtY / (float) totalWater);
        }
        return 0.0F;
    }

    /**
     * Returns {@code true} when a water quad's diagonal should flip from {@code (0, 2)} to
     * {@code (1, 3)} so a single differing corner (e.g., a waterfall/inflow contact corner)
     * interpolates symmetrically across both triangles instead of pinching along one half.
     */
    public static boolean shouldFlipWaterQuadDiagonal(
            int packedNormal0,
            int packedNormal1,
            int packedNormal2,
            int packedNormal3) {
        int ny0 = unpackNormalYByte(packedNormal0);
        int ny1 = unpackNormalYByte(packedNormal1);
        int ny2 = unpackNormalYByte(packedNormal2);
        int ny3 = unpackNormalYByte(packedNormal3);
        return Math.abs(ny1 - ny3) > Math.abs(ny0 - ny2);
    }

    /**
     * Resolves the emitted Y coordinate for both top-face fluid vertices ({@code ordinals 0..3})
     * and side-face top vertices ({@code ordinals 4..5}) so top and side edges always meet identically.
     *
     * <p>When a corner at {@code blockY} touches an upper water block (vanilla raises it to
     * {@code blockY + 1.0 > blockY + 0.90}) and at least one block around that corner at
     * {@code blockY} is an open-still water block, clamps the corner cleanly onto the planar
     * still-water height {@code blockY + 8/9}. Lower flowing top vertices are never pulled below
     * {@code blockY}, preventing solid-ledge poke-through and top/side height mismatches.
     */
    public static double resolveFluidTopVertexY(double vertexY, int blockY, boolean cornerHasOpenStillAtY) {
        if (cornerHasOpenStillAtY && vertexY > (double) blockY + UPPER_CONTACT_CLAMP_THRESHOLD) {
            return (double) blockY + STILL_WATER_SURFACE_OFFSET;
        }
        return vertexY;
    }

    /**
     * Resolves the emitted Y coordinate for side-face bottom vertices ({@code ordinals 6..7}).
     *
     * <p>When the fluid block sits directly above another water block ({@code waterDirectlyBelow})
     * and the bottom corner borders an adjacent open-still pool at {@code blockY - 1} whose top
     * was clamped from {@code blockY} down to {@code (blockY - 1) + 8/9}, extends the vertical
     * side-face bottom to that exact bit-for-bit height to seal the {@code 1/9}-block waterfall gap
     * without Z-fighting over solid ledges or dangling over air.
     */
    public static double resolveFluidSideBottomVertexY(
            double vertexY,
            int blockY,
            boolean waterDirectlyBelow,
            boolean cornerHasLowerOpenStill) {
        if (blockY > 0 && waterDirectlyBelow && cornerHasLowerOpenStill) {
            return (double) (blockY - 1) + STILL_WATER_SURFACE_OFFSET;
        }
        return vertexY;
    }

    private static float clamp01(float v) {
        if (v < 0.0F) {
            return 0.0F;
        }
        if (v > 1.0F) {
            return 1.0F;
        }
        return v;
    }

    private static float clampUnit(float v) {
        if (v > 1.0F) {
            return 1.0F;
        }
        if (v < -1.0F) {
            return -1.0F;
        }
        return v;
    }
}
