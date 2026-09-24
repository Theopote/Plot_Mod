package com.plot.plugin.road.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.polygon.PolygonTriangulator;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.canvas.CanvasCamera;
import imgui.ImDrawList;
import imgui.ImVec2;

import java.util.List;

/** 道路走廊画布叠加层渲染（L1/L2，与 Ghost Block 预览无关）。 */
public final class RoadOverlayRenderer {
    private static final float DASH_ON = 7f;
    private static final float DASH_OFF = 5f;
    private static final float DIRECTION_ARROW_SIZE = 8f;
    /** 超长走廊跳过填充三角化，避免耳切法 O(n²) 卡死主线程。 */
    private static final int MAX_FILL_VERTICES = 512;

    private RoadOverlayRenderer() {
    }

    public static void render(ImDrawList drawList, CanvasCamera camera, List<RoadOverlayEntry> entries) {
        if (drawList == null || camera == null || entries == null || entries.isEmpty()) {
            return;
        }
        for (RoadOverlayEntry entry : entries) {
            renderEntry(drawList, camera, entry);
        }
    }

    private static void renderEntry(ImDrawList drawList, CanvasCamera camera, RoadOverlayEntry entry) {
        List<Vec2d> corridor = entry.corridorPoints();
        if (corridor.size() < 3) {
            return;
        }
        RoadOverlayStyle style = RoadOverlayStyle.forState(entry.state());
        renderFill(drawList, camera, corridor, style.fillColor());
        renderPolylineOutline(drawList, camera, corridor, style, true);
        if (style.drawCenterline()) {
            renderCenterline(drawList, camera, entry.centerlinePoints(), style.outlineColor());
        }
        if (entry.state() == RoadOverlayState.PRIMARY) {
            renderDirectionArrow(drawList, camera, entry.centerlinePoints());
        }
    }

    private static void renderFill(
            ImDrawList drawList,
            CanvasCamera camera,
            List<Vec2d> points,
            int fillColor) {
        if ((fillColor >>> 24) == 0 || points.size() > MAX_FILL_VERTICES) {
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

    private static void renderPolylineOutline(
            ImDrawList drawList,
            CanvasCamera camera,
            List<Vec2d> points,
            RoadOverlayStyle style,
            boolean closed) {
        int count = points.size();
        int segments = closed ? count : count - 1;
        for (int i = 0; i < segments; i++) {
            Vec2d start = points.get(i);
            Vec2d end = points.get((i + 1) % count);
            if (style.dashed()) {
                drawDashedSegment(drawList, camera, start, end, style.outlineColor(), style.outlineThickness());
            } else {
                drawSolidSegment(drawList, camera, start, end, style.outlineColor(), style.outlineThickness());
            }
        }
    }

    private static void renderCenterline(
            ImDrawList drawList,
            CanvasCamera camera,
            List<Vec2d> centerline,
            int color) {
        if (centerline == null || centerline.size() < 2) {
            return;
        }
        for (int i = 0; i < centerline.size() - 1; i++) {
            drawDashedSegment(
                drawList,
                camera,
                centerline.get(i),
                centerline.get(i + 1),
                color,
                1.2f);
        }
    }

    private static void renderDirectionArrow(
            ImDrawList drawList,
            CanvasCamera camera,
            List<Vec2d> centerline) {
        if (centerline == null || centerline.size() < 2) {
            return;
        }
        Vec2d from = centerline.get(centerline.size() / 2 - 1);
        Vec2d to = centerline.get(centerline.size() / 2);
        if (from.distance(to) < 1e-6) {
            from = centerline.getFirst();
            to = centerline.get(1);
        }
        Vec2d dir = to.subtract(from);
        double length = dir.length();
        if (length < 1e-6) {
            return;
        }
        Vec2d unit = dir.multiply(1.0 / length);
        Vec2d mid = from.add(to).multiply(0.5);
        Vec2d tip = mid.add(unit.multiply(DIRECTION_ARROW_SIZE * 0.6));
        Vec2d left = tip.subtract(unit.multiply(DIRECTION_ARROW_SIZE * 0.5))
            .add(new Vec2d(-unit.y, unit.x).multiply(DIRECTION_ARROW_SIZE * 0.35));
        Vec2d right = tip.subtract(unit.multiply(DIRECTION_ARROW_SIZE * 0.5))
            .add(new Vec2d(unit.y, -unit.x).multiply(DIRECTION_ARROW_SIZE * 0.35));

        Vec2d screenTip = camera.worldToScreen(tip);
        Vec2d screenLeft = camera.worldToScreen(left);
        Vec2d screenRight = camera.worldToScreen(right);
        drawList.addTriangleFilled(
            (float) screenTip.x,
            (float) screenTip.y,
            (float) screenLeft.x,
            (float) screenLeft.y,
            (float) screenRight.x,
            (float) screenRight.y,
            PluginUiColors.ACCENT_BLUE);
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
}
