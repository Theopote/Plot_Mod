package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 路口几何（圆角/标线）区块：编辑 Tab 与路径 Tab 共用。 */
final class RoadIntersectionGeometrySection {

    private RoadIntersectionGeometrySection() {
    }

    static void render(RoadUiContext ctx, RoadNetwork network, RoadJunctionPanel junctionPanel) {
        if (ctx == null || network == null || junctionPanel == null) {
            return;
        }
        RoadNode junctionNode = RoadIntersectionPropertySupport.resolveJunctionNode(
            network, ctx.networkManager());
        if (junctionNode == null) {
            return;
        }
        ImGui.separator();
        RoadUiSections.section("plugin.road.junction_geometry_section");
        junctionPanel.renderForNode(junctionNode, false);
        ImGui.spacing();
    }
}
