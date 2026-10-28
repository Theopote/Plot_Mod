package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.WorldProjectionSnapshot;
import com.plot.plugin.earthwork.solver.EarthworkSectionProfile;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.canvas.CanvasCamera;
import imgui.ImDrawList;

/**
 * 在画布上叠加剖面截线：建筑平面图式剖切标头 + 指向看向侧的三角。
 */
public final class EarthworkSectionProfileCanvasRenderer {

    private static final int LINE_COLOR = 0xFF00D7FF; // amber ABGR
    private static final float LINE_THICKNESS = 2.0f;
    private static final float HEAD_THICKNESS = 3.5f;
    private static final float HEAD_LENGTH = 14f;
    private static final float TRI_SIZE = 9f;

    private EarthworkSectionProfileCanvasRenderer() {
    }

    public static void render(
            ImDrawList drawList,
            CanvasCamera camera,
            EarthworkSectionProfile profile,
            ICoordinateService coordinates) {
        if (drawList == null || camera == null || profile == null || profile.isEmpty()) {
            return;
        }
        EarthworkSectionProfile.SectionCutLine line = profile.sectionCutLine();
        Vec2d startCanvas = worldToCanvas(line.startWorldX(), line.startWorldZ(), coordinates);
        Vec2d endCanvas = worldToCanvas(line.endWorldX(), line.endWorldZ(), coordinates);
        if (startCanvas == null || endCanvas == null) {
            return;
        }
        Vec2d screenStart = camera.worldToScreen(startCanvas);
        Vec2d screenEnd = camera.worldToScreen(endCanvas);
        float x0 = (float) screenStart.x;
        float y0 = (float) screenStart.y;
        float x1 = (float) screenEnd.x;
        float y1 = (float) screenEnd.y;
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-3f) {
            return;
        }
        float ux = dx / len;
        float uy = dy / len;

        // 看向法向：世界 → 画布差分再投到屏幕，避免仅用屏幕旋转近似
        Vec2d midCanvas = new Vec2d(
            (line.startWorldX() + line.endWorldX()) * 0.5,
            (line.startWorldZ() + line.endWorldZ()) * 0.5);
        Vec2d viewTipWorld = new Vec2d(
            midCanvas.x + line.viewNx(),
            midCanvas.y + line.viewNz());
        Vec2d midCanvasPos = worldToCanvas(midCanvas.x, midCanvas.y, coordinates);
        Vec2d tipCanvas = worldToCanvas(viewTipWorld.x, viewTipWorld.y, coordinates);
        float vnx;
        float vny;
        if (midCanvasPos != null && tipCanvas != null) {
            Vec2d midScreen = camera.worldToScreen(midCanvasPos);
            Vec2d tipScreen = camera.worldToScreen(tipCanvas);
            float vx = (float) (tipScreen.x - midScreen.x);
            float vy = (float) (tipScreen.y - midScreen.y);
            float vlen = (float) Math.hypot(vx, vy);
            if (vlen > 1e-3f) {
                vnx = vx / vlen;
                vny = vy / vlen;
            } else {
                // 屏幕上截线左侧
                vnx = -uy;
                vny = ux;
            }
        } else {
            vnx = -uy;
            vny = ux;
        }

        drawList.addLine(x0, y0, x1, y1, LINE_COLOR, LINE_THICKNESS);
        drawSectionHead(drawList, x0, y0, ux, uy, vnx, vny);
        drawSectionHead(drawList, x1, y1, -ux, -uy, vnx, vny);
        drawViewTriangle(drawList, x0 + ux * HEAD_LENGTH * 0.55f, y0 + uy * HEAD_LENGTH * 0.55f, vnx, vny);
        drawViewTriangle(drawList, x1 - ux * HEAD_LENGTH * 0.55f, y1 - uy * HEAD_LENGTH * 0.55f, vnx, vny);
    }

    /** 端头短粗标：沿截线向内一段粗线，再向看向侧伸出一小段。 */
    private static void drawSectionHead(
            ImDrawList drawList,
            float ex,
            float ey,
            float inwardUx,
            float inwardUy,
            float vnx,
            float vny) {
        float hx = ex + inwardUx * HEAD_LENGTH;
        float hy = ey + inwardUy * HEAD_LENGTH;
        drawList.addLine(ex, ey, hx, hy, LINE_COLOR, HEAD_THICKNESS);
        drawList.addLine(
            ex,
            ey,
            ex + vnx * (HEAD_LENGTH * 0.55f),
            ey + vny * (HEAD_LENGTH * 0.55f),
            LINE_COLOR,
            HEAD_THICKNESS);
        drawList.addCircleFilled(ex, ey, 2.2f, LINE_COLOR);
        drawList.addCircle(ex, ey, 2.2f, PluginUiColors.RING_DARK, 0, 1.0f);
    }

    private static void drawViewTriangle(
            ImDrawList drawList,
            float baseX,
            float baseY,
            float vnx,
            float vny) {
        float tipX = baseX + vnx * TRI_SIZE;
        float tipY = baseY + vny * TRI_SIZE;
        float px = -vny;
        float py = vnx;
        float half = TRI_SIZE * 0.45f;
        drawList.addTriangleFilled(
            tipX,
            tipY,
            baseX + px * half,
            baseY + py * half,
            baseX - px * half,
            baseY - py * half,
            LINE_COLOR);
    }

    private static Vec2d worldToCanvas(double worldX, double worldZ, ICoordinateService coordinates) {
        if (coordinates == null) {
            return new Vec2d(worldX, worldZ);
        }
        try {
            WorldProjectionSnapshot projection = coordinates.captureProjection();
            if (projection != null && projection.isValid()) {
                return projection.toCanvas(new Vec2d(worldX, worldZ));
            }
        } catch (RuntimeException ignored) {
        }
        return new Vec2d(worldX, worldZ);
    }
}
