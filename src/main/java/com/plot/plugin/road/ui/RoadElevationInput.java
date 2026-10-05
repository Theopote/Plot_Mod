package com.plot.plugin.road.ui;

import com.plot.plugin.road.vertical.RoadElevationBounds;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** Shared ImGui elevation field clamped to world block Y bounds. */
final class RoadElevationInput {

    private RoadElevationInput() {
    }

    static boolean renderDragFloat(
            String label,
            float[] value,
            RoadElevationBounds bounds,
            float speed,
            String format) {
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        boolean changed = ImGui.dragFloat(
            label,
            value,
            speed,
            bounds.minYFloat(),
            bounds.maxYFloat(),
            format);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr(
                "plugin.road.elevation_bounds_hint",
                (int) bounds.minY(),
                (int) bounds.maxY()));
        }
        return changed;
    }
}
