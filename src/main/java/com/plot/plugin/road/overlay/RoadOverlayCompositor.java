package com.plot.plugin.road.overlay;

import com.plot.ui.canvas.Canvas;
import com.plot.ui.canvas.CanvasCamera;
import imgui.ImDrawList;
import imgui.ImGui;

import java.util.List;

/**
 * 在插件 UI 更新后补绘道路叠加层（画布先于插件面板渲染，需前景层覆盖过期走廊）。
 */
public final class RoadOverlayCompositor {
    private RoadOverlayCompositor() {
    }

    public static void renderForeground(Canvas canvas, CanvasCamera camera, List<RoadOverlayEntry> entries) {
        if (canvas == null || camera == null || entries == null || entries.isEmpty()) {
            return;
        }
        float x = canvas.getScreenX();
        float y = canvas.getScreenY();
        float w = canvas.getScreenWidth();
        float h = canvas.getScreenHeight();
        if (w <= 0f || h <= 0f) {
            return;
        }
        ImDrawList drawList = ImGui.getForegroundDrawList();
        drawList.pushClipRect(x, y, x + w, y + h, true);
        RoadOverlayRenderer.render(drawList, camera, entries);
        drawList.popClipRect();
    }
}
