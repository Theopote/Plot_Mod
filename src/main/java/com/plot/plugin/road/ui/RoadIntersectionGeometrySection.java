package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.RoadNetwork;

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
        // 标题由路径主面板 PluginSectionCard（##path_sec_junction_geometry）承担。
        junctionPanel.renderSelection(false);
    }
}
