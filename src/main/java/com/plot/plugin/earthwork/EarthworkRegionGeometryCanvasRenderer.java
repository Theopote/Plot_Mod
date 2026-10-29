package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.RegionGeometry;
import com.plot.plugin.earthwork.model.Breakline;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.ExclusionZone;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.RetainingEdge;
import com.plot.ui.canvas.CanvasCamera;
import imgui.ImDrawList;

import java.util.List;

/**
 * 在画布上绘制分区孔洞、排除区轮廓、硬边界折线与挡土墙轴线。
 */
public final class EarthworkRegionGeometryCanvasRenderer {

    private static final int HOLE_COLOR = 0xFF00A5FF; // orange (ABGR)
    private static final int EXCLUSION_OUTER_COLOR = 0xFF5050FF; // red-ish
    private static final int EXCLUSION_HOLE_COLOR = 0xFF8080FF;
    private static final int BREAKLINE_COLOR = 0xFF20D0FF; // gold / amber
    private static final int RETAINING_COLOR = 0xFFC080FF; // soft violet
    private static final float HOLE_THICKNESS = 2.5f;
    private static final float EXCLUSION_THICKNESS = 2.0f;
    private static final float BREAKLINE_THICKNESS = 2.5f;
    private static final float RETAINING_THICKNESS = 2.5f;
    private static final float ENDPOINT_RADIUS = 3.5f;

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
        EarthworkSite site = project.getActiveSite();
        if (site == null) {
            return;
        }
        for (ExclusionZone exclusion : site.getExclusionZones()) {
            if (exclusion == null || exclusion.getGeometry().isEmpty()) {
                continue;
            }
            renderGeometry(drawList, camera, exclusion.getGeometry(), EXCLUSION_OUTER_COLOR, EXCLUSION_THICKNESS);
            renderHoleRings(drawList, camera, exclusion.getGeometry().holes(), EXCLUSION_HOLE_COLOR);
        }
        for (Breakline breakline : site.getBreaklines()) {
            if (breakline == null) {
                continue;
            }
            drawOpenPolyline(
                drawList, camera, breakline.getPoints(), BREAKLINE_COLOR, BREAKLINE_THICKNESS, true);
        }
        for (RetainingEdge edge : site.getRetainingEdges()) {
            if (edge == null) {
                continue;
            }
            drawOpenPolyline(
                drawList, camera, edge.getPolyline(), RETAINING_COLOR, RETAINING_THICKNESS, true);
        }
        for (GradingZone zone : site.getGradingZones().values()) {
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
        drawPolylineSegments(drawList, camera, points, color, thickness, true);
    }

    private static void drawOpenPolyline(
            ImDrawList drawList,
            CanvasCamera camera,
            List<Vec2d> points,
            int color,
            float thickness,
            boolean markEndpoints) {
        drawPolylineSegments(drawList, camera, points, color, thickness, false);
        if (!markEndpoints || points == null || points.size() < 2) {
            return;
        }
        Vec2d first = points.getFirst();
        Vec2d last = points.getLast();
        if (first != null) {
            Vec2d screen = camera.worldToScreen(first);
            drawList.addCircleFilled((float) screen.x, (float) screen.y, ENDPOINT_RADIUS, color);
        }
        if (last != null && last != first) {
            Vec2d screen = camera.worldToScreen(last);
            drawList.addCircleFilled((float) screen.x, (float) screen.y, ENDPOINT_RADIUS, color);
        }
    }

    private static void drawPolylineSegments(
            ImDrawList drawList,
            CanvasCamera camera,
            List<Vec2d> points,
            int color,
            float thickness,
            boolean closed) {
        if (points == null || points.size() < 2) {
            return;
        }
        int count = points.size();
        int segmentCount = closed ? count : count - 1;
        for (int i = 0; i < segmentCount; i++) {
            Vec2d start = points.get(i);
            Vec2d end = points.get(closed ? (i + 1) % count : i + 1);
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
