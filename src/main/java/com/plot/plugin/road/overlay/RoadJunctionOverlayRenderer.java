package com.plot.plugin.road.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.ui.canvas.CanvasCamera;
import imgui.ImDrawList;

import java.util.List;

/** 交叉点画布标记与选中路缘圆弧预览渲染。 */
public final class RoadJunctionOverlayRenderer {
    private static final float MARKER_RADIUS = 6f;
    private static final int COLOR_FILLET_BORDER = 0xFF48C9A0;
    private static final int COLOR_SHARP_REF = 0x5548C9A0;

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
        if (entry.selected()) {
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
        List<Vec2d> sharp = entry.sharpOutline();
        if (sharp != null && sharp.size() >= 2) {
            for (int i = 0; i < sharp.size(); i++) {
                Vec2d a = sharp.get(i);
                Vec2d b = sharp.get((i + 1) % sharp.size());
                Vec2d sa = camera.worldToScreen(a);
                Vec2d sb = camera.worldToScreen(b);
                drawList.addLine(
                    (float) sa.x, (float) sa.y,
                    (float) sb.x, (float) sb.y,
                    COLOR_SHARP_REF, 1.0f);
            }
        }
        // 只描街角路缘圆弧，不闭合绕交叉点的整圈铺装环
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
