package com.plot.plugin.road.ui;

import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.ImVec2;

import java.util.LinkedHashSet;

/** PATH / EDIT / GENERATE 公共对象上下文条。 */
public final class RoadShellContextStrip {
    private RoadShellContextStrip() {
    }

    public static void render(RoadUiContext ctx) {
        RoadNetwork network = ctx.networkManager().getNetwork();
        LinkedHashSet<String> roadIds = ctx.networkManager().getSelectedRoadIds();
        float width = ImGui.getContentRegionAvail().x;
        ImVec2 origin = ImGui.getCursorScreenPos();
        float height = ImGui.getFrameHeightWithSpacing() * (roadIds.size() <= 1 ? 2.2f : 2.0f);
        var drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(
            origin.x, origin.y, origin.x + width, origin.y + height,
            PluginUiColors.PANEL_BG_DARK);
        drawList.addRect(
            origin.x, origin.y, origin.x + width, origin.y + height,
            PluginUiColors.PANEL_BORDER);

        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowPadding, 8f, 6f);
        if (ImGui.beginChild("##road_shell_context", width, height, false)) {
            if (roadIds.isEmpty()) {
                ImGui.textColored(
                    PluginUiColors.HINT_GRAY,
                    PlotI18n.tr("plugin.road.shell.no_selection"));
            } else if (roadIds.size() == 1) {
                renderSingle(ctx, network, ctx.networkManager().getPrimarySelectedRoad());
            } else {
                renderMulti(roadIds.size());
            }
            ImGui.endChild();
        }
        ImGui.popStyleVar();
        ImGui.spacing();
    }

    private static void renderSingle(RoadUiContext ctx, RoadNetwork network, Road road) {
        if (road == null) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.shell.no_selection"));
            return;
        }
        ImGui.text(RoadEdgeListHelper.formatRoadLabel(network, road));
        double length = RoadStationing.isStationable(network, road)
            ? RoadStationing.canonicalLength(network, road)
            : RoadEdgeListHelper.computeRoadWorldLength(network, road, ctx.host().coordinates());
        int issueCount = RoadEditDiagnosticsBanner.countIssues(ctx, network, road);
        String stats = PlotI18n.tr(
            "plugin.road.shell.single_stats",
            road.getSegmentIds().size(),
            length);
        if (issueCount > 0) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                stats + " · "
                    + PlotI18n.tr("plugin.road.edit.overview_warnings", issueCount));
        } else {
            ImGui.textColored(PluginUiColors.HINT_GRAY, stats);
        }
    }

    private static void renderMulti(int count) {
        ImGui.textColored(
            PluginUiColors.INFO_BLUE,
            PlotI18n.tr("plugin.road.batch_edit_title", count));
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.batch_edit_shared_only"));
    }
}
