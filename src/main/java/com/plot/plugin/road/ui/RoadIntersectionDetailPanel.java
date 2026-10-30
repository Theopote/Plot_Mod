package com.plot.plugin.road.ui;

import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadStackCrossing;
import com.plot.plugin.road.graph.RoadGraphQueries;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 交叉点属性编辑（路径 Tab 与属性侧栏主入口）。 */
public final class RoadIntersectionDetailPanel {
    private final RoadUiContext ctx;
    private final RoadGradeSeparationControls gradeSeparationControls;
    private final ComplexJunctionGuidePanel complexJunctionGuide;

    public RoadIntersectionDetailPanel(RoadUiContext ctx) {
        this.ctx = ctx;
        this.gradeSeparationControls = new RoadGradeSeparationControls(ctx);
        this.complexJunctionGuide = new ComplexJunctionGuidePanel(ctx);
    }

    /**
     * 渲染当前选中的交叉点（注册表 Crossing / Stack / 遗留拓扑节点）。
     *
     * @return 是否渲染了交叉点内容
     */
    public boolean renderFocusedSelection(
            RoadNetwork network,
            RoadGradeSeparationControls.Layout layout) {
        if (network == null || layout == null) {
            return false;
        }
        RoadCrossing crossing = ctx.networkManager().getSelectedCrossing();
        if (crossing != null) {
            renderCrossing(network, crossing, layout);
            return true;
        }
        RoadStackCrossing stack = ctx.networkManager().getSelectedStackCrossing();
        if (stack != null) {
            renderStackCrossing(network, stack, layout);
            return true;
        }
        String selectedNodeId = ctx.networkManager().getSelectedNodeId();
        if (selectedNodeId == null || selectedNodeId.isBlank()) {
            return false;
        }
        RoadNode node = network.getNode(selectedNodeId);
        if (node == null || node.getDegree() < 2) {
            return false;
        }
        if (!node.isJunction() && !RoadGraphQueries.isSimpleCrossing(node, network)) {
            return false;
        }
        renderLegacyJunction(network, node, layout);
        return true;
    }

    public void renderCrossing(RoadNetwork network, RoadCrossing crossing) {
        renderCrossing(network, crossing, RoadGradeSeparationControls.Layout.BLOCK);
    }

    public void renderCrossing(
            RoadNetwork network,
            RoadCrossing crossing,
            RoadGradeSeparationControls.Layout layout) {
        if (network == null || crossing == null || layout == null) {
            return;
        }
        if (layout != RoadGradeSeparationControls.Layout.INLINE) {
            ImGui.text(PlotI18n.tr("plugin.road.path.selected_intersection"));
            ImGui.textColored(
                PluginUiColors.ACCENT_BLUE,
                formatCrossingTitle(network, crossing));
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.path.crossing_registry_hint"));
        }

        gradeSeparationControls.renderCrossing(
            crossing,
            network,
            ctx.networkManager().getConfig(),
            layout);
    }

    public void renderStackCrossing(RoadNetwork network, RoadStackCrossing stack) {
        renderStackCrossing(network, stack, RoadGradeSeparationControls.Layout.BLOCK);
    }

    public void renderStackCrossing(
            RoadNetwork network,
            RoadStackCrossing stack,
            RoadGradeSeparationControls.Layout layout) {
        if (network == null || stack == null || layout == null) {
            return;
        }
        if (layout != RoadGradeSeparationControls.Layout.INLINE) {
            ImGui.text(PlotI18n.tr("plugin.road.path.selected_intersection"));
            ImGui.textColored(
                PluginUiColors.ACCENT_BLUE,
                PlotI18n.tr(
                    "plugin.road.path.stack_crossing_title",
                    formatRoadLabel(network, stack.roadId())));
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.path.stack_crossing_hint"));
        }

        gradeSeparationControls.renderStackCrossing(
            stack,
            network,
            ctx.networkManager().getConfig(),
            layout);
    }

    public void renderLegacyJunction(RoadNetwork network, RoadNode node) {
        renderLegacyJunction(network, node, RoadGradeSeparationControls.Layout.BLOCK);
    }

    public void renderLegacyJunction(
            RoadNetwork network,
            RoadNode node,
            RoadGradeSeparationControls.Layout layout) {
        if (network == null || node == null || layout == null) {
            return;
        }
        RoadNetworkBuilder.JunctionType type =
            ctx.networkManager().getNetworkBuilder().classify(node);
        if (layout != RoadGradeSeparationControls.Layout.INLINE) {
            ImGui.text(PlotI18n.tr("plugin.road.path.selected_intersection"));
            ImGui.textColored(PluginUiColors.ACCENT_BLUE, formatJunctionTitle(network, node, type));
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.junction_topology_type", RoadNetworkManager.junctionTypeLabel(type)));
        }

        if (RoadGraphQueries.isSimpleCrossing(node, network)) {
            gradeSeparationControls.renderLegacyJunction(
                node,
                network,
                ctx.networkManager().getConfig(),
                layout);
        } else {
            if (node.isGradeSeparated()) {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.WARNING,
                    PlotI18n.tr("plugin.road.path.complex_grade_separation_hint"));
            }
            if (layout != RoadGradeSeparationControls.Layout.INLINE) {
                complexJunctionGuide.render(network, node);
            }
        }
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
        java.util.List<String> roadIds = new java.util.ArrayList<>(network.getDistinctRoadIdsAtNode(node.getId()));
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
