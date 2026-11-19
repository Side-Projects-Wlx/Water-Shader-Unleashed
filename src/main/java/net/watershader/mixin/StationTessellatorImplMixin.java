package net.watershader.mixin;

import net.watershader.render.StencilNormalHelper;
import net.watershader.render.WaterLayer1Capture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Compatibility bridge for StationAPI's {@code StationTessellatorImpl.quad(...)}.
 *
 * <p>Normalizes non-water Layer 1 baked model faces to {@code (0, +1, 0)} (so DOWN faces on custom
 * Layer 1 models do not masquerade as still water), and captures already-offset triangles before
 * Amphetamine's default-priority cancel removes them from {@link net.minecraft.client.render.Tessellator}.
 */
@Pseudo
@Mixin(
        targets = "net/modificationstation/stationapi/impl/client/render/StationTessellatorImpl",
        priority = 900,
        remap = false
)
final class StationTessellatorImplMixin {
    @Shadow
    @Final
    private int[] fastVertexData;

    @Inject(
            method = "quad",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/modificationstation/stationapi/mixin/render/client/TessellatorAccessor;setHasNormals(Z)V",
                    shift = At.Shift.AFTER
            )
    )
    private void watershader$onStationQuad(CallbackInfo ci) {
        if (!WaterLayer1Capture.isActiveLayer1Block()) {
            return;
        }
        int[] v = this.fastVertexData;
        if (!WaterLayer1Capture.isActiveWaterBlock()) {
            v[6] = StencilNormalHelper.NORMAL_EXCLUDED_NON_WATER;
            v[14] = StencilNormalHelper.NORMAL_EXCLUDED_NON_WATER;
            v[22] = StencilNormalHelper.NORMAL_EXCLUDED_NON_WATER;
            v[30] = StencilNormalHelper.NORMAL_EXCLUDED_NON_WATER;
        }
        if (WaterLayer1Capture.shouldCaptureAmphetLayer1()) {
            emitStationVertex(v, 0);
            emitStationVertex(v, 8);
            emitStationVertex(v, 16);
            emitStationVertex(v, 0);
            emitStationVertex(v, 16);
            emitStationVertex(v, 24);
        }
    }

    private static void emitStationVertex(int[] v, int base) {
        WaterLayer1Capture.appendPreOffsetTriangleVertex(
                v[base],
                v[base + 1],
                v[base + 2],
                v[base + 3],
                v[base + 4],
                v[base + 5],
                v[base + 6]);
    }
}
