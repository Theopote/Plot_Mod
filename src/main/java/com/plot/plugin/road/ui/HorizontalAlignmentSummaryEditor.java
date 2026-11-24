package com.plot.plugin.road.ui;

import com.plot.plugin.road.alignment.HorizontalAlignmentCenterlineConsistency;
import com.plot.plugin.road.alignment.HorizontalAlignmentCenterlineMaterializer;
import com.plot.plugin.road.alignment.HorizontalAlignmentGeometry;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.centerline.CenterlineEditResult;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.ChainageDisplayContext;
import com.plot.plugin.road.station.RoadStationDataTransforms;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.component.UIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/**
 * 平面线形摘要、物化与「从折线拟合」入口。
 */
public final class HorizontalAlignmentSummaryEditor {

    private String lastHorizontalAlignmentMessage = "";

    public void clearMessages() {
        lastHorizontalAlignmentMessage = "";
    }

    public void render(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            ChainageDisplayContext chainageDisplay) {
        render(ctx, network, road, chainageDisplay, null);
    }

    /**
     * @param beforeMutationOrNull 写回工作副本前的检查点回调（会话 Undo）；可为 null
     */
    public void render(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            ChainageDisplayContext chainageDisplay,
            Runnable beforeMutationOrNull) {
        RoadHorizontalAlignment alignment = road.getHorizontalAlignment();
        boolean hasHa = alignment != null && !alignment.isEmpty();
        if (!hasHa) {
            renderEmptyState(network, road, beforeMutationOrNull);
            return;
        }
        var coordinates = ctx.host().coordinates();
        double canvasTotal = RoadStationing.canonicalLength(network, road);
        ChainageDisplayContext worldChainage = RoadLengthDisplay.worldChainageContext(
            chainageDisplay, network, road, coordinates);
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.horizontal_alignment_relation_hint"));
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr(
                "plugin.road.horizontal_alignment_length",
                alignment.getElements().size(),
                RoadLengthDisplay.formatCanvasMeters(canvasTotal, network, road, coordinates),
                worldChainage.format(0.0),
                worldChainage.format(
                    RoadLengthDisplay.canvasToBlocks(canvasTotal, network, road, coordinates))));
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
                        RoadLengthDisplay.formatCanvasMeters(
                            consistency.maxDeviationMeters(), network, road, coordinates),
                        RoadLengthDisplay.formatCanvasMeters(
                            consistency.meanDeviationMeters(), network, road, coordinates)));
                if (!consistency.lengthMatches()) {
                    RoadUiWidgets.textWrappedColored(
                        PluginUiColors.WARNING,
                        PlotI18n.tr(
                            "plugin.road.horizontal_alignment_length_mismatch_hint",
                            RoadLengthDisplay.formatCanvasMeters(
                                consistency.roadLengthMeters(), network, road, coordinates),
                            RoadLengthDisplay.formatCanvasMeters(
                                consistency.alignmentLengthMeters(), network, road, coordinates)));
                }
            }
        }
        if (!lastHorizontalAlignmentMessage.isBlank()) {
            RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, lastHorizontalAlignmentMessage);
        }
        if (RoadStationing.isStationable(network, road)
            && HorizontalAlignmentCenterlineMaterializer.canMaterialize(network, road)) {
            if (ImGui.button(PlotI18n.tr("plugin.road.horizontal_alignment_materialize") + "##ha_mat")) {
                pushCheckpoint(beforeMutationOrNull);
                CenterlineEditResult result =
                    HorizontalAlignmentCenterlineMaterializer.materialize(network, road);
                lastHorizontalAlignmentMessage = CenterlineEditMessages.format(result);
            }
            if (ImGui.isItemHovered()) {
                UIUtils.setTooltip(PlotI18n.tr("plugin.road.horizontal_alignment_materialize_hint"));
            }
            ImGui.sameLine();
        }
        if (RoadStationing.isStationable(network, road)
            && ImGui.button(PlotI18n.tr("plugin.road.horizontal_alignment_fit_from_polyline") + "##ha_refit")) {
            fitFromPolyline(network, road, beforeMutationOrNull);
        }
        if (ImGui.isItemHovered()) {
            UIUtils.setTooltip(PlotI18n.tr("plugin.road.horizontal_alignment_fit_from_polyline_tip"));
        }
        double scale = RoadLengthDisplay.canvasToWorldScale(network, road, coordinates);
        int index = 0;
        for (com.plot.plugin.road.alignment.HorizontalAlignmentElement element : alignment.getElements()) {
            double startCanvas = HorizontalAlignmentGeometry.elementStartChainage(alignment, index++);
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                HorizontalAlignmentGeometry.describeElement(
                    element, startCanvas, worldChainage, scale));
        }
    }

    private void renderEmptyState(
            RoadNetwork network,
            Road road,
            Runnable beforeMutationOrNull) {
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.horizontal_alignment_none"));
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.horizontal_alignment_relation_hint"));
        if (!lastHorizontalAlignmentMessage.isBlank()) {
            RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, lastHorizontalAlignmentMessage);
        }
        if (!RoadStationing.isStationable(network, road)) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.horizontal_alignment_fit_not_stationable"));
            return;
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.horizontal_alignment_fit_from_polyline") + "##ha_fit")) {
            fitFromPolyline(network, road, beforeMutationOrNull);
        }
        if (ImGui.isItemHovered()) {
            UIUtils.setTooltip(PlotI18n.tr("plugin.road.horizontal_alignment_fit_from_polyline_tip"));
        }
    }

    private void fitFromPolyline(RoadNetwork network, Road road, Runnable beforeMutationOrNull) {
        pushCheckpoint(beforeMutationOrNull);
        RoadStationDataTransforms.refitHorizontalAlignmentFromCenterline(network, road);
        RoadHorizontalAlignment fitted = road.getHorizontalAlignment();
        if (fitted != null && !fitted.isEmpty()) {
            lastHorizontalAlignmentMessage = PlotI18n.tr(
                "plugin.road.horizontal_alignment_fit_success",
                fitted.getElements().size());
        } else {
            lastHorizontalAlignmentMessage = PlotI18n.tr("plugin.road.horizontal_alignment_fit_failed");
        }
    }

    private static void pushCheckpoint(Runnable beforeMutationOrNull) {
        if (beforeMutationOrNull != null) {
            beforeMutationOrNull.run();
        }
    }
}
