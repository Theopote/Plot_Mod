package com.plot.mixin;

import com.plot.camera.ChunkUpdater;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Plot 正交视距可超过原版渲染距离选项上限（16/32）。
 * 在 HEAD 直接返回 override，避免依赖 RETURN 时机或其它 mixin 干扰。
 */
@Mixin(GameOptions.class)
public class GameOptionsMixin {
    @Inject(method = "getClampedViewDistance", at = @At("HEAD"), cancellable = true)
    private void plot$overrideClampedViewDistance(CallbackInfoReturnable<Integer> cir) {
        int override = ChunkUpdater.getPlotRenderDistanceOverride();
        if (override > 0) {
            cir.setReturnValue(override);
        }
    }
}
