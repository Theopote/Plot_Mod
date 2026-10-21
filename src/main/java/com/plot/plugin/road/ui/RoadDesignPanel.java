package com.plot.plugin.road.ui;

import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.ChainageDisplayContext;
import com.plot.plugin.road.station.ChainageDisplayMode;
import com.plot.plugin.road.station.RoadStationFormat;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;

/**
 * 单条逻辑道路 Design Stack（主界面产品化 + 高级道路设计折叠区）。
 */
final class RoadDesignPanel {

    private final RoadUiContext ctx;
    private final RoadIdentityEditor identityEditor = new RoadIdentityEditor();
    private final HorizontalAlignmentSummaryEditor horizontalAlignmentEditor = new HorizontalAlignmentSummaryEditor();
    private final VariableCrossSectionEditor variableCrossSectionEditor = new VariableCrossSectionEditor();
    private final StationFacilityEditor stationFacilityEditor = new StationFacilityEditor();
    private final RoadCenterlineEditPanel centerlineEditPanel = new RoadCenterlineEditPanel();
    private final RoadSegmentEditor segmentEditor = new RoadSegmentEditor();
    private final RoadNetworkToolsPanel networkToolsPanel;
    private ChainageDisplayMode chainageDisplayMode = ChainageDisplayMode.FROM_START;
    private boolean forceOpenAdvancedDesign;
    private boolean forceOpenDiagnostics;

    RoadDesignPanel(RoadUiContext ctx) {
        this.ctx = ctx;
        this.networkToolsPanel = new RoadNetworkToolsPanel(ctx);
    }

    void requestOpenDiagnostics() {
        forceOpenAdvancedDesign = true;
        forceOpenDiagnostics = true;
    }

    void renderUniformElevationConfirmPopup() {
        networkToolsPanel.renderConfirmPopup();
    }

    void renderAdvancedDesignSection(RoadNetwork network, Road road) {
        int headerFlags = forceOpenAdvancedDesign ? ImGuiTreeNodeFlags.DefaultOpen : 0;
        if (forceOpenAdvancedDesign) {
            forceOpenAdvancedDesign = false;
        }
        if (!ImGui.collapsingHeader(
            PlotI18n.tr("plugin.road.style.advanced_design"),
            headerFlags)) {
            return;
        }
        String primaryId = ctx.networkManager().getPrimarySelectedEdgeId();
        RoadEdge current = network.getEdge(primaryId);
        if (current == null) {
            return;
        }
        ChainageDisplayContext chainageDisplay = chainageContextOrNull(network, road);
        renderAdvancedDesign(network, road, current, chainageDisplay);
    }

    private void renderAdvancedDesign(
            RoadNetwork network,
            Road road,
            RoadEdge current,
            ChainageDisplayContext chainageDisplay) {
        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.road.design_stack.identity"))) {
            identityEditor.render(network, road, ctx.networkManager(), ctx.networkManager()::pushHistory);
            renderRoadIdentitySummary(network, road, chainageDisplay);
            if (chainageDisplay != null) {
                renderChainageDisplayToggle();
            }
        }

        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.road.design_stack.alignment"))) {
            horizontalAlignmentEditor.render(ctx, network, road, chainageDisplay);
            RoadCrossSectionEditor.renderAdvancedCrossSection(ctx, road, ctx::pushRoadEditHistory);
        }

        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.road.design_stack.variable_cross_section"))) {
            variableCrossSectionEditor.render(ctx, network, road, chainageDisplay, ctx.networkManager()::pushHistory);
        }

        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.road.design_stack.station_facilities"))) {
            stationFacilityEditor.render(network, road, chainageDisplay, ctx.networkManager()::pushHistory);
        }

        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.road.design_stack.segments"))) {
            segmentEditor.renderSegmentList(ctx, network, road);
            segmentEditor.renderSegmentSummary(network, road, current, chainageDisplay);
            centerlineEditPanel.render(ctx, network, road, current);
            segmentEditor.renderElevationHint(ctx, current);
            if (chainageDisplay != null) {
                renderSlopeOverridesSection(network, road, current, chainageDisplay);
            }
        }

        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.road.generate.advanced_terrain"))) {
            RoadUiWidgets.textWrappedColored(
                com.plot.plugin.ui.PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.generate.advanced_terrain_hint"));
            networkToolsPanel.render(network);
        }

        int diagnosticsFlags = forceOpenDiagnostics ? ImGuiTreeNodeFlags.DefaultOpen : 0;
        if (forceOpenDiagnostics) {
            forceOpenDiagnostics = false;
        }
        if (ImGui.collapsingHeader(
            PlotI18n.tr("plugin.road.design_stack.diagnostics"),
            diagnosticsFlags)) {
            RoadEditDiagnosticsBanner.renderDetailedIssues(ctx, network, road);
        }
    }

    private void renderSlopeOverridesSection(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            ChainageDisplayContext chainageDisplay) {
        ImGui.spacing();
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.generate.slope_overrides"))) {
            return;
        }
        RoadSegmentEditor.renderSlopeOverrides(ctx, network, road, edge, chainageDisplay);
    }

    private void renderRoadIdentitySummary(
            RoadNetwork network,
            Road road,
            ChainageDisplayContext chainageDisplay) {
        int segmentCount = road.getSegmentIds().size();
        double length = RoadEdgeListHelper.computeRoadLength(network, road);
        RoadUiWidgets.textWrappedColored(
            com.plot.plugin.ui.PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.road_scope_summary", segmentCount, length));
    }

    private void renderChainageDisplayToggle() {
        boolean fromEnd = chainageDisplayMode == ChainageDisplayMode.FROM_END;
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.chainage_display_from_end"), fromEnd)) {
            chainageDisplayMode = fromEnd ? ChainageDisplayMode.FROM_START : ChainageDisplayMode.FROM_END;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.chainage_display_mode"));
        }
        RoadUiWidgets.textWrappedColored(
            com.plot.plugin.ui.PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.chainage_display_format_hint"));
    }

    private ChainageDisplayContext chainageContextOrNull(RoadNetwork network, Road road) {
        if (!RoadStationing.isStationable(network, road)) {
            return null;
        }
        return new ChainageDisplayContext(
            RoadStationing.canonicalLength(network, road),
            chainageDisplayMode,
            RoadStationFormat.DISTANCE_METERS);
    }
}
