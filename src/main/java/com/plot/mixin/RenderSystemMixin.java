package com.plot.mixin;

import com.plot.ui.screen.PlotScreen;
import com.plot.ui.imgui.ImGuiRenderer;
import com.plot.ui.imgui.GuiOverlayRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.tracy.TracyFrameCapturer;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/**
 * 1.21.x：将 ImGui 的最终绘制放到 swapBuffers 前，避免被新渲染管线的 RenderPass/命令队列覆盖。
 * 参考 ChronoBlocks 的实现方式
 */
@Mixin(RenderSystem.class)
public class RenderSystemMixin {

    @Inject(
        method = "flipFrame",
        at = @At(
            value = "INVOKE",
            // 1.21.11 里 RenderSystem.flipFrame 不一定直接调用 Window.swapBuffers()（或签名不同），
            // 但最终一定会走到 GLFW.glfwSwapBuffers(windowHandle)。
            target = "Lorg/lwjgl/glfw/GLFW;glfwSwapBuffers(J)V",
            shift = At.Shift.BEFORE
        )
    )
    private static void plot$beforeSwapBuffers(Window window, TracyFrameCapturer capturer, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof PlotScreen)) {
            return;
        }
        ImGuiRenderer renderer = ImGuiRenderer.getInstance();
        if (renderer.isInitialized() && renderer.hasPendingDrawData()) {
            renderer.renderPendingDrawData();
        }
        GuiOverlayRenderer.flushPending();

    }
}
