package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.manager.RoadChangeKind;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.ChainageDisplayContext;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

import java.util.ArrayList;
import java.util.List;

/**
 * 逻辑道路分段列表、桩号摘要、坡度 override 与标高提示。
 */
public final class RoadSegmentEditor {

    private String slopeOverrideEditEdgeId = "";
    private List<RoadEdge.SlopeOverride> slopeOverrideEditDraft = null;

    public void renderSegmentList(RoadUiContext ctx, RoadNetwork network, Road road) {
        List<String> segmentIds = RoadEdgeListHelper.orderedSegmentIds(network, road);
        if (segmentIds.isEmpty()) {
            return;
        }

        String primaryId = ctx.networkManager().getPrimarySelectedEdgeId();
        if (!segmentIds.contains(primaryId)) {
            finishOrCancelSlopeOverrideEdit(ctx);
            ctx.networkManager().setPrimarySelectedEdge(segmentIds.getFirst());
            primaryId = segmentIds.getFirst();
        }
        renderSegmentListRows(
            ctx,
            network,
            segmentIds,
            primaryId,
            segmentId -> {
                finishOrCancelSlopeOverrideEdit(ctx);
                ctx.networkManager().setPrimarySelectedEdge(segmentId);
            });
    }

    /**
     * 浮动编辑器草稿分段列表：选择仅写入调用方维护的 working edge id，不改全局选中。
     */
    public void renderWorkingSegmentList(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            String selectedEdgeId,
            java.util.function.Consumer<String> onSelect) {
        List<String> segmentIds = RoadEdgeListHelper.orderedSegmentIds(network, road);
        if (segmentIds.isEmpty()) {
            return;
        }
        String primaryId = selectedEdgeId;
        if (primaryId == null || primaryId.isBlank() || !segmentIds.contains(primaryId)) {
            primaryId = segmentIds.getFirst();
            if (onSelect != null) {
                onSelect.accept(primaryId);
            }
        }
        renderSegmentListRows(ctx, network, segmentIds, primaryId, onSelect);
    }

    private void renderSegmentListRows(
            RoadUiContext ctx,
            RoadNetwork network,
            List<String> segmentIds,
            String primaryId,
            java.util.function.Consumer<String> onSelect) {
        var coordinates = ctx.host().coordinates();
        for (int i = 0; i < segmentIds.size(); i++) {
            String segmentId = segmentIds.get(i);
            RoadEdge edge = network.getEdge(segmentId);
            if (edge == null) {
                continue;
            }
            boolean selected = segmentId.equals(primaryId);
            String label = PlotI18n.tr("plugin.road.segment_index", i + 1, segmentIds.size())
                + " · " + PlotI18n.tr(
                    "plugin.road.segment_length",
                    RoadLengthDisplay.formatEdgeMeters(edge, coordinates));
            if (ImGui.selectable(label + "##seg_pick_" + i, selected) && !selected) {
                if (onSelect != null) {
                    onSelect.accept(segmentId);
                }
            }
            if (selected && segmentIds.size() > 1) {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.HINT_GRAY,
                    RoadEdgeListHelper.formatEdgeLabel(network, edge, coordinates));
            }
        }
        ImGui.spacing();
    }

    public void renderSegmentSummary(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            ChainageDisplayContext chainageDisplay) {
        var coordinates = ctx.host().coordinates();
        ImGui.text(PlotI18n.tr(
            "plugin.road.segment_length",
            RoadLengthDisplay.formatEdgeMeters(edge, coordinates)));
        if (RoadStationing.isStationable(network, road)) {
            double segmentStart = RoadStationing.segmentStartStation(network, road, edge.getId());
            if (segmentStart >= 0.0) {
                double segmentEnd = segmentStart + edge.getLength();
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.HINT_GRAY,
                    PlotI18n.tr(
                        "plugin.road.segment_chainage",
                        RoadLengthDisplay.formatChainage(
                            chainageDisplay, segmentStart, network, road, coordinates),
                        RoadLengthDisplay.formatChainage(
                            chainageDisplay, segmentEnd, network, road, coordinates)));
            }
        }
        ImGui.text(PlotI18n.tr(
            "plugin.road.segment_start",
            RoadEdgeListHelper.formatNodeLabel(network, edge.getStartNodeId())));
        ImGui.text(PlotI18n.tr(
            "plugin.road.segment_end",
            RoadEdgeListHelper.formatNodeLabel(network, edge.getEndNodeId())));
    }

    public void renderElevationHint(RoadUiContext ctx, RoadEdge edge) {
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

    public void renderSlopeOverrides(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            ChainageDisplayContext chainageDisplay) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        ImGui.spacing();
        ImGui.text(PlotI18n.tr("plugin.road.slope_overrides"));
        RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.slope_override_segment_hint"));

        if (!slopeOverrideEditEdgeId.isEmpty() && !slopeOverrideEditEdgeId.equals(edge.getId())) {
            finishOrCancelSlopeOverrideEdit(ctx);
        }
        List<RoadEdge.SlopeOverride> overrides = overridesForEdit(edge);
        boolean editDeactivated = false;

        for (int i = 0; i < overrides.size(); i++) {
            float[] start = {(float) overrides.get(i).startDistance};
            float[] end = {(float) overrides.get(i).endDistance};
            float[] slope = {overrides.get(i).maxSlope};
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
                ImGui.sliderFloat(PlotI18n.tr("plugin.road.slope_start") + "##s", start, 0, edgeLength, RoadUiFormat.SLIDER_METERS);
            } else {
                float sliderWidth = (rowWidth - deleteWidth - spacing * 2.0f) / 2.0f;
                ImGui.setNextItemWidth(sliderWidth);
                ImGui.sliderFloat(PlotI18n.tr("plugin.road.slope_start") + "##s", start, 0, edgeLength, RoadUiFormat.SLIDER_METERS);
            }
            if (ImGui.isItemActivated()) {
                beginSlopeOverrideEditIfNeeded(ctx, edge, overrides);
                overrides = overridesForEdit(edge);
                start[0] = (float) overrides.get(i).startDistance;
                end[0] = (float) overrides.get(i).endDistance;
            }
            if (ImGui.isItemDeactivatedAfterEdit()) {
                editDeactivated = true;
            }
            overrides.get(i).startDistance = start[0];
            if (overrides.get(i).startDistance > overrides.get(i).endDistance) {
                overrides.get(i).endDistance = overrides.get(i).startDistance;
                end[0] = (float) overrides.get(i).endDistance;
            }

            if (stackRangeSliders) {
                ImGui.setNextItemWidth(rowWidth);
                ImGui.sliderFloat(PlotI18n.tr("plugin.road.slope_end") + "##e", end, start[0], edgeLength, RoadUiFormat.SLIDER_METERS);
            } else {
                ImGui.sameLine();
                float sliderWidth = (rowWidth - deleteWidth - spacing * 2.0f) / 2.0f;
                ImGui.setNextItemWidth(sliderWidth);
                ImGui.sliderFloat(PlotI18n.tr("plugin.road.slope_end") + "##e", end, start[0], edgeLength, RoadUiFormat.SLIDER_METERS);
            }
            if (ImGui.isItemActivated()) {
                beginSlopeOverrideEditIfNeeded(ctx, edge, overrides);
                overrides = overridesForEdit(edge);
                start[0] = (float) overrides.get(i).startDistance;
                end[0] = (float) overrides.get(i).endDistance;
            }
            if (ImGui.isItemDeactivatedAfterEdit()) {
                editDeactivated = true;
            }
            overrides.get(i).endDistance = end[0];

            if (!stackRangeSliders) {
                ImGui.sameLine();
            }
            ImGui.pushStyleColor(ImGuiCol.Button, PluginUiColors.DELETE);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, PluginUiColors.DELETE_HOVER);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, PluginUiColors.DELETE_ACTIVE);
            if (ImGui.smallButton(deleteLabel + "##rm")) {
                final int removeIndex = i;
                finishOrCancelSlopeOverrideEdit(ctx);
                List<RoadEdge.SlopeOverride> next = new ArrayList<>(edge.getSlopeOverrides());
                next.remove(removeIndex);
                ctx.networkManager().updateRoadSlopeOverrides(edge.getId(), next);
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
                if (ImGui.isItemActivated()) {
                    beginSlopeOverrideEditIfNeeded(ctx, edge, overrides);
                    overrides = overridesForEdit(edge);
                    slope[0] = overrides.get(i).maxSlope;
                }
            }
            if (ImGui.isItemDeactivatedAfterEdit()) {
                editDeactivated = true;
            }
            overrides.get(i).maxSlope = RoadParameterLimits.clampGradePercent(slope[0]);

            if (RoadStationing.isStationable(network, road)) {
                final double startDistance = overrides.get(i).startDistance;
                final double endDistance = overrides.get(i).endDistance;
                var coordinates = ctx.host().coordinates();
                RoadStationing.stationAt(network, road, edge.getId(), startDistance)
                    .ifPresent(startStation -> RoadStationing.stationAt(network, road, edge.getId(), endDistance)
                        .ifPresent(endStation -> RoadUiWidgets.textWrappedColored(
                            PluginUiColors.HINT_GRAY,
                            PlotI18n.tr(
                                "plugin.road.slope_override_station_hint",
                                RoadLengthDisplay.formatChainage(
                                    chainageDisplay,
                                    startStation.chainageMeters(),
                                    network,
                                    road,
                                    coordinates),
                                RoadLengthDisplay.formatChainage(
                                    chainageDisplay,
                                    endStation.chainageMeters(),
                                    network,
                                    road,
                                    coordinates)))));
            }

            if (overrides.get(i).startDistance > overrides.get(i).endDistance) {
                RoadUiWidgets.textWrappedColored(PluginUiColors.INVALID, PlotI18n.tr("plugin.road.slope_range_invalid"));
            } else if (RoadNetworkManager.hasOverlappingOverride(overrides, i)) {
                RoadUiWidgets.textWrappedColored(PluginUiColors.WARNING_OVERLAP, PlotI18n.tr("plugin.road.slope_range_overlap"));
            }

            ImGui.popID();
        }

        if (slopeOverrideEditEdgeId.equals(edge.getId()) && editDeactivated) {
            ctx.networkManager().commitRoadSlopeOverrides(edge.getId(), overrides);
            resetSlopeOverrideEditSession();
        }

        if (ImGui.button(PlotI18n.tr("plugin.road.add_slope_override"))) {
            finishOrCancelSlopeOverrideEdit(ctx);
            List<RoadEdge.SlopeOverride> next = new ArrayList<>(edge.getSlopeOverrides());
            next.add(new RoadEdge.SlopeOverride(0, (float) edge.getLength(), config.getMaxSlope()));
            ctx.networkManager().updateRoadSlopeOverrides(edge.getId(), next);
        }
    }

    private List<RoadEdge.SlopeOverride> overridesForEdit(RoadEdge edge) {
        if (slopeOverrideEditEdgeId.equals(edge.getId()) && slopeOverrideEditDraft != null) {
            return slopeOverrideEditDraft;
        }
        return new ArrayList<>(edge.getSlopeOverrides());
    }

    private void beginSlopeOverrideEditIfNeeded(
            RoadUiContext ctx,
            RoadEdge edge,
            List<RoadEdge.SlopeOverride> overrides) {
        if (!slopeOverrideEditEdgeId.isEmpty()) {
            return;
        }
        slopeOverrideEditEdgeId = edge.getId();
        slopeOverrideEditDraft = new ArrayList<>(overrides);
        ctx.beginNetworkEdit(RoadChangeKind.GENERATION_SETTINGS);
    }

    /** 切换分段、折叠面板或离开编辑区时提交或取消 Manager 侧未完成的拖动事务。 */
    public void finishOrCancelSlopeOverrideEdit(RoadUiContext ctx) {
        if (slopeOverrideEditEdgeId.isEmpty() || slopeOverrideEditDraft == null) {
            return;
        }
        String edgeId = slopeOverrideEditEdgeId;
        List<RoadEdge.SlopeOverride> draft = slopeOverrideEditDraft;
        resetSlopeOverrideEditSession();
        ctx.networkManager().finishOrCancelRoadSlopeOverrides(edgeId, draft);
    }

    private void resetSlopeOverrideEditSession() {
        slopeOverrideEditEdgeId = "";
        slopeOverrideEditDraft = null;
    }

}
