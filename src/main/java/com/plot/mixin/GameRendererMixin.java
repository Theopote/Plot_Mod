package com.plot.mixin;

import com.plot.camera.CameraManager;
import com.plot.ui.screen.PlotScreenState;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "getBasicProjectionMatrix", at = @At("HEAD"), cancellable = true)
    private void onGetProjectionMatrix(CallbackInfoReturnable<Matrix4f> cir) {
        CameraManager cameraManager = CameraManager.getInstance();
        if (cameraManager.isOrthographic()) {
            cir.setReturnValue(cameraManager.getOrthographicCamera().getProjectionMatrix());
        }
    }
    
    /**
     * 拦截玩家第一人称手臂渲染方法，在 Plot 屏幕打开时跳过渲染
     */
    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void onRenderHand(CallbackInfo ci) {
        // 如果 Plot 屏幕打开，跳过玩家手臂渲染
        if (PlotScreenState.isPlotScreenOpen()) {
            ci.cancel();
        }
    }
} 