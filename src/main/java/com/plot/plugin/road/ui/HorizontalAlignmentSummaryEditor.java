package com.plot.plugin.road.ui;

import com.plot.plugin.road.alignment.HorizontalAlignmentCenterlineConsistency;
import com.plot.plugin.road.alignment.HorizontalAlignmentCenterlineMaterializer;
import com.plot.plugin.road.alignment.HorizontalAlignmentGeometry;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.centerline.CenterlineEditResult;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.ChainageDisplayContext;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/**
 * 平面线形摘要与物化入口。
 */
public final class HorizontalAlignmentSummaryEditor {

    private String lastHorizontalAlignmentMessage = "";

    public void render(
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
        var coordinates = ctx.host().coordinates();
        double canvasTotal = RoadStationing.canonicalLength(network, road);
        ChainageDisplayContext worldChainage = RoadLengthDisplay.worldChainageContext(
            chainageDisplay, network, road, coordinates);
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
                // 始终对传入的 network 物化（浮动编辑器为 workingNetwork），
                // 禁止经 Manager 写入 live，以免绕过窗口级事务。
                CenterlineEditResult result =
                    HorizontalAlignmentCenterlineMaterializer.materialize(network, road);
                lastHorizontalAlignmentMessage = CenterlineEditMessages.format(result);
            }
            ImGui.sameLine();
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.horizontal_alignment_materialize_hint"));
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
}
