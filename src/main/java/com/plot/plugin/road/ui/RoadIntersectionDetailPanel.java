package com.plot.plugin.road.ui;

import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.graph.RoadGraphQueries;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.ArrayList;
import java.util.List;

/** 路径 Tab：当前选中交叉点的简洁属性编辑（主入口）。 */
public final class RoadIntersectionDetailPanel {
    private final RoadUiContext ctx;
    private final RoadGradeSeparationControls gradeSeparationControls;
    private final ComplexJunctionGuidePanel complexJunctionGuide;

    public RoadIntersectionDetailPanel(RoadUiContext ctx) {
        this.ctx = ctx;
        this.gradeSeparationControls = new RoadGradeSeparationControls(ctx);
        this.complexJunctionGuide = new ComplexJunctionGuidePanel(ctx);
    }

    public void render(RoadNetwork network, RoadNode node) {
        if (network == null || node == null) {
            return;
        }
        RoadNetworkBuilder.JunctionType type =
            ctx.networkManager().getNetworkBuilder().classify(node);
        ImGui.text(PlotI18n.tr("plugin.road.path.selected_intersection"));
        ImGui.textColored(PluginUiColors.ACCENT_BLUE, formatJunctionTitle(network, node, type));
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.junction_topology_type", RoadNetworkManager.junctionTypeLabel(type)));

        if (RoadGraphQueries.isSimpleCrossing(node, network)) {
            gradeSeparationControls.render(
                node,
                network,
                ctx.networkManager().getConfig(),
                RoadGradeSeparationControls.Layout.BLOCK);
        } else {
            if (node.isGradeSeparated()) {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.WARNING,
                    PlotI18n.tr("plugin.road.path.complex_grade_separation_hint"));
            }
            complexJunctionGuide.render(network, node);
        }
    }

    private String formatJunctionTitle(
            RoadNetwork network,
            RoadNode node,
            RoadNetworkBuilder.JunctionType type) {
        List<String> roadIds = new ArrayList<>(network.getDistinctRoadIdsAtNode(node.getId()));
        if (roadIds.size() >= 2) {
            String a = formatRoadLabel(network, roadIds.get(0));
            String b = formatRoadLabel(network, roadIds.get(1));
            if (roadIds.size() == 2) {
                return PlotI18n.tr("plugin.road.path.intersection_pair", a, b);
            }
            return PlotI18n.tr(
                "plugin.road.path.intersection_multi",
                a,
                b,
                roadIds.size());
        }
        return RoadNetworkManager.junctionTypeLabel(type);
    }

    private static String formatRoadLabel(RoadNetwork network, String roadId) {
        Road road = network.getRoad(roadId);
        if (road == null) {
            return roadId;
        }
        return RoadEdgeListHelper.formatRoadLabel(network, road);
    }
}
