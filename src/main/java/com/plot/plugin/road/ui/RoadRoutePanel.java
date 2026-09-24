package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadEdgeListHelper;
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
 * 路线 Tab：当前道路、拾取路径、默认/快速参数、路网摘要、全部道路列表。
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
        renderCurrentRoadHeader();

        ImGui.separator();
        adoptPanel.render();

        if (!ctx.networkManager().getNetwork().getEdges().isEmpty()) {
            ImGui.separator();
            overviewPanel.renderCompactNetworkSummary();
            overviewPanel.renderHealthWarnings(ctx.networkManager().getNetwork());
        }

        ImGui.separator();
        renderRoadParametersSection();

        ImGui.separator();
        renderAllRoadsCollapsible();
    }

    private void renderRoadParametersSection() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        Road road = ctx.networkManager().getPrimarySelectedRoad();
        var selectedRoadIds = ctx.networkManager().getSelectedRoadIds();

        if (road != null && selectedRoadIds.size() == 1) {
            RoadSystemConfig config = ctx.networkManager().getConfig();
            RoadCrossSectionEditor.renderPreview(road, config);
            ImGui.spacing();
            RoadCrossSectionEditor.renderPresetButtons(
                ctx, road, ctx.networkManager()::pushHistory);
            ImGui.spacing();
            RoadRouteQuickTune.renderForRoad(ctx, road, ctx.networkManager()::pushHistory);
            RoadStyleProductControls.renderRoadMaxSlopePresets(
                ctx, road, ctx.networkManager()::pushHistory);
            return;
        }

        defaultParamsPanel.renderRoutePrimary();
    }

    private void renderCurrentRoadHeader() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        ImGui.text(PlotI18n.tr("plugin.road.route.current_road"));
        ImGui.sameLine();
        Road road = ctx.networkManager().getPrimarySelectedRoad();
        if (road != null) {
            ImGui.textColored(
                PluginUiColors.INFO_BLUE,
                RoadEdgeListHelper.formatRoadLabel(network, road));
        } else if (network.getEdges().isEmpty()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, "—");
        } else {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.route.current_road_empty"));
        }
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
