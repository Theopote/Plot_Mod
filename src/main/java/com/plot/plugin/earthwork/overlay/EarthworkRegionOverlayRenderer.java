package com.plot.plugin.earthwork.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.canvas.CanvasCamera;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

import java.util.List;

/** 土方区域画布叠加层渲染（填充含孔洞 + 描边）。 */
public final class EarthworkRegionOverlayRenderer {
    private static final float DASH_ON = 7f;
    private static final float DASH_OFF = 5f;
    private static final float SELECTED_MARKER_RADIUS = 5.5f;
    private static final float VIEWPORT_MARGIN = 64f;

    private EarthworkRegionOverlayRenderer() {
    }

    public static void render(ImDrawList drawList, CanvasCamera camera, List<EarthworkRegionOverlayEntry> entries) {
        if (drawList == null || camera == null || entries == null || entries.isEmpty()) {
            return;
        }
        for (EarthworkRegionOverlayEntry entry : entries) {
            renderEntry(drawList, camera, entry);
        }
    }

    private static void renderEntry(ImDrawList drawList, CanvasCamera camera, EarthworkRegionOverlayEntry entry) {
        List<Vec2d> points = entry.outerPoints();
        if (points.size() < 3) {
            return;
        }
        EarthworkRegionOverlayStyle style = EarthworkRegionOverlayStyle.forState(
            entry.state(),
            EarthworkRegionOverlayController.stableColor(entry.colorIndex()));
        renderFill(drawList, camera, entry, style.fillColor());
        renderOutline(drawList, camera, points, style);
        for (List<Vec2d> hole : entry.holes()) {
            if (hole != null && hole.size() >= 3) {
                renderOutline(drawList, camera, hole, style);
            }
        }
        if (entry.state() == EarthworkRegionOverlayState.SELECTED) {
            renderSelectedMarker(drawList, camera, entry);
        }
    }

    private static void renderFill(
            ImDrawList drawList,
            CanvasCamera camera,
            EarthworkRegionOverlayEntry entry,
            int fillColor) {
        if ((fillColor >>> 24) == 0) {
            return;
        }
        for (EarthworkRegionTriangulationCache.ScreenTriangle triangle
                : EarthworkRegionTriangulationCache.resolveScreenTriangles(entry, camera)) {
            if (!triangleVisible(triangle)) {
                continue;
            }
            drawList.addTriangleFilled(
                triangle.ax(), triangle.ay(),
                triangle.bx(), triangle.by(),
                triangle.cx(), triangle.cy(),
                fillColor);
        }
    }

    private static boolean triangleVisible(EarthworkRegionTriangulationCache.ScreenTriangle t) {
        float displayW = ImGui.getIO().getDisplaySizeX();
        float displayH = ImGui.getIO().getDisplaySizeY();
        float minX = Math.min(t.ax(), Math.min(t.bx(), t.cx()));
        float maxX = Math.max(t.ax(), Math.max(t.bx(), t.cx()));
        float minY = Math.min(t.ay(), Math.min(t.by(), t.cy()));
        float maxY = Math.max(t.ay(), Math.max(t.by(), t.cy()));
        return maxX >= -VIEWPORT_MARGIN
            && maxY >= -VIEWPORT_MARGIN
            && minX <= displayW + VIEWPORT_MARGIN
            && minY <= displayH + VIEWPORT_MARGIN;
    }

    private static void renderOutline(
            ImDrawList drawList,
            CanvasCamera camera,
            List<Vec2d> points,
            EarthworkRegionOverlayStyle style) {
        int count = points.size();
        for (int i = 0; i < count; i++) {
            Vec2d start = points.get(i);
            Vec2d end = points.get((i + 1) % count);
            if (style.dashed()) {
                drawDashedSegment(drawList, camera, start, end, style.outlineColor(), style.outlineThickness());
            } else {
                Vec2d screenStart = camera.worldToScreen(start);
                Vec2d screenEnd = camera.worldToScreen(end);
                if (!segmentVisible(
                        (float) screenStart.x, (float) screenStart.y,
                        (float) screenEnd.x, (float) screenEnd.y)) {
                    continue;
                }
                drawList.addLine(
                    (float) screenStart.x,
                    (float) screenStart.y,
                    (float) screenEnd.x,
                    (float) screenEnd.y,
                    style.outlineColor(),
                    style.outlineThickness());
            }
        }
    }

    private static void drawDashedSegment(
            ImDrawList drawList,
            CanvasCamera camera,
            Vec2d start,
            Vec2d end,
            int color,
            float thickness) {
        Vec2d screenStart = camera.worldToScreen(start);
        Vec2d screenEnd = camera.worldToScreen(end);
        float x1 = (float) screenStart.x;
        float y1 = (float) screenStart.y;
        float x2 = (float) screenEnd.x;
        float y2 = (float) screenEnd.y;
        if (!segmentVisible(x1, y1, x2, y2)) {
            return;
        }
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.hypot(dx, dy);
        if (length < 1f) {
            return;
        }
        float ux = dx / length;
        float uy = dy / length;
        float cursor = 0f;
        while (cursor < length) {
            float segmentEnd = Math.min(cursor + DASH_ON, length);
            float sx = x1 + ux * cursor;
            float sy = y1 + uy * cursor;
            float ex = x1 + ux * segmentEnd;
            float ey = y1 + uy * segmentEnd;
            if (segmentVisible(sx, sy, ex, ey)) {
                drawList.addLine(sx, sy, ex, ey, color, thickness);
            }
            cursor += DASH_ON + DASH_OFF;
        }
    }

    private static boolean segmentVisible(float x1, float y1, float x2, float y2) {
        float displayW = ImGui.getIO().getDisplaySizeX();
        float displayH = ImGui.getIO().getDisplaySizeY();
        float minX = Math.min(x1, x2);
        float maxX = Math.max(x1, x2);
        float minY = Math.min(y1, y2);
        float maxY = Math.max(y1, y2);
        return maxX >= -VIEWPORT_MARGIN
            && maxY >= -VIEWPORT_MARGIN
            && minX <= displayW + VIEWPORT_MARGIN
            && minY <= displayH + VIEWPORT_MARGIN;
    }

    private static void renderSelectedMarker(
            ImDrawList drawList,
            CanvasCamera camera,
            EarthworkRegionOverlayEntry entry) {
        // 质心落在孔洞内时退回外轮廓顶点平均
        double cx = 0;
        double cy = 0;
        List<Vec2d> points = entry.outerPoints();
        for (Vec2d point : points) {
            cx += point.x;
            cy += point.y;
        }
        cx /= points.size();
        cy /= points.size();
        if (!entry.containsWorldPoint(cx, cy)) {
            // 在外环边上取一点作为标记，避免画在孔洞空白处
            Vec2d edge = points.get(0);
            cx = edge.x;
            cy = edge.y;
        }
        Vec2d screen = camera.worldToScreen(new Vec2d(cx, cy));
        float x = (float) screen.x;
        float y = (float) screen.y;
        float radius = SELECTED_MARKER_RADIUS;
        ImVec2 top = new ImVec2(x, y - radius);
        ImVec2 left = new ImVec2(x - radius * 0.85f, y + radius * 0.65f);
        ImVec2 right = new ImVec2(x + radius * 0.85f, y + radius * 0.65f);
        int accent = EarthworkRegionOverlayController.stableColor(entry.colorIndex());
        drawList.addTriangleFilled(top.x, top.y, left.x, left.y, right.x, right.y, accent);
        drawList.addTriangle(top.x, top.y, left.x, left.y, right.x, right.y, PluginUiColors.RING_DARK, 1.6f);
    }
}
