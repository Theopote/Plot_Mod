package com.plot.ui.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import com.plot.ui.component.BlockIconRenderer;
import com.plot.ui.component.ControlPanelIcons;
import com.plot.ui.panel.layer.LayerPanel;
import com.plot.ui.screen.PlotScreenState;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plot UI 纹理生命周期管理：在界面关闭、资源包重载和客户端退出时释放 GL 纹理。
 */
public final class PlotTextureLifecycle {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/TextureLifecycle");

    private PlotTextureLifecycle() {
    }

    public static void disposeAll() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            disposeAllOnRenderThread();
            return;
        }

        if (RenderSystem.isOnRenderThread()) {
            disposeAllOnRenderThread();
        } else {
            client.execute(PlotTextureLifecycle::disposeAllOnRenderThread);
        }
    }

    private static void disposeAllOnRenderThread() {
        LOGGER.debug("Disposing Plot UI texture resources...");

        TextureManager.getInstance().dispose();
        com.plot.utils.ImGuiUtils.disposeAllTextures();
        ControlPanelIcons.dispose();
        LayerPanel.resetTextures();

        try {
            BlockIconRenderer.getInstance().invalidateAll();
        } catch (IllegalStateException ignored) {
            // BlockIconRenderer 已 close，跳过
        } catch (Exception e) {
            LOGGER.warn("Failed to invalidate block icon textures: {}", e.getMessage());
        }

        if (PlotScreenState.isPlotScreenOpen()) {
            ControlPanelIcons.loadTextures();
        }
    }
}
