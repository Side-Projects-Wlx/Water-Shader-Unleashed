package net.watershader.render;

/**
 * Duck interface applied to {@link net.minecraft.client.render.chunk.ChunkBuilder} to attach
 * per-chunk translucent (Layer 1) geometry when rendering alongside mods that replace display lists
 * (such as Amphetamine).
 */
public interface WaterChunkAccess {
    WaterLayer1Mesh watershader$getLayer1Mesh();

    void watershader$setLayer1Mesh(WaterLayer1Mesh mesh);

    void watershader$releaseLayer1Mesh();

    boolean watershader$hasWater();

    void watershader$setHasWater(boolean hasWater);
}
