package com.plot.plugin.road.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadJunctionGeometry;
import com.plot.plugin.road.RoadNetworkOverviewRenderer;
import com.plot.ui.canvas.CanvasCamera;
import imgui.ImDrawList;

import java.util.List;

/** 交叉点画布标记与选中圆角轮廓渲染。 */
public final class RoadJunctionOverlayRenderer {
    private static final float MARKER_RADIUS = 6f;
    private static final int COLOR_FILLET_FILL = 0x3348C9A0;
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
        List<Vec2d> fillet = entry.filletOutline();
        if (fillet == null || fillet.size() < 2) {
            return;
        }
        float[] xs = new float[fillet.size()];
        float[] ys = new float[fillet.size()];
        for (int i = 0; i < fillet.size(); i++) {
            Vec2d screen = camera.worldToScreen(fillet.get(i));
            xs[i] = (float) screen.x;
            ys[i] = (float) screen.y;
        }
        if (RoadJunctionGeometry.isSimplePolygon(fillet)
                && RoadNetworkOverviewRenderer.isStrictlyConvex(fillet)
                && fillet.size() <= 64) {
            // ImGui convex fill expects ImVec2[]; fall back to polyline ring when unavailable.
            for (int i = 0; i < fillet.size(); i++) {
                int j = (i + 1) % fillet.size();
                drawList.addLine(xs[i], ys[i], xs[j], ys[j], COLOR_FILLET_FILL, 3.0f);
            }
        } else {
            for (int i = 0; i < fillet.size(); i++) {
                int j = (i + 1) % fillet.size();
                drawList.addLine(xs[i], ys[i], xs[j], ys[j], COLOR_FILLET_FILL, 3.0f);
            }
        }
        for (int i = 0; i < fillet.size(); i++) {
            int j = (i + 1) % fillet.size();
            drawList.addLine(xs[i], ys[i], xs[j], ys[j], COLOR_FILLET_BORDER, 1.5f);
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
