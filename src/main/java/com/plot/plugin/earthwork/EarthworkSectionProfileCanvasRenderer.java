package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.WorldProjectionSnapshot;
import com.plot.plugin.earthwork.solver.EarthworkSectionProfile;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.canvas.CanvasCamera;
import imgui.ImDrawList;

/**
 * 在画布上叠加当前地形剖面截线位置。
 */
public final class EarthworkSectionProfileCanvasRenderer {

    private static final int LINE_COLOR = 0xFF00D7FF; // amber ABGR
    private static final float LINE_THICKNESS = 2.5f;

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
        Vec2d startCanvas;
        Vec2d endCanvas;
        if (line.alongX()) {
            startCanvas = worldBlockToCanvas(line.primaryMin(), line.crossMid(), coordinates);
            endCanvas = worldBlockToCanvas(line.primaryMax(), line.crossMid(), coordinates);
        } else {
            startCanvas = worldBlockToCanvas(line.crossMid(), line.primaryMin(), coordinates);
            endCanvas = worldBlockToCanvas(line.crossMid(), line.primaryMax(), coordinates);
        }
        if (startCanvas == null || endCanvas == null) {
            return;
        }
        Vec2d screenStart = camera.worldToScreen(startCanvas);
        Vec2d screenEnd = camera.worldToScreen(endCanvas);
        drawList.addLine(
            (float) screenStart.x,
            (float) screenStart.y,
            (float) screenEnd.x,
            (float) screenEnd.y,
            LINE_COLOR,
            LINE_THICKNESS);
        float mx = (float) ((screenStart.x + screenEnd.x) * 0.5);
        float my = (float) ((screenStart.y + screenEnd.y) * 0.5);
        drawList.addCircleFilled(mx, my, 4f, LINE_COLOR);
        drawList.addCircle(mx, my, 4f, PluginUiColors.RING_DARK, 0, 1.2f);
    }

    private static Vec2d worldBlockToCanvas(int worldX, int worldZ, ICoordinateService coordinates) {
        if (coordinates == null) {
            return new Vec2d(worldX + 0.5, worldZ + 0.5);
        }
        try {
            WorldProjectionSnapshot projection = coordinates.captureProjection();
            if (projection != null && projection.isValid()) {
                return projection.toCanvas(new Vec2d(worldX + 0.5, worldZ + 0.5));
            }
        } catch (RuntimeException ignored) {
        }
        return new Vec2d(worldX + 0.5, worldZ + 0.5);
    }
}
