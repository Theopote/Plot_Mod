package com.plot.plugin.road.ui;

import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.LinkedHashSet;

/** 道路插件统一的当前选择头部（路线 / 样式 / 建造）。 */
public final class RoadSelectionHeader {
    public enum Mode {
        NONE,
        SINGLE,
        MULTI
    }

    private RoadSelectionHeader() {
    }

    public static Mode resolveMode(RoadUiContext ctx) {
        LinkedHashSet<String> roadIds = ctx.networkManager().getSelectedRoadIds();
        if (roadIds.isEmpty()) {
            return Mode.NONE;
        }
        return roadIds.size() == 1 ? Mode.SINGLE : Mode.MULTI;
    }

    public static void render(RoadUiContext ctx) {
        RoadNetwork network = ctx.networkManager().getNetwork();
        LinkedHashSet<String> roadIds = ctx.networkManager().getSelectedRoadIds();
        ImGui.text(PlotI18n.tr("plugin.road.selection.title"));
        ImGui.spacing();

        if (roadIds.isEmpty()) {
            if (network.getEdges().isEmpty()) {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.HINT_GRAY,
                    PlotI18n.tr("plugin.road.selection.none_empty_network"));
            } else {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.HINT_GRAY,
                    PlotI18n.tr("plugin.road.selection.none"));
            }
            return;
        }

        if (roadIds.size() == 1) {
            Road road = ctx.networkManager().getPrimarySelectedRoad();
            if (road == null) {
                return;
            }
            ImGui.bulletText(RoadEdgeListHelper.formatRoadLabel(network, road));
            double length = RoadEdgeListHelper.computeRoadWorldLength(
                network, road, ctx.host().coordinates());
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.selection.single_length", length));
            return;
        }

        ImGui.textColored(
            PluginUiColors.INFO_BLUE,
            PlotI18n.tr("plugin.road.selection.multi_summary", roadIds.size()));
        Road primary = ctx.networkManager().getPrimarySelectedRoad();
        if (primary != null) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr(
                    "plugin.road.selection.primary",
                    RoadEdgeListHelper.formatRoadLabel(network, primary)));
        }
        double totalLength = 0.0;
        for (String roadId : roadIds) {
            Road road = network.getRoad(roadId);
            if (road != null) {
                totalLength += RoadEdgeListHelper.computeRoadWorldLength(
                    network, road, ctx.host().coordinates());
            }
        }
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.selection.multi_length", totalLength));
        ImGui.spacing();
        for (String roadId : roadIds) {
            Road road = network.getRoad(roadId);
            if (road != null) {
                ImGui.bulletText(RoadEdgeListHelper.formatRoadLabel(network, road));
            }
        }
    }
}
