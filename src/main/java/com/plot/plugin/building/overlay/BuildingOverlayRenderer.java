package com.plot.plugin.building.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.polygon.PolygonTriangulator;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.canvas.CanvasCamera;
import imgui.ImDrawList;
import imgui.ImVec2;

import java.util.List;

/** 建筑轮廓画布叠加层渲染（L1/L2，与 Ghost Block 预览无关）。 */
public final class BuildingOverlayRenderer {
    private static final float DASH_ON = 7f;
    private static final float DASH_OFF = 5f;
    private static final float PRIMARY_MARKER_RADIUS = 5.5f;

    private BuildingOverlayRenderer() {
    }

    public static void render(ImDrawList drawList, CanvasCamera camera, List<BuildingOverlayEntry> entries) {
        if (drawList == null || camera == null || entries == null || entries.isEmpty()) {
            return;
        }
        for (BuildingOverlayEntry entry : entries) {
            renderEntry(drawList, camera, entry);
        }
    }

    private static void renderEntry(ImDrawList drawList, CanvasCamera camera, BuildingOverlayEntry entry) {
        List<Vec2d> points = entry.outerPoints();
        if (points.size() < 3) {
            return;
        }
        BuildingOverlayStyle style = BuildingOverlayStyle.forState(entry.state());
        renderFill(drawList, camera, points, style.fillColor());
        renderOutline(drawList, camera, points, style);
        if (entry.state() == BuildingOverlayState.PRIMARY) {
            renderPrimaryMarker(drawList, camera, points);
        }
    }

    private static void renderFill(
            ImDrawList drawList,
            CanvasCamera camera,
            List<Vec2d> points,
            int fillColor) {
        if ((fillColor >>> 24) == 0) {
            return;
        }
        PolygonTriangulator.TriangulationResult triangulation = PolygonTriangulator.triangulate(points);
        if (!triangulation.success()) {
            return;
        }
        for (PolygonTriangulator.Triangle triangle : triangulation.triangles()) {
            Vec2d a = points.get(triangle.i0());
            Vec2d b = points.get(triangle.i1());
            Vec2d c = points.get(triangle.i2());
            Vec2d screenA = camera.worldToScreen(a);
            Vec2d screenB = camera.worldToScreen(b);
            Vec2d screenC = camera.worldToScreen(c);
            drawList.addTriangleFilled(
                (float) screenA.x,
                (float) screenA.y,
                (float) screenB.x,
                (float) screenB.y,
                (float) screenC.x,
                (float) screenC.y,
                fillColor);
        }
    }

    private static void renderOutline(
            ImDrawList drawList,
            CanvasCamera camera,
            List<Vec2d> points,
            BuildingOverlayStyle style) {
        int count = points.size();
        for (int i = 0; i < count; i++) {
            Vec2d start = points.get(i);
            Vec2d end = points.get((i + 1) % count);
            if (style.dashed()) {
                drawDashedSegment(drawList, camera, start, end, style.outlineColor(), style.outlineThickness());
            } else {
                drawSolidSegment(drawList, camera, start, end, style.outlineColor(), style.outlineThickness());
            }
        }
    }

    private static void drawSolidSegment(
            ImDrawList drawList,
            CanvasCamera camera,
            Vec2d start,
            Vec2d end,
            int color,
            float thickness) {
        Vec2d screenStart = camera.worldToScreen(start);
        Vec2d screenEnd = camera.worldToScreen(end);
        drawList.addLine(
            (float) screenStart.x,
            (float) screenStart.y,
            (float) screenEnd.x,
            (float) screenEnd.y,
            color,
            thickness);
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
            drawList.addLine(
                x1 + ux * cursor,
                y1 + uy * cursor,
                x1 + ux * segmentEnd,
                y1 + uy * segmentEnd,
                color,
                thickness);
            cursor += DASH_ON + DASH_OFF;
        }
    }

    private static void renderPrimaryMarker(ImDrawList drawList, CanvasCamera camera, List<Vec2d> points) {
        Vec2d centroid = BuildingGeometryUtils.computeCentroid(points);
        Vec2d screen = camera.worldToScreen(centroid);
        float x = (float) screen.x;
        float y = (float) screen.y;
        float radius = PRIMARY_MARKER_RADIUS;
        ImVec2 top = new ImVec2(x, y - radius);
        ImVec2 left = new ImVec2(x - radius * 0.85f, y + radius * 0.65f);
        ImVec2 right = new ImVec2(x + radius * 0.85f, y + radius * 0.65f);
        drawList.addTriangleFilled(top.x, top.y, left.x, left.y, right.x, right.y, PluginUiColors.ACCENT_BLUE);
        drawList.addTriangle(top.x, top.y, left.x, left.y, right.x, right.y, PluginUiColors.RING_DARK, 1.6f);
    }
}
