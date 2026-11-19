package net.watershader.mixin;

import net.minecraft.world.World;
import net.minecraft.world.WorldRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link WorldRegion}'s underlying {@link World} so fluid chunk rebuilds can query raw
 * {@code LightType.SKY} independent of torch/block lighting.
 */
@Mixin(WorldRegion.class)
public interface WorldRegionAccessor {
    @Accessor("world")
    World watershader$getWorld();
}
