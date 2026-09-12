package com.plot.plugin.powerline.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.model.PlacedSingleTower;
import com.plot.plugin.powerline.placement.SingleTowerOrientation;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.canvas.CanvasCamera;
import imgui.ImDrawList;

import java.util.List;

/** 已落地单塔的画布标记（非 Ghost 预览）。 */
public final class PlacedSingleTowerCanvasRenderer {
    private static final float MARKER_RADIUS = 5f;

    private PlacedSingleTowerCanvasRenderer() {
    }

    public static void render(
            ImDrawList drawList,
            CanvasCamera camera,
            List<PlacedSingleTower> placements,
            String selectedTowerId) {
        if (drawList == null || camera == null || placements == null || placements.isEmpty()) {
            return;
        }
        for (PlacedSingleTower placement : placements) {
            boolean selected = placement.getId().equals(selectedTowerId);
            renderOne(drawList, camera, placement, selected);
        }
    }

    private static void renderOne(
            ImDrawList drawList,
            CanvasCamera camera,
            PlacedSingleTower placement,
            boolean selected) {
        Vec2d center = camera.worldToScreen(placement.getPlanPoint());
        float radius = selected ? MARKER_RADIUS + 3f : MARKER_RADIUS;
        int fill = selected ? PluginUiColors.ACCENT_BLUE : PluginUiColors.STATUS_OK;
        drawList.addCircleFilled((float) center.x, (float) center.y, radius, fill);
        drawList.addCircle((float) center.x, (float) center.y, radius, PluginUiColors.RING_DARK, 16, selected ? 2.5f : 1.5f);

        Vec2d tangent = SingleTowerOrientation.tangentForQuadrant(placement.getRotationQuadrant());
        Vec2d arrowEnd = placement.getPlanPoint().add(tangent.multiply(16.0 / Math.max(camera.getZoom(), 0.05)));
        Vec2d screenEnd = camera.worldToScreen(arrowEnd);
        drawList.addLine(
            (float) center.x,
            (float) center.y,
            (float) screenEnd.x,
            (float) screenEnd.y,
            PluginUiColors.LEGEND,
            2f);
    }
}
