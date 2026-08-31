package com.plot.plugin.road.ui;

import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.ChainageDisplayMode;
import com.plot.plugin.road.station.RoadStationFormat;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;

import java.util.function.Supplier;

/**
 * 道路设计方向指示：K0+000 在链入口，桩号沿箭头递增。
 * <p>
 * 与 {@link com.plot.plugin.road.station.ChainageDisplayMode}（仅显示格式）无关。
 */
public final class RoadDirectionIndicator {

    private static final float DIAGRAM_HEIGHT = 28f;
    private static final float DOT_RADIUS = 4f;
    private static final float ARROW_HEAD = 7f;

    private RoadDirectionIndicator() {
    }

    public static void render(
            RoadNetwork network,
            Road road,
            Runnable onReverseRoad,
            Supplier<String> statusMessage) {
        if (road == null || network == null) {
            return;
        }

        ImGui.spacing();
        ImGui.text(PlotI18n.tr("plugin.road.design_stack.direction"));

        if (!RoadStationing.isStationable(network, road)) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.road_direction_not_stationable"));
            return;
        }

        double totalLength = RoadStationing.totalLength(network, road);
        String chainStart = RoadStationing.format(0.0, totalLength, RoadStationFormat.KILOMETER_PLUS, ChainageDisplayMode.FROM_START);
        String chainEnd = RoadStationing.format(totalLength, totalLength, RoadStationFormat.KILOMETER_PLUS, ChainageDisplayMode.FROM_START);

        renderDiagram(chainStart, chainEnd);
        renderEntryExitHint(network, road);

        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.road_direction_design_hint"));

        if (ImGui.button(PlotI18n.tr("plugin.road.reverse_road_direction") + "##road_direction_reverse")) {
            if (onReverseRoad != null) {
                onReverseRoad.run();
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.reverse_road_direction"));
        }

        String message = statusMessage != null ? statusMessage.get() : null;
        if (message != null && !message.isBlank()) {
            RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, message);
        }
    }

    private static void renderDiagram(String chainStart, String chainEnd) {
        float avail = ImGui.getContentRegionAvail().x;
        if (avail < 80f) {
            ImGui.text(chainStart + " -> " + chainEnd);
            return;
        }

        float startWidth = ImGui.calcTextSize(chainStart).x;
        float endWidth = ImGui.calcTextSize(chainEnd).x;
        float gap = 10f;
        float lineStart = startWidth + gap + DOT_RADIUS * 2f + 4f;
        float lineEnd = Math.max(lineStart + 40f, avail - endWidth - gap - ARROW_HEAD - 4f);

        ImVec2 cursor = ImGui.getCursorScreenPos();
        float textY = cursor.y;
        float centerY = cursor.y + DIAGRAM_HEIGHT * 0.55f;

        ImDrawList drawList = ImGui.getWindowDrawList();
        int accent = ImGui.getColorU32(ImGuiCol.Text);
        int lineColor = PluginUiColors.ACCENT_BLUE;

        drawList.addText(cursor.x, textY, accent, chainStart);
        float dotX = cursor.x + startWidth + gap + DOT_RADIUS;
        drawList.addCircleFilled(dotX, centerY, DOT_RADIUS, lineColor);
        drawList.addLine(dotX + DOT_RADIUS + 2f, centerY, lineEnd, centerY, lineColor, 2f);
        drawList.addTriangleFilled(
            lineEnd,
            centerY,
            lineEnd - ARROW_HEAD,
            centerY - ARROW_HEAD * 0.55f,
            lineEnd - ARROW_HEAD,
            centerY + ARROW_HEAD * 0.55f,
            lineColor);

        drawList.addText(cursor.x + avail - endWidth, textY, accent, chainEnd);
        ImGui.dummy(avail, DIAGRAM_HEIGHT);
    }

    private static void renderEntryExitHint(RoadNetwork network, Road road) {
        String entry = RoadStationing.chainEntryNodeId(network, road)
            .map(nodeId -> RoadEdgeListHelper.formatNodeLabel(network, nodeId))
            .orElse("-");
        String exit = RoadStationing.chainExitNodeId(network, road)
            .map(nodeId -> RoadEdgeListHelper.formatNodeLabel(network, nodeId))
            .orElse("-");
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.road_direction_entry_exit", entry, exit));
    }
}
