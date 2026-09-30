package com.plot.plugin.road.ui;

import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.crossing.CrossingType;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.graph.RoadGraphQueries;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.overlay.IntersectionOverlaySource;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 路径 Tab：交叉点列表（注册表 Crossing + 遗留拓扑节点）。 */
public final class RoadIntersectionListPanel {
    private final RoadUiContext ctx;

    public RoadIntersectionListPanel(RoadUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        List<RoadCrossing> crossings = new ArrayList<>(network.getCrossings().values());
        List<RoadNode> legacyJunctions = listLegacyJunctionNodes(network, crossings);
        if (crossings.isEmpty() && legacyJunctions.isEmpty()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.path.no_intersections"));
            return;
        }

        String selectedCrossingId = ctx.networkManager().getSelectedCrossingId();
        String selectedNodeId = ctx.networkManager().getSelectedNodeId();
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.path.intersection_list_hint"));

        crossings.sort(Comparator.comparing(c -> c.roadAId() + c.roadBId()));
        for (RoadCrossing crossing : crossings) {
            renderCrossingRow(network, crossing, crossing.id().equals(selectedCrossingId));
        }
        for (RoadNode node : legacyJunctions) {
            renderLegacyRow(network, node, node.getId().equals(selectedNodeId));
        }
    }

    private List<RoadNode> listLegacyJunctionNodes(RoadNetwork network, List<RoadCrossing> crossings) {
        List<RoadNode> junctions = new ArrayList<>();
        for (RoadNode node : network.getNodes().values()) {
            if (node == null || node.getDegree() < 2) {
                continue;
            }
            if (isCoveredByRegistryCrossing(node, crossings)) {
                continue;
            }
            if (node.isJunction() || RoadGraphQueries.isSimpleCrossing(node, network) || node.isGradeSeparated()) {
                junctions.add(node);
            }
        }
        junctions.sort(Comparator.comparingInt(RoadNode::getDegree).reversed());
        return junctions;
    }

    private static boolean isCoveredByRegistryCrossing(RoadNode node, List<RoadCrossing> crossings) {
        if (node.getPosition() == null) {
            return false;
        }
        for (RoadCrossing crossing : crossings) {
            if (crossing.position() != null
                    && RoadGeometryUtils.pointsNear(node.getPosition(), crossing.position(), 0.5)) {
                return true;
            }
        }
        return false;
    }

    private void renderCrossingRow(RoadNetwork network, RoadCrossing crossing, boolean selected) {
        String title = formatCrossingTitle(network, crossing);
        String status = formatCrossingStatus(network, crossing);
        String label = title + "  ·  " + status;
        if (ImGui.selectable(label + "##crossing_" + crossing.id(), selected)) {
            focusCrossing(network, crossing);
        }
    }

    private void renderLegacyRow(RoadNetwork network, RoadNode node, boolean selected) {
        RoadNetworkBuilder.JunctionType type =
            ctx.networkManager().getNetworkBuilder().classify(node);
        String title = formatJunctionTitle(network, node, type);
        String status = formatLegacyStatus(network, node);
        String label = title + "  ·  " + status;
        if (ImGui.selectable(label + "##junction_" + node.getId(), selected)) {
            focusLegacyJunction(network, node);
        }
        if (!RoadGraphQueries.isSimpleCrossing(node, network) && node.isGradeSeparated()) {
            ImGui.sameLine();
            ImGui.textColored(PluginUiColors.WARNING, "!");
        }
    }

    private static String formatCrossingStatus(RoadNetwork network, RoadCrossing crossing) {
        if (crossing.type() == CrossingType.AT_GRADE) {
            return PlotI18n.tr("plugin.road.path.intersection_status_at_grade");
        }
        String elevatedRoadId = crossing.elevatedRoadId();
        if (elevatedRoadId == null || elevatedRoadId.isBlank()) {
            return PlotI18n.tr("plugin.road.path.intersection_status_auto");
        }
        return PlotI18n.tr(
            "plugin.road.path.intersection_status_elevated",
            formatRoadLabel(network, elevatedRoadId));
    }

    private static String formatLegacyStatus(RoadNetwork network, RoadNode node) {
        if (!RoadGraphQueries.isSimpleCrossing(node, network)) {
            if (node.isGradeSeparated()) {
                return PlotI18n.tr("plugin.road.path.intersection_status_complex_grade");
            }
            return PlotI18n.tr("plugin.road.path.intersection_status_complex");
        }
        if (!node.isGradeSeparated()) {
            return PlotI18n.tr("plugin.road.path.intersection_status_at_grade");
        }
        String elevatedRoadId = node.getElevatedRoadId();
        if (elevatedRoadId == null || elevatedRoadId.isBlank()) {
            return PlotI18n.tr("plugin.road.path.intersection_status_auto");
        }
        return PlotI18n.tr(
            "plugin.road.path.intersection_status_elevated",
            formatRoadLabel(network, elevatedRoadId));
    }

    private void focusCrossing(RoadNetwork network, RoadCrossing crossing) {
        ctx.networkManager().focusIntersection(IntersectionOverlaySource.CROSSING, crossing.id());
        ctx.requestOverlayRefresh();
    }

    private void focusLegacyJunction(RoadNetwork network, RoadNode node) {
        ctx.networkManager().focusIntersection(IntersectionOverlaySource.LEGACY_NODE, node.getId());
        ctx.requestOverlayRefresh();
    }

    private String formatCrossingTitle(RoadNetwork network, RoadCrossing crossing) {
        String a = formatRoadLabel(network, crossing.roadAId());
        String b = formatRoadLabel(network, crossing.roadBId());
        return PlotI18n.tr("plugin.road.path.intersection_pair", a, b);
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
