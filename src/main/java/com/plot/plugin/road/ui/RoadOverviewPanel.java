package com.plot.plugin.road.ui;

import com.plot.plugin.road.RoadNetworkEngineeringValidator;
import com.plot.plugin.road.RoadNetworkValidationReport;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.RoadNetworkOverviewRenderer;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

import java.util.LinkedHashSet;

/**
 * 道路概览 Tab：路网统计、缩略图点选；选中后提供进入编辑/纵断面的 workflow 捷径。
 */
public final class RoadOverviewPanel {
    private final RoadUiContext ctx;
    private long cachedHealthKey = Long.MIN_VALUE;
    private RoadNetworkValidationReport cachedHealthReport =
        new RoadNetworkValidationReport(java.util.List.of());

    public RoadOverviewPanel(RoadUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        RoadNetwork network = ctx.networkManager().getNetwork();

        RoadUiSections.section("plugin.road.section.network_health");
        ImGui.text(PlotI18n.tr("plugin.road.network_stats",
            network.getRoads().size(),
            network.getJunctionCount(),
            String.format("%.1f", network.getTotalLength())));

        renderHealthWarnings(network);

        RoadNetworkOverviewRenderer.render(
            network,
            ctx.networkManager().getNetworkBuilder(),
            ctx.networkManager().getConfig(),
            ctx.networkManager().getSelectedEdgeIds(),
            ctx.networkManager().getSelectedNodeId(),
            edgeId -> ctx.networkManager().handleEdgeSelect(edgeId, ImGui.getIO().getKeyCtrl()),
            ctx.networkManager()::handleNodeSelect
        );

        ImGui.spacing();
        if (hasSelection()) {
            RoadUiSections.section("plugin.road.section.selected");
            renderSelectionShortcuts(network);
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.pushStyleColor(ImGuiCol.Text, PluginUiColors.HINT_GRAY);
        ImGui.textWrapped(PlotI18n.tr("plugin.road.network_map_hint"));
        ImGui.popStyleColor();
    }

    private void renderHealthWarnings(RoadNetwork network) {
        if (network.getEdges().isEmpty()) {
            return;
        }
        long healthKey = ctx.networkManager().getNetworkRevision() * 31L
            + (ctx.networkManager().isAdoptIntersectionRepairPending() ? 1L : 0L);
        if (healthKey != cachedHealthKey) {
            cachedHealthReport = RoadNetworkEngineeringValidator.analyzeOverviewHealth(
                network,
                ctx.networkManager().isAdoptIntersectionRepairPending());
            cachedHealthKey = healthKey;
        }
        for (RoadNetworkValidationReport.Item item : cachedHealthReport.nonOkItems()) {
            int color = switch (item.level()) {
                case WARNING -> PluginUiColors.WARNING;
                case ERROR -> PluginUiColors.ERROR;
                default -> PluginUiColors.HINT_GRAY;
            };
            ImGui.textColored(color, "\u26a0 " + PlotI18n.tr(item.messageKey(), item.args()));
        }
    }

    private boolean hasSelection() {
        String selectedNodeId = ctx.networkManager().getSelectedNodeId();
        if (selectedNodeId != null && !selectedNodeId.isBlank()) {
            return true;
        }
        return !ctx.networkManager().getSelectedEdgeIds().isEmpty();
    }

    private void renderSelectionShortcuts(RoadNetwork network) {
        String selectedNodeId = ctx.networkManager().getSelectedNodeId();
        if (selectedNodeId != null && !selectedNodeId.isBlank()) {
            return;
        }
        if (ctx.networkManager().getSelectedEdgeIds().isEmpty()) {
            return;
        }

        LinkedHashSet<String> roadIds = ctx.networkManager().getSelectedRoadIds();
        if (roadIds.size() > 1) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.overview_multi_road_selected", roadIds.size()));
            if (ImGui.button(PlotI18n.tr("plugin.road.overview_edit_road") + "##overview_multi_edit")) {
                ctx.requestTab(RoadUiTab.EDIT);
            }
            return;
        }

        Road road = ctx.networkManager().getPrimarySelectedRoad();
        if (road == null) {
            return;
        }

        int segmentCount = road.getSegmentIds().size();
        double length = RoadEdgeListHelper.computeRoadLength(network, road);
        ImGui.text(PlotI18n.tr(
            "plugin.road.overview_road_summary",
            RoadEdgeListHelper.formatRoadLabel(network, road),
            segmentCount,
            length));

        float buttonWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;
        if (ImGui.button(PlotI18n.tr("plugin.road.overview_edit_road") + "##overview_edit", buttonWidth, 0)) {
            ctx.requestTab(RoadUiTab.EDIT);
        }
        ImGui.sameLine();
        String primaryEdgeId = ctx.networkManager().getPrimarySelectedEdgeId();
        if (ImGui.button(PlotI18n.tr("plugin.road.overview_view_profile") + "##overview_profile", buttonWidth, 0)) {
            ctx.requestViewProfile(primaryEdgeId);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.overview_view_profile"));
        }
    }
}
