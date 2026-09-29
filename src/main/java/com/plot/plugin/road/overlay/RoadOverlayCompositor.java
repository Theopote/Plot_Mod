package com.plot.plugin.road.overlay;

import com.plot.ui.canvas.Canvas;
import com.plot.ui.canvas.CanvasCamera;
import imgui.ImDrawList;
import imgui.ImGui;

import java.util.List;

/**
 * 在插件 UI 更新后补绘道路叠加层：使用 BackgroundDrawList 并裁剪到画布区域，
 * 保证叠加层位于画布之上、ImGui 面板之下。
 */
public final class RoadOverlayCompositor {
    private RoadOverlayCompositor() {
    }

    public static void renderOnCanvas(
            Canvas canvas,
            CanvasCamera camera,
            List<RoadOverlayEntry> entries,
            List<RoadJunctionOverlayEntry> junctionEntries) {
        if (canvas == null || camera == null) {
            return;
        }
        float x = canvas.getScreenX();
        float y = canvas.getScreenY();
        float w = canvas.getScreenWidth();
        float h = canvas.getScreenHeight();
        if (w <= 0f || h <= 0f) {
            return;
        }
        ImDrawList drawList = ImGui.getBackgroundDrawList();
        if (drawList == null) {
            return;
        }
        drawList.pushClipRect(x, y, x + w, y + h, true);
        if (entries != null && !entries.isEmpty()) {
            RoadOverlayRenderer.render(drawList, camera, entries);
        }
        if (junctionEntries != null && !junctionEntries.isEmpty()) {
            RoadJunctionOverlayRenderer.render(drawList, camera, junctionEntries);
        }
        drawList.popClipRect();
    }
}
