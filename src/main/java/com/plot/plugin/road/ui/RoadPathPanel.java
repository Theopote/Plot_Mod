package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.ui.editor.RoadFloatingEditorHost;
import com.plot.plugin.road.ui.editor.RoadFloatingEditorKind;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

/**
 * 路线工作区：道路在哪里、当前选择、路径列表与交叉关系；中心线编辑入口在列表下方。
 */
public final class RoadPathPanel {
    private final RoadUiContext ctx;
    private final RoadAdoptPanel adoptPanel;
    private final RoadEdgeListPanel edgeListPanel;
    private final RoadPathOverviewPanel overviewPanel;
    private final RoadIntersectionDetailPanel intersectionDetailPanel;
    private final RoadIntersectionListPanel intersectionListPanel;
    private final RoadJunctionPanel junctionPanel;
    private final RoadFloatingEditorHost floatingEditorHost;

    public RoadPathPanel(
            RoadUiContext ctx,
            RoadAdoptPanel adoptPanel,
            RoadEdgeListPanel edgeListPanel,
            RoadPathOverviewPanel overviewPanel,
            RoadIntersectionDetailPanel intersectionDetailPanel,
            RoadIntersectionListPanel intersectionListPanel,
            RoadJunctionPanel junctionPanel,
            RoadFloatingEditorHost floatingEditorHost) {
        this.ctx = ctx;
        this.adoptPanel = adoptPanel;
        this.edgeListPanel = edgeListPanel;
        this.overviewPanel = overviewPanel;
        this.intersectionDetailPanel = intersectionDetailPanel;
        this.intersectionListPanel = intersectionListPanel;
        this.junctionPanel = junctionPanel;
        this.floatingEditorHost = floatingEditorHost;
    }

    public void render() {
        RoadPathHeader.render(ctx, adoptPanel);

        ImGui.separator();
        renderRoadList();
        renderCenterlineEntry();

        RoadNetwork network = ctx.networkManager().getNetwork();
        if (!network.getEdges().isEmpty()) {
            ImGui.separator();
            RoadUiSections.section("plugin.road.path.intersections");
            renderSelectedIntersection(network);
            intersectionListPanel.render();
        }
    }

    private void renderCenterlineEntry() {
        RoadSelectionHeader.Mode mode = RoadSelectionHeader.resolveMode(ctx);
        if (mode == RoadSelectionHeader.Mode.NONE) {
            return;
        }
        Road road = ctx.networkManager().getPrimarySelectedRoad();
        if (road == null) {
            return;
        }
        ImGui.spacing();
        RoadUiSections.section("plugin.road.route.centerline");
        int selectedCount = ctx.networkManager().getSelectedRoadIds().size();
        if (mode == RoadSelectionHeader.Mode.MULTI && selectedCount > 1) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.edit.tools.batch_centerline.multi_hint", selectedCount));
        } else {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.route.centerline_hint"));
        }
        boolean active = floatingEditorHost != null
            && floatingEditorHost.isOpen(RoadFloatingEditorKind.CENTERLINE);
        if (active) {
            ImGui.pushStyleColor(ImGuiCol.Button, PluginUiColors.ACCENT_BLUE);
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.route.edit_centerline") + "##path_edit_centerline")) {
            ctx.requestEditCenterline(road.getId());
        }
        if (active) {
            ImGui.popStyleColor();
        }
    }

    private void renderSelectedIntersection(RoadNetwork network) {
        if (!RoadIntersectionPropertySupport.hasIntersectionSelection(ctx.networkManager())) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.path.intersection_select_hint"));
            ImGui.spacing();
            return;
        }
        intersectionDetailPanel.renderFocusedSelection(
            network, RoadGradeSeparationControls.Layout.BLOCK);
        RoadIntersectionGeometrySection.render(ctx, network, junctionPanel);
        ImGui.spacing();
    }

    private void renderRoadList() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        if (network.getEdges().isEmpty()) {
            RoadUiSections.section("plugin.road.path.road_list");
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.path.empty_hint"));
            return;
        }

        overviewPanel.renderNetworkMap(network);
        ImGui.spacing();

        RoadUiSections.section("plugin.road.path.road_list");
        edgeListPanel.renderPathList("path_edge_list");
    }

}
