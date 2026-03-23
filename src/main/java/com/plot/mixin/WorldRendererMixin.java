package com.plot.mixin;

import com.plot.ui.screen.PlotScreenState;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * WorldRenderer Mixin
 * 用于在 Plot 屏幕打开时禁用云渲染和雾渲染
 */
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    
    /**
     * 拦截云渲染方法，在 Plot 屏幕打开时跳过云渲染
     */
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void onRenderClouds(CallbackInfo ci) {
        // 如果 Plot 屏幕打开，跳过云渲染
        if (PlotScreenState.isPlotScreenOpen()) {
            ci.cancel();
        }
    }
}
