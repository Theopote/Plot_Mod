package com.plot.plugin.road.ui.design;

import com.plot.plugin.road.ui.StationFacilityEditor;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** Station facilities along the road. */
public final class RoadFacilitySection {
    private final StationFacilityEditor stationFacilityEditor = new StationFacilityEditor();

    public void render(RoadDesignSectionContext ctx) {
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.design_stack.station_facilities"))) {
            return;
        }
        stationFacilityEditor.render(
            ctx.network, ctx.road, ctx.chainageDisplay(), ctx.ui::pushRoadEditHistory);
    }
}
