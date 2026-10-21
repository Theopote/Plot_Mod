package com.plot.mixin;

import com.plot.camera.PlotRenderDistanceState;
import com.plot.ui.screen.PlotScreenState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * WorldRenderer Mixin：Plot 打开时禁用云；reload 后核对视距是否真正抬高。
 */
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @Unique
    private static final Logger plot$LOGGER = LoggerFactory.getLogger("Plot/WorldRendererMixin");

    @Shadow
    private int viewDistance;

    /**
     * 拦截云渲染方法，在 Plot 屏幕打开时跳过云渲染
     */
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void onRenderClouds(CallbackInfo ci) {
        if (PlotScreenState.isPlotUiActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "reload()V", at = @At("RETURN"))
    private void plot$logViewDistanceAfterReload(CallbackInfo ci) {
        int override = PlotRenderDistanceState.getOverrideChunks();
        if (override <= 0) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        int clamped = client != null && client.options != null
            ? client.options.getClampedViewDistance()
            : -1;
        plot$LOGGER.info(
            "WorldRenderer.reload 完成: field.viewDistance={}, getClampedViewDistance={}, plotOverride={}",
            this.viewDistance, clamped, override);
        if (this.viewDistance < override) {
            plot$LOGGER.warn(
                "WorldRenderer 视距未跟上 Plot override（{} < {}），地形圆可能仍偏小",
                this.viewDistance, override);
        }
    }
}
