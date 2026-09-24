package com.plot.plugin.road.ui;

import com.plot.plugin.road.alignment.HorizontalAlignmentCenterlineConsistency;
import com.plot.plugin.road.alignment.HorizontalAlignmentCenterlineMaterializer;
import com.plot.plugin.road.alignment.HorizontalAlignmentGeometry;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.centerline.CenterlineEditResult;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.ChainageDisplayContext;
import com.plot.plugin.road.station.RoadStationFormat;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/**
 * 平面线形摘要与物化入口。
 */
final class HorizontalAlignmentSummaryEditor {

    private String lastHorizontalAlignmentMessage = "";

    void render(
            RoadUiContext ctx,
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
                    lastHorizontalAlignmentMessage = CenterlineEditMessages.format(result);
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
