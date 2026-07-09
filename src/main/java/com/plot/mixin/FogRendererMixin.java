package com.plot.mixin;

import com.plot.ui.screen.PlotScreenState;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.render.fog.FogRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Plot 界面打开时返回零填充的雾 UBO，禁用世界雾效。
 */
@Mixin(FogRenderer.class)
public class FogRendererMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/FogRendererMixin");

    @Final
    @Shadow
    private GpuBuffer emptyBuffer;

    @Inject(method = "getFogBuffer", at = @At("HEAD"), cancellable = true)
    private void plot$getEmptyFogBufferWhenUiActive(
            FogRenderer.FogType fogType,
            CallbackInfoReturnable<GpuBufferSlice> cir
    ) {
        if (!PlotScreenState.isPlotUiActive()) {
            return;
        }

        int fogSize = FogRenderer.FOG_UBO_SIZE;
        int bufferSize = emptyBuffer.size();
        if (fogSize <= 0 || fogSize > bufferSize) {
            LOGGER.warn(
                    "Cannot disable fog: emptyBuffer size {} incompatible with FOG_UBO_SIZE {}",
                    bufferSize,
                    fogSize
            );
            return;
        }

        cir.setReturnValue(emptyBuffer.slice(0, fogSize));
    }
}
