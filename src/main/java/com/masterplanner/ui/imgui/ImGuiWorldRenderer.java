package com.masterplanner.ui.imgui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 负责处理全局 ImGui 渲染事件
 * 在 1.21.x 中，这里可以作为 RenderSystem.flipFrame 的钩子或 WorldRenderEvents 的处理者
 */
public class ImGuiWorldRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImGuiWorldRenderer.class);

    public static void init() {
        LOGGER.info("ImGuiWorldRenderer initialized (Placeholder for Direct Rendering)");
        // 如果需要 HUD 渲染或世界内渲染，可以在这里注册事件
    }

}
