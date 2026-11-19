package net.watershader.mixin;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes third-person camera state on {@link GameRenderer} so {@code WaterShaderPipeline} can
 * compute the exact F5 camera world position matching {@code GameRenderer.applyCameraTransform}.
 */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor("thirdPersonDistance")
    float watershader$getThirdPersonDistance();

    @Accessor("prevThirdPersonDistance")
    float watershader$getPrevThirdPersonDistance();

    @Accessor("thirdPersonYaw")
    float watershader$getThirdPersonYaw();

    @Accessor("prevThirdPersonYaw")
    float watershader$getPrevThirdPersonYaw();

    @Accessor("thirdPersonPitch")
    float watershader$getThirdPersonPitch();

    @Accessor("prevThirdPersonPitch")
    float watershader$getPrevThirdPersonPitch();
}
