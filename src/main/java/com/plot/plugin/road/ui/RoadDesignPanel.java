package com.plot.plugin.road.ui;

import com.plot.core.terrain.MinecraftTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadTopologyInvariantValidator;
import com.plot.plugin.road.model.RoadTopologyViolation;
import com.plot.plugin.road.repair.RoadAutoRepair;
import com.plot.plugin.road.station.ChainageDisplayContext;
import com.plot.plugin.road.station.ChainageDisplayMode;
import com.plot.plugin.road.station.RoadStationFormat;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.centerline.RoadCenterlineShapeValidator;
import com.plot.plugin.road.centerline.RoadCenterlineViolation;
import com.plot.plugin.road.validation.RoadValidationMessage;
import com.plot.plugin.road.validation.RoadValidationMessageCatalog;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import net.minecraft.world.World;

import java.util.List;

/**
 * 单条逻辑道路 Design Stack（Identity → Alignment → Section → Station → Segments）。
 */
final class RoadDesignPanel {

    private final RoadUiContext ctx;
    private final RoadIdentityEditor identityEditor = new RoadIdentityEditor();
    private final VerticalAlignmentEditor verticalAlignmentEditor = new VerticalAlignmentEditor();
    private final VerticalProfileEditor verticalProfileEditor = new VerticalProfileEditor();
    private final HorizontalAlignmentSummaryEditor horizontalAlignmentEditor = new HorizontalAlignmentSummaryEditor();
    private final VariableCrossSectionEditor variableCrossSectionEditor = new VariableCrossSectionEditor();
    private final StationFacilityEditor stationFacilityEditor = new StationFacilityEditor();
    private final RoadCenterlineEditPanel centerlineEditPanel = new RoadCenterlineEditPanel();
    private final RoadSegmentEditor segmentEditor = new RoadSegmentEditor();
    private ChainageDisplayMode chainageDisplayMode = ChainageDisplayMode.FROM_START;

    RoadDesignPanel(RoadUiContext ctx) {
        this.ctx = ctx;
    }

    void render(RoadNetwork network) {
        ImGui.separator();
        String primaryId = ctx.networkManager().getPrimarySelectedEdgeId();
        RoadEdge current = network.getEdge(primaryId);
        if (current == null) {
            return;
        }
        Road road = ctx.networkManager().getRoadForEdge(current);
        if (road == null) {
            return;
        }

        RoadUiSections.roadHeader();
        ChainageDisplayContext chainageDisplay = chainageContextOrNull(network, road);

        RoadUiSections.group("plugin.road.design_stack.identity");
        identityEditor.render(network, road, ctx.networkManager()::pushHistory);
        renderRoadIdentitySummary(network, road, chainageDisplay);
        RoadDirectionIndicator.render(
            network,
            road,
            () -> centerlineEditPanel.recordMessage(ctx.networkManager().reverseRoad(road)),
            centerlineEditPanel::lastMessage);
        renderRoadAutoRepair(network, road);
        renderRoadTopologyHints(network, road);
        renderCenterlineShapeHints(network, road);

        RoadUiSections.group("plugin.road.design_stack.alignment");
        horizontalAlignmentEditor.render(ctx, network, road, chainageDisplay);
        verticalAlignmentEditor.render(
            network, road, chainageDisplay, ctx.networkManager().getConfig(),
            this::requireTerrainOrNull,
            ctx.networkManager()::pushHistory);
        verticalProfileEditor.renderInline(ctx, network, current);

        RoadUiSections.group("plugin.road.design_stack.typical_section");
        RoadCrossSectionEditor.renderRoadLevelCollapsibles(ctx, road, ctx.networkManager()::pushHistory);

        RoadUiSections.group("plugin.road.design_stack.station_controls");
        if (chainageDisplay != null) {
            renderChainageDisplayToggle();
        }
        variableCrossSectionEditor.render(ctx, network, road, chainageDisplay, ctx.networkManager()::pushHistory);
        stationFacilityEditor.render(network, road, chainageDisplay, ctx.networkManager()::pushHistory);

        RoadUiSections.group("plugin.road.design_stack.segments");
        segmentEditor.renderSegmentList(ctx, network, road);
        current = network.getEdge(ctx.networkManager().getPrimarySelectedEdgeId());
        if (current == null) {
            return;
        }
        segmentEditor.renderSegmentSummary(network, road, current, chainageDisplay);
        centerlineEditPanel.render(ctx, network, road, current);
        segmentEditor.renderElevationHint(ctx, current);
        segmentEditor.renderSlopeOverrides(ctx, network, road, current, chainageDisplay);
    }

    private void renderRoadIdentitySummary(
            RoadNetwork network,
            Road road,
            ChainageDisplayContext chainageDisplay) {
        int segmentCount = road.getSegmentIds().size();
        double length = RoadEdgeListHelper.computeRoadLength(network, road);
        ImGui.text(PlotI18n.tr("plugin.road.design_stack.length", length));
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.road_scope_summary", segmentCount, length));
        if (chainageDisplay != null) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr(
                    "plugin.road.chainage_range",
                    chainageDisplay.format(0.0),
                    chainageDisplay.format(chainageDisplay.totalLength())));
        }
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
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.chainage_display_format_hint"));
    }

    private ChainageDisplayContext chainageContextOrNull(RoadNetwork network, Road road) {
        if (!RoadStationing.isStationable(network, road)) {
            return null;
        }
        return new ChainageDisplayContext(
            RoadStationing.canonicalLength(network, road),
            chainageDisplayMode,
            RoadStationFormat.KILOMETER_PLUS);
    }

    private void renderRoadAutoRepair(RoadNetwork network, Road road) {
        RoadAutoRepairUi.render(ctx, network, road);
    }

    private void renderRoadTopologyHints(RoadNetwork network, Road road) {
        if (!RoadAutoRepair.diagnose(
                network,
                road,
                ctx.networkManager().getConfig(),
                new com.plot.plugin.road.RoadNetworkBuilder().probeIntersectionCompleteness(network),
                ctx.networkManager().isAdoptIntersectionRepairPending()).isEmpty()) {
            return;
        }
        java.util.List<RoadTopologyViolation> violations = RoadTopologyInvariantValidator.validateRoad(network, road);
        if (violations.isEmpty()) {
            return;
        }
        for (RoadTopologyViolation violation : violations) {
            RoadValidationMessage message = RoadValidationMessageCatalog.fromTopologyKind(violation.kind());
            if (message != null) {
                RoadValidationMessageUi.render(message, ctx, network, road);
            }
        }
    }

    private void renderCenterlineShapeHints(RoadNetwork network, Road road) {
        List<RoadCenterlineViolation> violations = RoadCenterlineShapeValidator.validateRoad(network, road);
        if (violations.isEmpty()) {
            return;
        }
        for (RoadCenterlineViolation violation : violations) {
            RoadValidationMessage message = RoadValidationMessageCatalog.fromCenterlineKind(violation.kind());
            if (message != null) {
                RoadValidationMessageUi.render(message, ctx, network, road);
            }
        }
    }

    private TerrainSampler requireTerrainOrNull() {
        World world = RoadNetworkGenerator.getClientWorld();
        if (world == null) {
            ctx.status().error(PlotI18n.tr("plugin.road.generate_world_unavailable"));
            return null;
        }
        return MinecraftTerrainSampler.of(world, ctx.host().coordinates());
    }
}
