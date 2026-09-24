package com.plot.plugin.road.ui;

import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTreeNodeFlags;

import java.util.ArrayList;
import java.util.List;

/**
 * 路线 Tab：选择道路、拾取路径、预设、横断面与基础参数（0 / 1 / N 选择态）。
 */
public final class RoadRoutePanel {
    private final RoadUiContext ctx;
    private final RoadAdoptPanel adoptPanel;
    private final RoadDefaultParamsPanel defaultParamsPanel;
    private final RoadEdgeListPanel edgeListPanel;
    private final RoadOverviewPanel overviewPanel;

    public RoadRoutePanel(
            RoadUiContext ctx,
            RoadAdoptPanel adoptPanel,
            RoadDefaultParamsPanel defaultParamsPanel,
            RoadEdgeListPanel edgeListPanel,
            RoadOverviewPanel overviewPanel) {
        this.ctx = ctx;
        this.adoptPanel = adoptPanel;
        this.defaultParamsPanel = defaultParamsPanel;
        this.edgeListPanel = edgeListPanel;
        this.overviewPanel = overviewPanel;
    }

    public void render() {
        RoadSelectionHeader.render(ctx);

        ImGui.separator();
        adoptPanel.render();

        if (!ctx.networkManager().getNetwork().getEdges().isEmpty()) {
            ImGui.separator();
            overviewPanel.renderCompactNetworkSummary();
            overviewPanel.renderHealthWarnings(ctx.networkManager().getNetwork());
        }

        ImGui.separator();
        renderWorkspace();

        ImGui.separator();
        renderAllRoadsCollapsible();
    }

    private void renderWorkspace() {
        RoadSelectionHeader.Mode mode = RoadSelectionHeader.resolveMode(ctx);
        switch (mode) {
            case NONE -> renderNoSelectionWorkspace();
            case SINGLE -> renderSingleRoadWorkspace();
            case MULTI -> renderMultiRoadWorkspace();
        }
    }

    private void renderNoSelectionWorkspace() {
        RoadUiSections.section("plugin.road.route.new_road_defaults");
        RoadCrossSectionPreviewSection.render(ctx);
        ImGui.spacing();
        defaultParamsPanel.renderRoutePrimary();
    }

    private void renderSingleRoadWorkspace() {
        Road road = ctx.networkManager().getPrimarySelectedRoad();
        if (road == null) {
            return;
        }
        RoadCrossSectionPreviewSection.render(ctx);
        ImGui.spacing();
        RoadPresetCards.renderForRoad(ctx, road, ctx.networkManager()::pushHistory);
        ImGui.spacing();
        RoadRouteQuickTune.renderForRoad(ctx, road, ctx.networkManager()::pushHistory);
        RoadStyleProductControls.renderRoadMaxSlopePresets(
            ctx, road, ctx.networkManager()::pushHistory);

        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.road.route.more_road_settings"))) {
            defaultParamsPanel.renderAdvancedDefaultsCollapsible();
        }
    }

    private void renderMultiRoadWorkspace() {
        var selectedRoadIds = ctx.networkManager().getSelectedRoadIds();
        RoadCrossSectionPreviewSection.render(ctx);
        ImGui.spacing();
        RoadPresetCards.renderForRoads(ctx, selectedRoadIds, ctx.networkManager()::pushHistory);
        ImGui.spacing();
        RoadNetworkManager.BatchEditDefaults synced = ctx.networkManager().loadBatchEditDefaults();
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.batch_edit_hint", selectedRoadIds.size()));
        RoadBatchCrossSectionEditor.renderDraftFields(ctx, synced);
    }

    private void renderAllRoadsCollapsible() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        List<com.plot.plugin.road.model.RoadEdge> allEdges =
            new ArrayList<>(network.getEdges().values());
        ImGui.setNextItemOpen(false, ImGuiCond.FirstUseEver);
        if (!ImGui.collapsingHeader(
            PlotI18n.tr("plugin.road.route.all_roads"),
            ImGuiTreeNodeFlags.None)) {
            return;
        }
        if (allEdges.isEmpty()) {
            return;
        }
        edgeListPanel.renderToolbar("##route");
        edgeListPanel.renderList(true, "route_edge_list");
    }
}
