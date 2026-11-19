package net.watershader.mixin;

import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.watershader.render.WaterShaderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Prevents mobs and the player (including first-person hand and third-person model) from flashing
 * dark when bobbing in surface water or wading past 66% bounding-box depth.
 */
@Mixin(Entity.class)
final class EntityMixin {
    @Redirect(
            method = "getBrightnessAtEyes(F)F",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;method_1782(III)F"
            )
    )
    private float watershader$sampleSurfaceBrightnessInWater(World world, int x, int y, int z) {
        if (WaterShaderPipeline.isActive()
                && world != null
                && y >= 0
                && y < 127
                && world.getMaterial(x, y, z) == Material.WATER) {
            int checkY = y + 1;
            int maxLift = Math.min(127, y + 3);
            while (checkY < maxLift && world.getMaterial(x, checkY, z) == Material.WATER) {
                checkY++;
            }
            if (world.getMaterial(x, checkY, z) != Material.WATER
                    && !WaterShaderPipeline.isOpaqueSolid(world, x, checkY, z)) {
                return Math.max(world.method_1782(x, y, z), world.method_1782(x, checkY, z));
            }
        }
        return world.method_1782(x, y, z);
    }
}
