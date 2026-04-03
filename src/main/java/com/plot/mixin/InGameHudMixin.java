package com.plot.mixin;

import com.plot.ui.screen.PlotScreenState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * InGameHud Mixin
 * 用于在 Plot 屏幕打开时强制隐藏 HUD
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {
    
    /**
     * 拦截物品栏渲染方法，在 Plot 屏幕打开时跳过渲染
     */
    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void onRenderHotbar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        // 如果 Plot 屏幕打开，跳过物品栏渲染
        if (PlotScreenState.isPlotUiActive()) {
            ci.cancel();
        }
    }
    
    /**
     * 拦截手持物品工具提示渲染方法，在 Plot 屏幕打开时跳过渲染
     */
    @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"), cancellable = true)
    private void onRenderHeldItemTooltip(DrawContext context, CallbackInfo ci) {
        // 如果 Plot 屏幕打开，跳过手持物品工具提示渲染
        if (PlotScreenState.isPlotUiActive()) {
            ci.cancel();
        }
    }
}
