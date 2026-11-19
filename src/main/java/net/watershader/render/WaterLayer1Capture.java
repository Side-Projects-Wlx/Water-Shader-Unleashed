package net.watershader.render;

import org.lwjgl.opengl.GL11;

import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Captures Layer 1 (translucent) geometry during {@code ChunkBuilder.rebuild()} when mods replace
 * vanilla display lists (e.g. Amphetamine) so Water Shader can still render Layer 1 with surface
 * normals under its own {@code transparency} and {@code color_white} shaders.
 */
public final class WaterLayer1Capture {
    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    private static final int INITIAL_WORDS = 4096;

    private static boolean rebuildingChunk = false;
    private static boolean captureMeshEnabled = false;
    private static boolean activeLayer1Block = false;
    private static boolean activeWaterBlock = false;
    private static boolean chunkHasWater = false;
    private static int activeWaterBlockY = 0;

    private static int chunkOriginX = 0;
    private static int chunkOriginY = 0;
    private static int chunkOriginZ = 0;

    // Mirrored Tessellator state during chunk rebuild
    private static int tessMode = GL11.GL_QUADS;
    private static double xOffset = 0.0;
    private static double yOffset = 0.0;
    private static double zOffset = 0.0;
    private static double texU = 0.0;
    private static double texV = 0.0;
    private static int colorRgba = -1;
    private static int packedNormal = StencilNormalHelper.NORMAL_EXCLUDED_NON_WATER;
    private static boolean hasTexture = false;
    private static boolean hasColor = false;
    private static boolean hasNormals = false;
    private static boolean colorDisabled = false;

    private static final int[] quadStaging = new int[4 * WaterLayer1Mesh.INTS_PER_VERTEX];
    private static int quadVertexCount = 0;

    private static int[] outWords = new int[INITIAL_WORDS];
    private static int outWordCount = 0;

    private WaterLayer1Capture() {}

    public static void beginChunkRebuild(boolean enableMeshCapture, int originX, int originY, int originZ) {
        rebuildingChunk = true;
        captureMeshEnabled = enableMeshCapture;
        activeLayer1Block = false;
        activeWaterBlock = false;
        chunkHasWater = false;
        activeWaterBlockY = 0;
        chunkOriginX = originX;
        chunkOriginY = originY;
        chunkOriginZ = originZ;
        quadVertexCount = 0;
        outWordCount = 0;
        tessMode = GL11.GL_QUADS;
        xOffset = 0.0;
        yOffset = 0.0;
        zOffset = 0.0;
    }

    public static WaterLayer1Mesh endChunkRebuild() {
        rebuildingChunk = false;
        activeLayer1Block = false;
        activeWaterBlock = false;
        quadVertexCount = 0;
        if (!captureMeshEnabled || outWordCount <= 0) {
            captureMeshEnabled = false;
            outWordCount = 0;
            return null;
        }
        WaterLayer1Mesh mesh = new WaterLayer1Mesh(chunkOriginX, chunkOriginY, chunkOriginZ, outWords, outWordCount);
        captureMeshEnabled = false;
        outWordCount = 0;
        // Shrink huge temporary buffer if a modded chunk spiked memory
        if (outWords.length > 65536) {
            outWords = new int[INITIAL_WORDS];
        }
        return mesh;
    }

    public static void abortChunkRebuildIfActive() {
        if (rebuildingChunk) {
            rebuildingChunk = false;
            captureMeshEnabled = false;
            activeLayer1Block = false;
            activeWaterBlock = false;
            quadVertexCount = 0;
            outWordCount = 0;
        }
    }

    public static boolean isRebuildingChunk() {
        return rebuildingChunk;
    }

    public static boolean shouldCaptureAmphetLayer1() {
        return rebuildingChunk && captureMeshEnabled && activeLayer1Block;
    }

    public static boolean isActiveLayer1Block() {
        return activeLayer1Block;
    }

    public static boolean isActiveWaterBlock() {
        return activeWaterBlock;
    }

    public static int getActiveWaterBlockY() {
        return activeWaterBlockY;
    }

    public static void beginBlock(boolean layer1, boolean water, int blockY) {
        activeLayer1Block = layer1;
        activeWaterBlock = layer1 && water;
        if (activeWaterBlock) {
            chunkHasWater = true;
        }
        activeWaterBlockY = blockY;
    }

    public static boolean didChunkHaveWater() {
        return chunkHasWater;
    }

    public static void endBlock() {
        activeLayer1Block = false;
        activeWaterBlock = false;
    }

    public static void onStart(int mode) {
        if (!rebuildingChunk || !captureMeshEnabled) {
            return;
        }
        tessMode = mode;
        quadVertexCount = 0;
        hasTexture = false;
        hasColor = false;
        hasNormals = false;
        colorDisabled = false;
        texU = 0.0;
        texV = 0.0;
        colorRgba = -1;
        packedNormal = StencilNormalHelper.NORMAL_EXCLUDED_NON_WATER;
    }

    public static void onDraw() {
        if (!rebuildingChunk || !captureMeshEnabled) {
            return;
        }
        quadVertexCount = 0;
    }

    public static void onSetOffset(double x, double y, double z) {
        if (!rebuildingChunk || !captureMeshEnabled) {
            return;
        }
        xOffset = x;
        yOffset = y;
        zOffset = z;
    }

    public static void onTranslate(float x, float y, float z) {
        if (!rebuildingChunk || !captureMeshEnabled) {
            return;
        }
        xOffset += x;
        yOffset += y;
        zOffset += z;
    }

    public static void onTexture(double u, double v) {
        if (!rebuildingChunk || !captureMeshEnabled) {
            return;
        }
        hasTexture = true;
        texU = u;
        texV = v;
    }

    public static void onColor(int r, int g, int b, int a) {
        if (!rebuildingChunk || !captureMeshEnabled || colorDisabled) {
            return;
        }
        hasColor = true;
        colorRgba = packColor(r, g, b, a);
    }

    public static void onDisableColor() {
        if (!rebuildingChunk || !captureMeshEnabled) {
            return;
        }
        colorDisabled = true;
    }

    public static void onNormal(int normalBits) {
        if (!rebuildingChunk || !captureMeshEnabled) {
            return;
        }
        hasNormals = true;
        packedNormal = normalBits;
    }

    public static void onVertex(double x, double y, double z) {
        if (!shouldCaptureAmphetLayer1()) {
            return;
        }
        int xBits = Float.floatToRawIntBits((float) (x + xOffset));
        int yBits = Float.floatToRawIntBits((float) (y + yOffset));
        int zBits = Float.floatToRawIntBits((float) (z + zOffset));
        int uBits = hasTexture ? Float.floatToRawIntBits((float) texU) : 0;
        int vBits = hasTexture ? Float.floatToRawIntBits((float) texV) : 0;
        int col = hasColor ? colorRgba : -1;
        int norm = hasNormals ? packedNormal : StencilNormalHelper.NORMAL_EXCLUDED_NON_WATER;

        if (tessMode == GL11.GL_QUADS) {
            int base = quadVertexCount * WaterLayer1Mesh.INTS_PER_VERTEX;
            quadStaging[base] = xBits;
            quadStaging[base + 1] = yBits;
            quadStaging[base + 2] = zBits;
            quadStaging[base + 3] = uBits;
            quadStaging[base + 4] = vBits;
            quadStaging[base + 5] = col;
            quadStaging[base + 6] = norm;
            quadStaging[base + 7] = 0;
            quadVertexCount++;
            if (quadVertexCount == 4) {
                emitQuadAsTriangles();
                quadVertexCount = 0;
            }
        } else if (tessMode == GL11.GL_TRIANGLES) {
            appendRawVertexWords(xBits, yBits, zBits, uBits, vBits, col, norm);
        }
    }

    /**
     * Appends one pre-transformed triangle vertex (already offset by {@code Tessellator.xOffset/yOffset/zOffset},
     * as emitted by StationAPI {@code StationTessellatorImpl.quad}) directly into the capture buffer.
     */
    public static void appendPreOffsetTriangleVertex(
            int xBits,
            int yBits,
            int zBits,
            int uBits,
            int vBits,
            int colorBits,
            int normalBits) {
        if (!shouldCaptureAmphetLayer1()) {
            return;
        }
        appendRawVertexWords(xBits, yBits, zBits, uBits, vBits, colorBits, normalBits);
    }

    public static int packColor(int r, int g, int b, int a) {
        int cr = clampByte(r);
        int cg = clampByte(g);
        int cb = clampByte(b);
        int ca = clampByte(a);
        if (LITTLE_ENDIAN) {
            return (ca << 24) | (cb << 16) | (cg << 8) | cr;
        }
        return (cr << 24) | (cg << 16) | (cb << 8) | ca;
    }

    private static void emitQuadAsTriangles() {
        ensureOutCapacity(6 * WaterLayer1Mesh.INTS_PER_VERTEX);
        boolean flipDiag = activeWaterBlock && StencilNormalHelper.shouldFlipWaterQuadDiagonal(
                quadStaging[6],
                quadStaging[WaterLayer1Mesh.INTS_PER_VERTEX + 6],
                quadStaging[2 * WaterLayer1Mesh.INTS_PER_VERTEX + 6],
                quadStaging[3 * WaterLayer1Mesh.INTS_PER_VERTEX + 6]);
        if (flipDiag) {
            copyStagingVertex(1);
            copyStagingVertex(2);
            copyStagingVertex(3);
            copyStagingVertex(1);
            copyStagingVertex(3);
            copyStagingVertex(0);
        } else {
            copyStagingVertex(0);
            copyStagingVertex(1);
            copyStagingVertex(2);
            copyStagingVertex(0);
            copyStagingVertex(2);
            copyStagingVertex(3);
        }
    }

    private static void copyStagingVertex(int vertexIndex) {
        int src = vertexIndex * WaterLayer1Mesh.INTS_PER_VERTEX;
        System.arraycopy(quadStaging, src, outWords, outWordCount, WaterLayer1Mesh.INTS_PER_VERTEX);
        outWordCount += WaterLayer1Mesh.INTS_PER_VERTEX;
    }

    private static void appendRawVertexWords(int xBits, int yBits, int zBits, int uBits, int vBits, int col, int norm) {
        ensureOutCapacity(WaterLayer1Mesh.INTS_PER_VERTEX);
        outWords[outWordCount] = xBits;
        outWords[outWordCount + 1] = yBits;
        outWords[outWordCount + 2] = zBits;
        outWords[outWordCount + 3] = uBits;
        outWords[outWordCount + 4] = vBits;
        outWords[outWordCount + 5] = col;
        outWords[outWordCount + 6] = norm;
        outWords[outWordCount + 7] = 0;
        outWordCount += WaterLayer1Mesh.INTS_PER_VERTEX;
    }

    private static void ensureOutCapacity(int additionalWords) {
        int needed = outWordCount + additionalWords;
        if (needed > outWords.length) {
            int newCap = Math.max(needed, outWords.length * 2);
            outWords = Arrays.copyOf(outWords, newCap);
        }
    }

    private static int clampByte(int c) {
        if (c > 255) {
            return 255;
        }
        if (c < 0) {
            return 0;
        }
        return c;
    }
}
