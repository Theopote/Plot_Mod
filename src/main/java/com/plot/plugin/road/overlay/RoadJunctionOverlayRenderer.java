package com.plot.plugin.road.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.ui.canvas.CanvasCamera;
import imgui.ImDrawList;

import java.util.List;

/** 交叉点画布标记渲染。 */
public final class RoadJunctionOverlayRenderer {
    private static final float MARKER_RADIUS = 6f;

    private RoadJunctionOverlayRenderer() {
    }

    public static void render(
            ImDrawList drawList,
            CanvasCamera camera,
            List<RoadJunctionOverlayEntry> entries) {
        if (drawList == null || camera == null || entries == null) {
            return;
        }
        for (RoadJunctionOverlayEntry entry : entries) {
            renderEntry(drawList, camera, entry);
        }
    }

    private static void renderEntry(
            ImDrawList drawList,
            CanvasCamera camera,
            RoadJunctionOverlayEntry entry) {
        Vec2d screen = camera.worldToScreen(entry.position());
        float x = (float) screen.x;
        float y = (float) screen.y;
        int color = entry.kind().markerColor();
        float radius = entry.selected() ? MARKER_RADIUS + 2f : MARKER_RADIUS;
        switch (entry.kind()) {
            case GRADE_SEPARATED -> drawDiamond(drawList, x, y, radius, color);
            case COMPLEX, WARNING -> drawSquare(drawList, x, y, radius, color);
            default -> drawList.addCircleFilled(x, y, radius, color);
        }
        if (entry.selected()) {
            drawList.addCircle(x, y, radius + 3f, 0xFFFFFFFF, 16, 1.5f);
        }
    }

    private static void drawDiamond(ImDrawList drawList, float x, float y, float radius, int color) {
        drawList.addQuadFilled(
            x, y - radius,
            x + radius, y,
            x, y + radius,
            x - radius, y,
            color);
    }

    private static void drawSquare(ImDrawList drawList, float x, float y, float radius, int color) {
        drawList.addRectFilled(x - radius, y - radius, x + radius, y + radius, color);
    }
}
