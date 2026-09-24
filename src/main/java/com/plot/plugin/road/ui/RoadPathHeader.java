package com.plot.plugin.road.ui;

import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;

/** 路径 Tab 顶部：拾取路径、显示开关与路网摘要（对齐建筑轮廓 Footprints Tab）。 */
public final class RoadPathHeader {
    private RoadPathHeader() {
    }

    public static void render(RoadUiContext ctx, RoadAdoptPanel adoptPanel) {
        ctx.toolManager().updateSelectedPaths();

        if (ctx.toolManager().getPathPickSession().isActive()) {
            renderPickSessionState(ctx);
            adoptPanel.renderIntersectionRepairPrompt();
            return;
        }

        if (ImGui.button(
                PlotI18n.tr("plugin.road.pick_path"),
                ImGui.getContentRegionAvailX() * 0.58f,
                0)) {
            ctx.toolManager().activatePathPickTool();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.pick_path_hint"));
        }
        ImGui.sameLine();
        ImGui.checkbox(PlotI18n.tr("plugin.road.overlay.show_paths"), ctx.showRoadOverlay());

        renderProjectStats(ctx);
        adoptPanel.renderIntersectionRepairPrompt();
    }

    private static void renderPickSessionState(RoadUiContext ctx) {
        int count = ctx.toolManager().getPathPickSession().getAccumulatedCount();
        if (count > 0) {
            List<com.plot.core.model.Shape> overlayPaths = ctx.toolManager().getPickOverlayPaths();
            var coordinates = ctx.host().coordinates();
            double totalLength = overlayPaths.stream()
                .mapToDouble(path -> RoadGeometryUtils.calculateWorldPathLength(coordinates, path))
                .sum();
            ImGui.textColored(
                PluginUiColors.STATUS_INFO,
                PlotI18n.tr(
                    "plugin.road.path.picking_summary",
                    count,
                    RoadUiFormat.format(totalLength)));
        } else {
            ImGui.textColored(
                PluginUiColors.STATUS_INFO,
                PlotI18n.tr("plugin.road.adopt_picking_active"));
        }
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.path.picking_hint"));
        if (ImGui.button(PlotI18n.tr("plugin.road.path.cancel_pick") + "##road_path_cancel_pick", 0, 0)) {
            ctx.toolManager().cancelPathPick();
        }
    }

    private static void renderProjectStats(RoadUiContext ctx) {
        RoadNetwork network = ctx.networkManager().getNetwork();
        ImGui.spacing();
        if (network.getEdges().isEmpty()) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.path.empty_hint"));
            return;
        }
        ImGui.text(PlotI18n.tr(
            "plugin.road.path.project_stats",
            network.getRoads().size(),
            RoadUiFormat.format(RoadEdgeListHelper.computeNetworkWorldLength(
                network, ctx.host().coordinates())),
            network.getJunctionCount()));
    }
}
