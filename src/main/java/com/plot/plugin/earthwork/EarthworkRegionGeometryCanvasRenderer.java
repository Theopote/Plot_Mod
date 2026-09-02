package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.RegionGeometry;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.ExclusionZone;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.ui.canvas.CanvasCamera;
import imgui.ImDrawList;

import java.util.List;

/**
 * 在画布上绘制分区孔洞与排除区轮廓。
 */
public final class EarthworkRegionGeometryCanvasRenderer {

    private static final int HOLE_COLOR = 0xFF00A5FF; // orange (ABGR)
    private static final int EXCLUSION_OUTER_COLOR = 0xFF5050FF; // red-ish
    private static final int EXCLUSION_HOLE_COLOR = 0xFF8080FF;
    private static final float HOLE_THICKNESS = 2.5f;
    private static final float EXCLUSION_THICKNESS = 2.0f;

    private EarthworkRegionGeometryCanvasRenderer() {
    }

    public static void render(
            ImDrawList drawList,
            CanvasCamera camera,
            EarthworkProject project,
            String selectedZoneId) {
        if (drawList == null || camera == null || project == null) {
            return;
        }
        for (ExclusionZone exclusion : project.getActiveSite().getExclusionZones()) {
            if (exclusion == null || exclusion.getGeometry().isEmpty()) {
                continue;
            }
            renderGeometry(drawList, camera, exclusion.getGeometry(), EXCLUSION_OUTER_COLOR, EXCLUSION_THICKNESS);
            renderHoleRings(drawList, camera, exclusion.getGeometry().holes(), EXCLUSION_HOLE_COLOR);
        }
        for (GradingZone zone : project.getActiveSite().getGradingZones().values()) {
            if (zone == null || !zone.getId().equals(selectedZoneId)) {
                continue;
            }
            renderHoleRings(drawList, camera, zone.getHoles(), HOLE_COLOR);
        }
    }

    private static void renderGeometry(
            ImDrawList drawList,
            CanvasCamera camera,
            RegionGeometry geometry,
            int color,
            float thickness) {
        drawRing(drawList, camera, geometry.outerRing(), color, thickness);
    }

    private static void renderHoleRings(
            ImDrawList drawList,
            CanvasCamera camera,
            List<List<Vec2d>> holes,
            int color) {
        if (holes == null) {
            return;
        }
        for (List<Vec2d> hole : holes) {
            drawRing(drawList, camera, hole, color, HOLE_THICKNESS);
        }
    }

    private static void drawRing(
            ImDrawList drawList,
            CanvasCamera camera,
            List<Vec2d> points,
            int color,
            float thickness) {
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
                color,
                thickness);
        }
    }
}
