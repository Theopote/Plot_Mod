package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadTopologyInvariantValidator;
import com.plot.plugin.road.model.RoadTopologyViolation;
import com.plot.plugin.road.validation.RoadValidationMessage;
import com.plot.plugin.road.validation.RoadValidationMessageCatalog;
import com.plot.plugin.road.repair.RoadAutoRepair;
import com.plot.plugin.road.centerline.CenterlineEditStatus;
import com.plot.plugin.road.centerline.CenterlineEditResult;
import com.plot.plugin.road.centerline.RoadCenterlineEditor;
import com.plot.plugin.road.alignment.HorizontalAlignmentCenterlineConsistency;
import com.plot.plugin.road.alignment.HorizontalAlignmentCenterlineMaterializer;
import com.plot.plugin.road.alignment.HorizontalAlignmentGeometry;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.station.ChainageDisplayContext;
import com.plot.plugin.road.station.ChainageDisplayMode;
import com.plot.plugin.road.station.RoadStationFormat;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.terrain.MinecraftTerrainSampler;
import com.plot.plugin.road.terrain.TerrainSampler;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 道路编辑 Tab：网络级批量操作、边列表、基于选中态的节点/边属性编辑。
 * <p>
 * 选中单条道路时采用 Road Design Stack 分组（Identity → Alignment → Typical Section →
 * Station Controls → Segments），不增加顶层 Tab。
 */
public final class RoadEditPanel {
    private final RoadUiContext ctx;
    private final RoadEdgeListPanel edgeListPanel;
    private final RoadJunctionPanel junctionPanel;
    private final RoadNodePropertyPanel nodePropertyPanel;

    /** 全网统一标高草稿（自定义 Y） */
    private final int[] uniformElevationDraft = {64};
    private String lastRecommendationSummary = "";
    private boolean uniformElevationConfirmPending = false;
    /** true = 自动采样应用；false = 自定义 Y */
    private boolean uniformElevationConfirmAuto = true;
    private final RoadIdentityEditor identityEditor = new RoadIdentityEditor();
    private final StationFacilityEditor stationFacilityEditor = new StationFacilityEditor();
    private final VariableCrossSectionEditor variableCrossSectionEditor = new VariableCrossSectionEditor();
    private final VerticalAlignmentEditor verticalAlignmentEditor = new VerticalAlignmentEditor();
    private float centerlineEditDistance = 10f;
    private float centerlineFilletRadius = 2f;
    private int centerlineVertexIndex = 1;
    private String lastCenterlineEditMessage = "";
    private String lastHorizontalAlignmentMessage = "";
    private ChainageDisplayMode chainageDisplayMode = ChainageDisplayMode.FROM_START;

    public RoadEditPanel(
            RoadUiContext ctx,
            RoadEdgeListPanel edgeListPanel,
            RoadJunctionPanel junctionPanel,
            RoadNodePropertyPanel nodePropertyPanel) {
        this.ctx = ctx;
        this.edgeListPanel = edgeListPanel;
        this.junctionPanel = junctionPanel;
        this.nodePropertyPanel = nodePropertyPanel;
    }

    public void render() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        ctx.networkManager().ensureSelectionValid();

        renderUniformFlatElevationControls(network);
        ImGui.separator();

        RoadUiSections.section("plugin.road.section.road_list");
        List<RoadEdge> allEdges = new ArrayList<>(network.getEdges().values());
        if (allEdges.isEmpty()) {
            RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.no_edges"));
        } else {
            ImGui.text(PlotI18n.tr("plugin.road.edge_list"));
            RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.edge_list_hint"));
            edgeListPanel.renderToolbar("##edit");
            edgeListPanel.renderList(true, "edit_edge_list");
        }

        renderSelectionDispatch(network, allEdges.isEmpty());

        ImGui.separator();
        nodePropertyPanel.renderAllNodesCollapsibleList();
    }

    private void renderSelectionDispatch(RoadNetwork network, boolean edgesEmpty) {
        String selectedNodeId = ctx.networkManager().getSelectedNodeId();
        int selectedEdgeCount = ctx.networkManager().getSelectedEdgeIds().size();
        int selectedRoadCount = ctx.networkManager().getSelectedRoadIds().size();
        int selectedLogicalCount = selectedRoadCount > 0 ? selectedRoadCount : selectedEdgeCount;

        if (selectedNodeId != null && !selectedNodeId.isBlank()) {
            RoadUiSections.group("plugin.road.section.node_junction");
            nodePropertyPanel.renderForSelectedNode(junctionPanel);
            return;
        }

        if (edgesEmpty) {
            RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.edit_select_hint"));
            return;
        }

        // 多选：仅批量编辑；单选：仅单条详情，避免两套控件重叠误导用户
        if (selectedLogicalCount > 1) {
            renderBatchEditPanel();
            return;
        }

        if (selectedEdgeCount >= 1) {
            renderSingleEdgeDetail(network);
            return;
        }

        RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.edit_select_hint"));
    }

    private void renderSingleEdgeDetail(RoadNetwork network) {
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
            () -> {
                CenterlineEditResult result = ctx.networkManager().reverseRoad(road);
                lastCenterlineEditMessage = formatCenterlineEditResult(result);
            },
            () -> lastCenterlineEditMessage);
        renderRoadAutoRepair(network, road);
        renderRoadTopologyHints(network, road);

        RoadUiSections.group("plugin.road.design_stack.alignment");
        renderHorizontalAlignmentSummary(network, road, chainageDisplay);
        verticalAlignmentEditor.render(network, road, chainageDisplay, ctx.networkManager()::pushHistory);

        RoadUiSections.group("plugin.road.design_stack.typical_section");
        RoadCrossSectionEditor.renderRoadLevelCollapsibles(ctx, road, ctx.networkManager()::pushHistory);

        RoadUiSections.group("plugin.road.design_stack.station_controls");
        if (chainageDisplay != null) {
            renderChainageDisplayToggle();
        }
        variableCrossSectionEditor.render(ctx, network, road, chainageDisplay, ctx.networkManager()::pushHistory);
        stationFacilityEditor.render(network, road, chainageDisplay, ctx.networkManager()::pushHistory);

        RoadUiSections.group("plugin.road.design_stack.segments");
        renderSegmentList(network, road);
        current = network.getEdge(ctx.networkManager().getPrimarySelectedEdgeId());
        if (current == null) {
            return;
        }
        renderSegmentSummary(network, road, current, chainageDisplay);
        renderCenterlineEditTools(network, road, current);
        renderElevationHint(current);
        renderSlopeOverrides(network, road, current, chainageDisplay);
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

    private ChainageDisplayContext chainageContext(RoadNetwork network, Road road) {
        return new ChainageDisplayContext(
            RoadStationing.canonicalLength(network, road),
            chainageDisplayMode,
            RoadStationFormat.KILOMETER_PLUS);
    }

    private ChainageDisplayContext chainageContextOrNull(RoadNetwork network, Road road) {
        if (!RoadStationing.isStationable(network, road)) {
            return null;
        }
        return chainageContext(network, road);
    }

    private void renderHorizontalAlignmentSummary(
            RoadNetwork network,
            Road road,
            ChainageDisplayContext chainageDisplay) {
        RoadHorizontalAlignment alignment = road.getHorizontalAlignment();
        if (alignment == null || alignment.isEmpty()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.horizontal_alignment_none"));
            return;
        }
        ImGui.spacing();
        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.road.horizontal_alignment_section"))) {
            double total = RoadStationing.canonicalLength(network, road);
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr(
                    "plugin.road.horizontal_alignment_length",
                    alignment.getElements().size(),
                    total,
                    formatAlignmentChainage(chainageDisplay, total, 0.0),
                    formatAlignmentChainage(chainageDisplay, total, total)));
            if (RoadStationing.isStationable(network, road)) {
                HorizontalAlignmentCenterlineConsistency.Report consistency =
                    HorizontalAlignmentCenterlineConsistency.evaluate(network, road);
                if (consistency.evaluable()) {
                    int color = consistency.isConsistent()
                        ? PluginUiColors.HINT_GRAY
                        : PluginUiColors.WARNING;
                    RoadUiWidgets.textWrappedColored(
                        color,
                        PlotI18n.tr(
                            "plugin.road.horizontal_alignment_centerline_deviation",
                            consistency.maxDeviationMeters(),
                            consistency.meanDeviationMeters()));
                    if (!consistency.lengthMatches()) {
                        RoadUiWidgets.textWrappedColored(
                            PluginUiColors.WARNING,
                            PlotI18n.tr(
                                "plugin.road.horizontal_alignment_length_mismatch_hint",
                                consistency.roadLengthMeters(),
                                consistency.alignmentLengthMeters()));
                    }
                }
            }
            if (!lastHorizontalAlignmentMessage.isBlank()) {
                RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, lastHorizontalAlignmentMessage);
            }
            if (RoadStationing.isStationable(network, road)
                && HorizontalAlignmentCenterlineMaterializer.canMaterialize(network, road)) {
                if (ImGui.button(PlotI18n.tr("plugin.road.horizontal_alignment_materialize") + "##ha_mat")) {
                    CenterlineEditResult result = ctx.networkManager().materializeHorizontalAlignment(road);
                    lastHorizontalAlignmentMessage = formatCenterlineEditResult(result);
                }
                ImGui.sameLine();
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.HINT_GRAY,
                    PlotI18n.tr("plugin.road.horizontal_alignment_materialize_hint"));
            }
            int index = 0;
            for (com.plot.plugin.road.alignment.HorizontalAlignmentElement element : alignment.getElements()) {
                double start = HorizontalAlignmentGeometry.elementStartChainage(alignment, index++);
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.HINT_GRAY,
                    chainageDisplay != null
                        ? HorizontalAlignmentGeometry.describeElement(element, start, chainageDisplay)
                        : HorizontalAlignmentGeometry.describeElement(element, start, RoadStationFormat.KILOMETER_PLUS));
            }
        }
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
        List<RoadTopologyViolation> violations = RoadTopologyInvariantValidator.validateRoad(network, road);
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

    private void renderSegmentList(RoadNetwork network, Road road) {
        List<String> segmentIds = RoadEdgeListHelper.orderedSegmentIds(network, road);
        if (segmentIds.isEmpty()) {
            return;
        }

        String primaryId = ctx.networkManager().getPrimarySelectedEdgeId();
        if (!segmentIds.contains(primaryId)) {
            ctx.networkManager().setPrimarySelectedEdge(segmentIds.getFirst());
            primaryId = segmentIds.getFirst();
        }
        for (int i = 0; i < segmentIds.size(); i++) {
            String segmentId = segmentIds.get(i);
            RoadEdge edge = network.getEdge(segmentId);
            if (edge == null) {
                continue;
            }
            boolean selected = segmentId.equals(primaryId);
            String label = PlotI18n.tr("plugin.road.segment_index", i + 1, segmentIds.size())
                + " · " + PlotI18n.tr("plugin.road.segment_length", edge.getLength());
            if (ImGui.selectable(label + "##seg_pick_" + i, selected)) {
                ctx.networkManager().setPrimarySelectedEdge(segmentId);
            }
            if (selected && segmentIds.size() > 1) {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.HINT_GRAY,
                    RoadEdgeListHelper.formatEdgeLabel(network, edge));
            }
        }
        ImGui.spacing();
    }

    private void renderSegmentSummary(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            ChainageDisplayContext chainageDisplay) {
        ImGui.text(PlotI18n.tr("plugin.road.segment_length", edge.getLength()));
        if (RoadStationing.isStationable(network, road)) {
            double segmentStart = RoadStationing.segmentStartStation(network, road, edge.getId());
            if (segmentStart >= 0.0) {
                double segmentEnd = segmentStart + edge.getLength();
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.HINT_GRAY,
                    PlotI18n.tr(
                        "plugin.road.segment_chainage",
                        formatChainage(chainageDisplay, segmentStart),
                        formatChainage(chainageDisplay, segmentEnd)));
            }
        }
        ImGui.text(PlotI18n.tr(
            "plugin.road.segment_start",
            RoadEdgeListHelper.formatNodeLabel(network, edge.getStartNodeId())));
        ImGui.text(PlotI18n.tr(
            "plugin.road.segment_end",
            RoadEdgeListHelper.formatNodeLabel(network, edge.getEndNodeId())));
    }

    private void renderCenterlineEditTools(RoadNetwork network, Road road, RoadEdge edge) {
        ImGui.spacing();
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.centerline_edit_section"))) {
            return;
        }
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.centerline_edit_hint"));

        if (!lastCenterlineEditMessage.isBlank()) {
            RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, lastCenterlineEditMessage);
        }

        float edgeLength = (float) edge.getLength();
        float[] distance = {centerlineEditDistance};
        ImGui.setNextItemWidth(Math.min(220f, ImGui.getContentRegionAvailX()));
        if (ImGui.sliderFloat(PlotI18n.tr("plugin.road.centerline_distance") + "##cl_dist", distance, 0f, Math.max(edgeLength, 1f), "%.1fm")) {
            centerlineEditDistance = distance[0];
        }

        if (ImGui.button(PlotI18n.tr("plugin.road.centerline_insert_pi") + "##cl_pi")) {
            CenterlineEditResult result = ctx.networkManager().insertPiAtLocalDistance(edge.getId(), centerlineEditDistance);
            lastCenterlineEditMessage = formatCenterlineEditResult(result);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.centerline_split") + "##cl_split")) {
            CenterlineEditResult result = ctx.networkManager().splitEdgeAtLocalDistance(edge.getId(), centerlineEditDistance);
            lastCenterlineEditMessage = formatCenterlineEditResult(result);
        }

        List<com.plot.api.geometry.Vec2d> points = edge.getCenterlinePoints();
        int interiorCount = Math.max(0, points.size() - 2);
        if (interiorCount > 0) {
            centerlineVertexIndex = Math.min(centerlineVertexIndex, interiorCount);
            centerlineVertexIndex = Math.max(1, centerlineVertexIndex);
            ImGui.setNextItemWidth(Math.min(160f, ImGui.getContentRegionAvailX()));
            int[] vertexValue = {centerlineVertexIndex};
            if (ImGui.sliderInt(PlotI18n.tr("plugin.road.centerline_vertex") + "##cl_vtx", vertexValue, 1, interiorCount)) {
                centerlineVertexIndex = vertexValue[0];
            }
            float[] radius = {centerlineFilletRadius};
            ImGui.setNextItemWidth(Math.min(160f, ImGui.getContentRegionAvailX()));
            if (ImGui.sliderFloat(PlotI18n.tr("plugin.road.centerline_fillet_radius") + "##cl_r", radius, 0.5f, 20f, "%.1fm")) {
                centerlineFilletRadius = radius[0];
            }
            if (ImGui.button(PlotI18n.tr("plugin.road.centerline_fillet") + "##cl_fillet")) {
                CenterlineEditResult result = ctx.networkManager().filletCenterlineVertex(
                    edge.getId(),
                    centerlineVertexIndex,
                    centerlineFilletRadius
                );
                lastCenterlineEditMessage = formatCenterlineEditResult(result);
            }
        }

        if (ImGui.button(PlotI18n.tr("plugin.road.centerline_reverse_segment") + "##cl_rev_seg")) {
            CenterlineEditResult result = ctx.networkManager().reverseEdge(edge.getId());
            lastCenterlineEditMessage = formatCenterlineEditResult(result);
        }

        renderMergeButtons(network, edge);
    }

    private void renderMergeButtons(RoadNetwork network, RoadEdge edge) {
        String startNodeId = edge.getStartNodeId();
        String endNodeId = edge.getEndNodeId();
        boolean canMergeStart = RoadCenterlineEditor.canMergeAtNode(network, startNodeId);
        boolean canMergeEnd = RoadCenterlineEditor.canMergeAtNode(network, endNodeId);
        if (!canMergeStart && !canMergeEnd) {
            return;
        }
        if (canMergeStart && ImGui.button(PlotI18n.tr("plugin.road.centerline_merge_start") + "##cl_m_s")) {
            CenterlineEditResult result = ctx.networkManager().mergeSegmentsAtNode(startNodeId);
            lastCenterlineEditMessage = formatCenterlineEditResult(result);
        }
        if (canMergeStart && canMergeEnd) {
            ImGui.sameLine();
        }
        if (canMergeEnd && ImGui.button(PlotI18n.tr("plugin.road.centerline_merge_end") + "##cl_m_e")) {
            CenterlineEditResult result = ctx.networkManager().mergeSegmentsAtNode(endNodeId);
            lastCenterlineEditMessage = formatCenterlineEditResult(result);
        }
    }

    private String formatCenterlineEditResult(CenterlineEditResult result) {
        if (result == null) {
            return PlotI18n.tr("plugin.road.centerline_edit_failed");
        }
        if (result.isSuccess()) {
            if (result.detailMessageKey() != null && !result.detailMessageKey().isBlank()) {
                return PlotI18n.tr(result.detailMessageKey());
            }
            if (result.mergedEdgeId() != null) {
                return PlotI18n.tr("plugin.road.centerline_edit_merged");
            }
            if (result.secondEdgeId() != null) {
                return PlotI18n.tr("plugin.road.centerline_edit_split");
            }
            return PlotI18n.tr("plugin.road.centerline_edit_success");
        }
        String key = switch (result.status()) {
            case EDGE_NOT_FOUND -> "plugin.road.centerline_edit_edge_not_found";
            case INVALID_DISTANCE -> "plugin.road.centerline_edit_invalid_distance";
            case INVALID_VERTEX -> "plugin.road.centerline_edit_invalid_vertex";
            case INVALID_RADIUS -> "plugin.road.centerline_edit_invalid_radius";
            case SPLIT_FAILED -> "plugin.road.centerline_edit_split_failed";
            case MERGE_FAILED -> "plugin.road.centerline_edit_merge_failed";
            case ALIGNMENT_STATIONS_INVALID -> "plugin.road.centerline_edit_alignment_invalid";
            case HORIZONTAL_ALIGNMENT_NOT_DEFINED -> "plugin.road.horizontal_alignment_materialize_no_alignment";
            case ROAD_NOT_STATIONABLE -> "plugin.road.horizontal_alignment_materialize_not_stationable";
            case JUNCTION_ENDPOINT_CONFLICT -> null;
            default -> "plugin.road.centerline_edit_failed";
        };
        if (key == null) {
            RoadValidationMessage message = RoadValidationMessageCatalog.fromCenterlineStatus(result.status());
            if (message != null) {
                return PlotI18n.tr(message.titleKey(), message.args())
                    + "\n" + PlotI18n.tr(message.detailKey(), message.args());
            }
            return PlotI18n.tr("plugin.road.centerline_edit_failed");
        }
        return PlotI18n.tr(key);
    }

    private void renderUniformFlatElevationControls(RoadNetwork network) {
        // 折叠收起：全网破坏性操作，避免与日常边编辑抢视觉焦点
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.uniform_flat_elevation"))) {
            return;
        }
        RoadUiWidgets.textWrappedColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.road.uniform_flat_elevation_warning"));
        RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.uniform_flat_elevation_hint"));

        boolean disabled = network.getEdges().isEmpty();
        if (disabled) {
            ImGui.beginDisabled();
        }

        float half = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;

        if (ImGui.button(PlotI18n.tr("plugin.road.uniform_elevation_auto_apply"), half, 0)) {
            uniformElevationConfirmAuto = true;
            uniformElevationConfirmPending = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.uniform_elevation_auto_apply_hint"));
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.uniform_elevation_sample"), half, 0)) {
            sampleUniformElevationSuggestion();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.uniform_elevation_sample_hint"));
        }

        if (!lastRecommendationSummary.isBlank()) {
            RoadUiWidgets.textWrappedColored(PluginUiColors.STATUS_INFO, lastRecommendationSummary);
        }

        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.55f);
        ImGui.sliderInt(
            PlotI18n.tr("plugin.road.uniform_elevation_custom_y") + "##uniform_y",
            uniformElevationDraft,
            RoadParameterLimits.ELEVATION_MIN,
            RoadParameterLimits.ELEVATION_MAX,
            "Y=%d"
        );
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.node_elevation"));
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.uniform_elevation_custom_apply"), 0, 0)) {
            uniformElevationConfirmAuto = false;
            uniformElevationConfirmPending = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.uniform_elevation_custom_apply_hint"));
        }

        if (disabled) {
            ImGui.endDisabled();
        }
        ImGui.spacing();
    }

    /**
     * 统一标高二次确认（破坏性：全节点手动 Y + 各路 maxSlope=0）。
     */
    public void renderUniformElevationConfirmPopup() {
        if (uniformElevationConfirmPending) {
            ImGui.openPopup("##road_uniform_elevation_confirm");
            uniformElevationConfirmPending = false;
        }

        if (ImGui.beginPopupModal("##road_uniform_elevation_confirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            if (uniformElevationConfirmAuto) {
                ImGui.textWrapped(PlotI18n.tr("plugin.road.uniform_elevation_confirm_auto"));
            } else {
                ImGui.textWrapped(PlotI18n.tr(
                    "plugin.road.uniform_elevation_confirm_custom",
                    uniformElevationDraft[0]));
            }
            ImGui.separator();
            RoadUiWidgets.textWrappedColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.road.uniform_elevation_confirm_side_effect"));

            if (ImGui.button(PlotI18n.tr("plugin.road.uniform_elevation_confirm_ok"), 120, 0)) {
                if (uniformElevationConfirmAuto) {
                    applyUniformFlatElevationAuto();
                } else {
                    ctx.networkManager().applyCustomUniformFlatElevation(uniformElevationDraft[0]);
                }
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 120, 0)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
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

    private void applyUniformFlatElevationAuto() {
        TerrainSampler terrain = requireTerrainOrNull();
        if (terrain == null) {
            return;
        }
        var recommendation = ctx.networkManager().applyUniformFlatElevation(terrain);
        if (recommendation != null) {
            uniformElevationDraft[0] = recommendation.elevation();
            lastRecommendationSummary = formatRecommendation(recommendation);
        }
    }

    private void sampleUniformElevationSuggestion() {
        TerrainSampler terrain = requireTerrainOrNull();
        if (terrain == null) {
            return;
        }
        var recommendation = ctx.networkManager().previewUniformElevation(terrain);
        if (recommendation != null) {
            uniformElevationDraft[0] = recommendation.elevation();
            lastRecommendationSummary = formatRecommendation(recommendation);
        }
    }

    private static String formatRecommendation(
            com.plot.plugin.road.RoadUniformElevationUtils.ElevationRecommendation recommendation) {
        String strategy = recommendation.usedMode()
            ? PlotI18n.tr("plugin.road.uniform_elevation_strategy_mode")
            : PlotI18n.tr("plugin.road.uniform_elevation_strategy_average");
        return PlotI18n.tr(
            "plugin.road.uniform_elevation_preview",
            recommendation.elevation(),
            strategy,
            recommendation.sampleCount(),
            String.format("%.1f", recommendation.average()));
    }

    private void renderElevationHint(RoadEdge edge) {
        RoadGenerationResult edgeResult = ctx.previewManager().getLastEdgeResult(edge.getId());
        if (edgeResult == null || !edgeResult.hasProfileData()) {
            RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.elevation_hint_preview_required"));
            return;
        }

        int startGround = edgeResult.profileGroundHeights.getFirst();
        int endGround = edgeResult.profileGroundHeights.getLast();
        int startGuide = edgeResult.profileGuideLine.getFirst();
        int endGuide = edgeResult.profileGuideLine.getLast();
        ImGui.text(PlotI18n.tr("plugin.road.elevation_hint_start", startGround, startGuide));
        ImGui.text(PlotI18n.tr("plugin.road.elevation_hint_end", endGround, endGuide));
    }

    private void renderSlopeOverrides(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            ChainageDisplayContext chainageDisplay) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        ImGui.spacing();
        ImGui.text(PlotI18n.tr("plugin.road.slope_overrides"));
        RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.slope_override_segment_hint"));
        List<RoadEdge.SlopeOverride> overrides = new ArrayList<>(edge.getSlopeOverrides());
        List<RoadEdge.SlopeOverride> originalOverrides = RoadNetworkManager.snapshotSlopeOverrides(overrides);

        for (int i = 0; i < overrides.size(); i++) {
            RoadEdge.SlopeOverride override = overrides.get(i);
            float[] start = {(float) override.startDistance};
            float[] end = {(float) override.endDistance};
            float[] slope = {override.maxSlope};
            ImGui.pushID(i);

            float rowWidth = ImGui.getContentRegionAvailX();
            float spacing = ImGui.getStyle().getItemSpacingX();
            String deleteLabel = PlotI18n.tr("plugin.road.delete");
            float deleteWidth = ImGui.calcTextSize(deleteLabel, false, 0.0f).x
                + ImGui.getStyle().getFramePaddingX() * 2.0f;
            boolean stackRangeSliders = rowWidth < deleteWidth + spacing + 160.0f;
            float edgeLength = (float) edge.getLength();

            if (stackRangeSliders) {
                ImGui.setNextItemWidth(rowWidth);
                ImGui.sliderFloat(PlotI18n.tr("plugin.road.slope_start") + "##s", start, 0, edgeLength, "%.1fm");
            } else {
                float sliderWidth = (rowWidth - deleteWidth - spacing * 2.0f) / 2.0f;
                ImGui.setNextItemWidth(sliderWidth);
                ImGui.sliderFloat(PlotI18n.tr("plugin.road.slope_start") + "##s", start, 0, edgeLength, "%.1fm");
            }
            if (ImGui.isItemActivated()) {
                ctx.networkManager().pushHistory();
            }
            // 滑条在 isItemActivated 之后写回，保证历史快照为改前值
            override.startDistance = start[0];
            if (override.startDistance > override.endDistance) {
                override.endDistance = override.startDistance;
                end[0] = (float) override.endDistance;
            }

            if (stackRangeSliders) {
                ImGui.setNextItemWidth(rowWidth);
                ImGui.sliderFloat(PlotI18n.tr("plugin.road.slope_end") + "##e", end, start[0], edgeLength, "%.1fm");
            } else {
                ImGui.sameLine();
                float sliderWidth = (rowWidth - deleteWidth - spacing * 2.0f) / 2.0f;
                ImGui.setNextItemWidth(sliderWidth);
                ImGui.sliderFloat(PlotI18n.tr("plugin.road.slope_end") + "##e", end, start[0], edgeLength, "%.1fm");
            }
            if (ImGui.isItemActivated()) {
                ctx.networkManager().pushHistory();
            }
            override.endDistance = end[0];

            if (!stackRangeSliders) {
                ImGui.sameLine();
            }
            ImGui.pushStyleColor(ImGuiCol.Button, PluginUiColors.DELETE);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, PluginUiColors.DELETE_HOVER);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, PluginUiColors.DELETE_ACTIVE);
            if (ImGui.smallButton(deleteLabel + "##rm")) {
                ctx.networkManager().pushHistory();
                overrides.remove(i);
                edge.setSlopeOverrides(overrides);
                ImGui.popStyleColor(3);
                ImGui.popID();
                return;
            }
            ImGui.popStyleColor(3);

            if (com.plot.ui.component.EngineeringSlopeInput.render(
                "slope_override_" + i,
                PlotI18n.tr("plugin.road.slope_value"),
                slope,
                com.plot.ui.component.EngineeringSlopeInput.ValueKind.GRADE
            )) {
                ctx.networkManager().pushHistory();
            }
            override.maxSlope = RoadParameterLimits.clampGradePercent(slope[0]);

            if (RoadStationing.isStationable(network, road)) {
                RoadStationing.stationAt(network, road, edge.getId(), override.startDistance)
                    .ifPresent(startStation -> RoadStationing.stationAt(network, road, edge.getId(), override.endDistance)
                        .ifPresent(endStation -> RoadUiWidgets.textWrappedColored(
                            PluginUiColors.HINT_GRAY,
                            PlotI18n.tr(
                                "plugin.road.slope_override_station_hint",
                                formatChainage(chainageDisplay, startStation.chainageMeters()),
                                formatChainage(chainageDisplay, endStation.chainageMeters())))));
            }

            if (override.startDistance > override.endDistance) {
                RoadUiWidgets.textWrappedColored(PluginUiColors.INVALID, PlotI18n.tr("plugin.road.slope_range_invalid"));
            } else if (RoadNetworkManager.hasOverlappingOverride(overrides, i)) {
                RoadUiWidgets.textWrappedColored(PluginUiColors.WARNING_OVERLAP, PlotI18n.tr("plugin.road.slope_range_overlap"));
            }

            ImGui.popID();
        }

        if (!RoadNetworkManager.slopeOverridesEqual(overrides, originalOverrides)) {
            edge.setSlopeOverrides(overrides);
        }

        if (ImGui.button(PlotI18n.tr("plugin.road.add_slope_override"))) {
            ctx.networkManager().pushHistory();
            overrides.add(new RoadEdge.SlopeOverride(0, (float) edge.getLength(), config.getMaxSlope()));
            edge.setSlopeOverrides(overrides);
        }
    }

    private void renderBatchEditPanel() {
        int selectedEdgeCount = ctx.networkManager().getSelectedEdgeIds().size();
        if (selectedEdgeCount == 0) {
            return;
        }
        int selectedRoadCount = ctx.networkManager().getSelectedRoadIds().size();
        int displayCount = selectedRoadCount > 0 ? selectedRoadCount : selectedEdgeCount;
        int headerFlags = displayCount > 1 ? ImGuiTreeNodeFlags.DefaultOpen : 0;
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.batch_edit"), headerFlags)) {
            return;
        }

        RoadNetworkManager.BatchEditDefaults synced = ctx.networkManager().loadBatchEditDefaults();
        RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.batch_edit_hint", displayCount));
        RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.batch_cross_section_only"));
        RoadBatchCrossSectionEditor.renderDraftFields(ctx, synced);
    }

    private static String formatChainage(ChainageDisplayContext display, double chainageMeters) {
        if (display != null) {
            return display.format(chainageMeters);
        }
        return RoadStationing.format(chainageMeters, RoadStationFormat.KILOMETER_PLUS);
    }

    private static String formatAlignmentChainage(
            ChainageDisplayContext display,
            double alignmentTotal,
            double chainageMeters) {
        if (display != null) {
            return new ChainageDisplayContext(alignmentTotal, display.mode(), display.format()).format(chainageMeters);
        }
        return RoadStationing.format(chainageMeters, RoadStationFormat.KILOMETER_PLUS);
    }
}
