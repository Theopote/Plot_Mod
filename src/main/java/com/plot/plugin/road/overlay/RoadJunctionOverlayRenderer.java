package com.plot.plugin.road.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.ui.canvas.CanvasCamera;
import imgui.ImDrawList;

import java.util.List;

/** 交叉点画布标记与选中路缘圆弧预览渲染。 */
public final class RoadJunctionOverlayRenderer {
    private static final float MARKER_RADIUS = 6f;
    private static final int COLOR_FILLET_BORDER = 0xFF48C9A0;

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
        // 圆角预览只画路缘圆弧；不画锐角参考多边形（淡色折线会显得杂乱）。
        if (!entry.filletArcs().isEmpty()) {
            renderFilletPreview(drawList, camera, entry);
        }
        Vec2d screen = camera.worldToScreen(entry.position());
        float x = (float) screen.x;
        float y = (float) screen.y;
        RoadJunctionOverlayKind kind = entry.selected()
            ? RoadJunctionOverlayKind.SELECTED
            : entry.kind();
        int color = kind.markerColor();
        float radius = entry.selected() ? MARKER_RADIUS + 2f : MARKER_RADIUS;
        switch (kind) {
            case GRADE_SEPARATED -> drawDiamond(drawList, x, y, radius, color);
            case COMPLEX, WARNING -> drawSquare(drawList, x, y, radius, color);
            case AT_GRADE -> drawList.addCircle(x, y, radius, color, 16, 2f);
            default -> drawList.addCircleFilled(x, y, radius, color);
        }
        if (entry.selected()) {
            drawList.addCircle(x, y, radius + 3f, 0xFFFFFFFF, 16, 1.5f);
        }
    }

    private static void renderFilletPreview(
            ImDrawList drawList,
            CanvasCamera camera,
            RoadJunctionOverlayEntry entry) {
        for (List<Vec2d> arc : entry.filletArcs()) {
            if (arc == null || arc.size() < 2) {
                continue;
            }
            for (int i = 0; i < arc.size() - 1; i++) {
                Vec2d sa = camera.worldToScreen(arc.get(i));
                Vec2d sb = camera.worldToScreen(arc.get(i + 1));
                drawList.addLine(
                    (float) sa.x, (float) sa.y,
                    (float) sb.x, (float) sb.y,
                    COLOR_FILLET_BORDER, 2.0f);
            }
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
