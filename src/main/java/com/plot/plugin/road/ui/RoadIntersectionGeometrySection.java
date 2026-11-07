package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.RoadNetwork;
import imgui.ImGui;

/** 路口几何（圆角/标线）区块：编辑 Tab 与路径 Tab 共用。 */
final class RoadIntersectionGeometrySection {

    private RoadIntersectionGeometrySection() {
    }

    static void render(RoadUiContext ctx, RoadNetwork network, RoadJunctionPanel junctionPanel) {
        if (ctx == null || network == null || junctionPanel == null) {
            return;
        }
        if (!RoadIntersectionPropertySupport.hasIntersectionSelection(ctx.networkManager())) {
            return;
        }
        ImGui.separator();
        RoadUiSections.section("plugin.road.junction_geometry_section");
        junctionPanel.renderSelection(false);
        ImGui.spacing();
    }
}
