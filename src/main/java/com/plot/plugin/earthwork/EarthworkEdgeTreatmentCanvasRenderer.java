package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EdgeTreatment;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.ZoneEdgeSettings;
import com.plot.ui.canvas.CanvasCamera;
import imgui.ImDrawList;

import java.util.List;

/**
 * 在画布上按 {@link EdgeTreatment} 为分区边界边着色。
 */
public final class EarthworkEdgeTreatmentCanvasRenderer {

    private static final float EDGE_THICKNESS = 3.0f;
    private static final float SELECTED_ZONE_THICKNESS = 4.5f;

    private EarthworkEdgeTreatmentCanvasRenderer() {
    }

    public static void render(
            ImDrawList drawList,
            CanvasCamera camera,
            EarthworkProject project,
            String selectedZoneId) {
        if (drawList == null || camera == null || project == null) {
            return;
        }
        for (GradingZone zone : project.getActiveSite().getGradingZones().values()) {
            if (zone == null || !zone.isEnabled()) {
                continue;
            }
            renderZone(drawList, camera, zone, zone.getId().equals(selectedZoneId));
        }
    }

    private static void renderZone(
            ImDrawList drawList,
            CanvasCamera camera,
            GradingZone zone,
            boolean selected) {
        List<Vec2d> points = zone.getOuterPoints();
        if (points.size() < 2) {
            return;
        }
        ZoneEdgeSettings settings = zone.getEdgeSettings();
        float thickness = selected ? SELECTED_ZONE_THICKNESS : EDGE_THICKNESS;
        int count = points.size();
        for (int edgeIndex = 0; edgeIndex < count; edgeIndex++) {
            Vec2d start = points.get(edgeIndex);
            Vec2d end = points.get((edgeIndex + 1) % count);
            if (start == null || end == null) {
                continue;
            }
            EdgeTreatment treatment = settings.resolveTreatment(edgeIndex);
            int color = EarthworkEdgeTreatmentColors.colorFor(treatment);
            drawEdge(drawList, camera, start, end, color, thickness);
            if (selected) {
                drawEdgeMidpointMarker(drawList, camera, start, end, color);
            }
        }
    }

    private static void drawEdge(
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

    private static void drawEdgeMidpointMarker(
            ImDrawList drawList,
            CanvasCamera camera,
            Vec2d start,
            Vec2d end,
            int color) {
        Vec2d midpoint = start.lerp(end, 0.5);
        Vec2d screen = camera.worldToScreen(midpoint);
        float radius = 4.0f;
        drawList.addCircleFilled((float) screen.x, (float) screen.y, radius, color);
        drawList.addCircle((float) screen.x, (float) screen.y, radius + 1.0f, EarthworkEdgeTreatmentColors.SELECTED_EDGE, 12, 1.5f);
    }
}
