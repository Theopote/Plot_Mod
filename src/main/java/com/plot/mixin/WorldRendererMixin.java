package com.plot.mixin;

import com.plot.ui.screen.PlotScreenState;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Plot 打开时禁用云渲染。
 */
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void onRenderClouds(CallbackInfo ci) {
        if (PlotScreenState.isPlotUiActive()) {
            ci.cancel();
        }
    }
}
