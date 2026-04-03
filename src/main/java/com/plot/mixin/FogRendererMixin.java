package com.plot.mixin;

import com.plot.ui.screen.PlotScreenState;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.render.fog.FogRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FogRenderer Mixin
 * 用于在 Plot 屏幕打开时禁用雾渲染
 */
@Mixin(FogRenderer.class)
public class FogRendererMixin {
    
    @Final
    @Shadow
    private com.mojang.blaze3d.buffers.GpuBuffer emptyBuffer;
    
    /**
     * 拦截获取雾缓冲区方法，在 Plot 屏幕打开时强制返回空缓冲区（禁用雾）
     * 根据反编译代码，FOG_UBO_SIZE = vec4(16字节) + 6个float(24字节) = 40字节，但实际可能是64字节对齐
     */
    @Inject(method = "getFogBuffer", at = @At("RETURN"), cancellable = true)
    private void onGetFogBuffer(FogRenderer.FogType fogType, CallbackInfoReturnable<GpuBufferSlice> cir) {
        // 如果 Plot 屏幕打开，强制返回空缓冲区（禁用雾）
        if (PlotScreenState.isPlotUiActive()) {
            // 返回空缓冲区，相当于禁用雾
            // 尝试使用不同的大小，从大到小，找到可用的最大值
            // slice 方法的 length 参数必须小于缓冲区大小（不能等于）
            // 1.21.10: GpuBuffer.slice 使用 int 参数
            int[] sizes = {40, 32, 16};
            for (int size : sizes) {
                try {
                    cir.setReturnValue(emptyBuffer.slice(0, size));
                    return;
                } catch (IllegalArgumentException e) {
                    // 继续尝试下一个更小的值
                }
            }
            // 如果所有尝试都失败，使用原始返回值（不修改，避免崩溃）
        }
    }
}
