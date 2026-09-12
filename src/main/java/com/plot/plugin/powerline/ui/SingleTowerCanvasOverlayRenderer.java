package com.plot.plugin.powerline.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.placement.SingleTowerOrientation;
import com.plot.plugin.powerline.placement.SingleTowerPlacementState;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;

/** 单塔放置模式的画布叠加层：落点标记 + 朝向箭头。 */
public final class SingleTowerCanvasOverlayRenderer {
    private static final float POLE_RADIUS = 8f;
    private static final float ARROW_LENGTH = 28f;
    private static final float ARROW_THICKNESS = 3f;

    private SingleTowerCanvasOverlayRenderer() {
    }

    public static void render(ImDrawList drawList, CanvasCamera camera, SingleTowerPlacementState state) {
        if (drawList == null || camera == null || state == null || !state.hoverValid() || state.planPoint() == null) {
            return;
        }

        Vec2d center = camera.worldToScreen(state.planPoint());
        int fill = PluginUiColors.ACCENT_BLUE;
        int outline = PluginUiColors.RING_DARK;
        drawList.addCircleFilled((float) center.x, (float) center.y, POLE_RADIUS, fill);
        drawList.addCircle((float) center.x, (float) center.y, POLE_RADIUS, outline, 24, 2f);

        Vec2d tangent = state.tangent();
        Vec2d arrowEnd = state.planPoint().add(tangent.multiply(ARROW_LENGTH / Math.max(camera.getZoom(), 0.05)));
        Vec2d screenEnd = camera.worldToScreen(arrowEnd);
        drawList.addLine(
            (float) center.x,
            (float) center.y,
            (float) screenEnd.x,
            (float) screenEnd.y,
            PluginUiColors.STATUS_OK,
            ARROW_THICKNESS);

        String hint = PlotI18n.tr(
            "plugin.powerline.single_tower.hud",
            state.designLabel(),
            state.buildBaseY(),
            PlotI18n.tr(SingleTowerOrientation.labelKey(state.rotationQuadrant())));
        Vec2d labelPos = camera.worldToScreen(state.planPoint().add(new Vec2d(12, -18)));
        drawList.addText((float) labelPos.x, (float) labelPos.y, PluginUiColors.HINT_GRAY, hint);
    }
}
