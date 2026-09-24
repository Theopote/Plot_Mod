package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.ChainageDisplayContext;
import com.plot.plugin.road.station.RoadStationFormat;
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
final class RoadSegmentEditor {

    void renderSegmentList(RoadUiContext ctx, RoadNetwork network, Road road) {
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

    void renderSegmentSummary(
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

    void renderElevationHint(RoadUiContext ctx, RoadEdge edge) {
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

    void renderSlopeOverrides(
            RoadUiContext ctx,
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
                final int removeIndex = i;
                ctx.editNetwork(() -> {
                    overrides.remove(removeIndex);
                    edge.setSlopeOverrides(overrides);
                });
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
            ctx.editNetwork(() -> {
                overrides.add(new RoadEdge.SlopeOverride(0, (float) edge.getLength(), config.getMaxSlope()));
                edge.setSlopeOverrides(overrides);
            });
        }
    }

    private static String formatChainage(ChainageDisplayContext display, double chainageMeters) {
        if (display != null) {
            return display.format(chainageMeters);
        }
        return RoadStationing.format(chainageMeters, RoadStationFormat.KILOMETER_PLUS);
    }
}
