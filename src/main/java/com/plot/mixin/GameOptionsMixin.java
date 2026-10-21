package com.plot.mixin;

import com.plot.camera.ChunkUpdater;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 仅在 Plot override 大于原版钳制值时抬高，绝不把渲染距离压小。
 */
@Mixin(GameOptions.class)
public class GameOptionsMixin {
    @Inject(method = "getClampedViewDistance", at = @At("RETURN"), cancellable = true)
    private void plot$boostClampedViewDistance(CallbackInfoReturnable<Integer> cir) {
        int override = ChunkUpdater.getPlotRenderDistanceOverride();
        int vanilla = cir.getReturnValueI();
        if (override > vanilla) {
            cir.setReturnValue(override);
        }
    }
}
