package net.watershader.render;

/**
 * Active rendering pass during a single multi-pass Water Shader frame.
 */
public enum WaterRenderPass {
    /** Shader pipeline inactive; vanilla / modded rendering runs unintercepted. */
    NONE,
    /** Pass 0: Main world rendered into supersampled FBO 0 ({@code colorMap}). */
    MAIN,
    /** Pass 1: Normal-classified water mask rendered into supersampled FBO 1 ({@code stencilMap}). */
    STENCIL,
    /** Pass 2: Mirrored oblique-clipped world rendered into FBO 2 ({@code reflectedColorMap}). */
    REFLECTION
}
