package com.plot.plugin.pattern;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.model.PatternProject;
import com.plot.ui.canvas.CanvasCamera;
import imgui.ImDrawList;

import java.util.List;

/**
 * 在画布上绘制选中铺装区域的孔洞轮廓。
 */
public final class PatternFootprintGeometryCanvasRenderer {
    private static final int HOLE_COLOR = 0xFF00A5FF;
    private static final float HOLE_THICKNESS = 2.5f;

    private PatternFootprintGeometryCanvasRenderer() {
    }

    public static void render(
            ImDrawList drawList,
            CanvasCamera camera,
            PatternProject project,
            String selectedFootprintId) {
        if (drawList == null || camera == null || project == null || selectedFootprintId == null) {
            return;
        }
        PatternFootprint footprint = project.getFootprint(selectedFootprintId);
        if (footprint == null) {
            return;
        }
        renderHoleRings(drawList, camera, footprint.getHoles());
    }

    private static void renderHoleRings(
            ImDrawList drawList,
            CanvasCamera camera,
            List<List<Vec2d>> holes) {
        if (holes == null || holes.isEmpty()) {
            return;
        }
        for (List<Vec2d> hole : holes) {
            drawRing(drawList, camera, hole);
        }
    }

    private static void drawRing(ImDrawList drawList, CanvasCamera camera, List<Vec2d> points) {
        if (points == null || points.size() < 2) {
            return;
        }
        int count = points.size();
        for (int i = 0; i < count; i++) {
            Vec2d start = points.get(i);
            Vec2d end = points.get((i + 1) % count);
            if (start == null || end == null) {
                continue;
            }
            Vec2d screenStart = camera.worldToScreen(start);
            Vec2d screenEnd = camera.worldToScreen(end);
            drawList.addLine(
                (float) screenStart.x,
                (float) screenStart.y,
                (float) screenEnd.x,
                (float) screenEnd.y,
                HOLE_COLOR,
                HOLE_THICKNESS);
        }
    }
}
