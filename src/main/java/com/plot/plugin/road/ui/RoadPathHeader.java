package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadAdoptDuplicateDetector;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.crossing.RoadIntersectionInventory;
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

        renderPickAndOverlayControls(ctx);

        renderProjectStats(ctx);
        adoptPanel.renderIntersectionRepairPrompt();
    }

    /**
     * 「选择路径 / 显示道路路径 / 显示边坡宽度 / 边坡距离」按可用宽度自动折行，
     * 边坡开关与距离滑动条紧跟路径显示开关之后。
     */
    private static void renderPickAndOverlayControls(RoadUiContext ctx) {
        String pickLabel = PlotI18n.tr("plugin.road.pick_path");
        String pathsLabel = PlotI18n.tr("plugin.road.overlay.show_paths");
        String slopeLabel = PlotI18n.tr("plugin.road.overlay.show_slope_batter");

        float avail = ImGui.getContentRegionAvailX();
        float spacing = ImGui.getStyle().getItemSpacingX();
        float used = 0f;

        float pickWidth = ImGui.calcTextSize(pickLabel).x + ImGui.getStyle().getFramePaddingX() * 2f;
        if (ImGui.button(pickLabel, 0, 0)) {
            ctx.toolManager().activatePathPickTool();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.pick_path_hint"));
        }
        used = pickWidth;

        float pathsWidth = checkboxWidth(pathsLabel);
        if (used + spacing + pathsWidth <= avail) {
            ImGui.sameLine(0f, spacing);
            used += spacing + pathsWidth;
        } else {
            used = pathsWidth;
        }
        if (ImGui.checkbox(pathsLabel, ctx.showRoadOverlay())) {
            ctx.requestOverlayRefresh();
        }

        float slopeWidth = checkboxWidth(slopeLabel);
        if (used + spacing + slopeWidth <= avail) {
            ImGui.sameLine(0f, spacing);
            used += spacing + slopeWidth;
        } else {
            used = slopeWidth;
        }
        if (ImGui.checkbox(slopeLabel, ctx.showSlopeBatterOverlay())) {
            ctx.requestOverlayRefresh();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.overlay.show_slope_batter_hint"));
        }

        renderSlopeBatterDistanceSlider(ctx, avail, spacing, used);
    }

    private static void renderSlopeBatterDistanceSlider(
            RoadUiContext ctx, float avail, float spacing, float used) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        if (config == null) {
            return;
        }
        int[] runBlocks = {config.getSlopeBatterMaxRunBlocks()};
        String distanceLabel = PlotI18n.tr(
            "plugin.road.overlay.slope_batter_distance", runBlocks[0]);
        float sliderWidth = Math.min(220f, Math.max(120f, avail * 0.35f));
        float labelWidth = ImGui.calcTextSize(distanceLabel).x;
        float totalWidth = labelWidth + ImGui.getStyle().getItemInnerSpacingX() + sliderWidth;
        if (used + spacing + totalWidth <= avail) {
            ImGui.sameLine(0f, spacing);
        }
        ImGui.setNextItemWidth(sliderWidth);
        // 拖动时只写入配置；松手后再 invalidate 预览/叠加层，避免每帧全量重算导致卡顿。
        if (ImGui.sliderInt(
                distanceLabel + "##path_slope_batter_run",
                runBlocks,
                RoadParameterLimits.MIN_SLOPE_BATTER_RUN_BLOCKS,
                RoadParameterLimits.MAX_SLOPE_BATTER_RUN_BLOCKS,
                "%d")) {
            config.setSlopeBatterMaxRunBlocks(runBlocks[0]);
            config.markCustom();
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            ctx.onGenerationConfigChanged();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.overlay.slope_batter_distance_hint"));
        }
    }

    private static float checkboxWidth(String label) {
        return ImGui.getFrameHeight()
            + ImGui.getStyle().getItemInnerSpacingX()
            + ImGui.calcTextSize(label).x;
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
        renderDuplicatePathHint(ctx);
        if (ImGui.button(PlotI18n.tr("plugin.road.path.cancel_pick") + "##road_path_cancel_pick", 0, 0)) {
            ctx.toolManager().cancelPathPick();
        }
    }

    private static void renderDuplicatePathHint(RoadUiContext ctx) {
        RoadNetwork network = ctx.networkManager().getNetwork();
        if (network.getEdges().isEmpty()) {
            return;
        }
        long duplicateCount = ctx.toolManager().getPickOverlayPaths().stream()
            .map(RoadGeometryUtils::extractShapePoints)
            .filter(points -> RoadAdoptDuplicateDetector.overlapsExistingPath(network, points))
            .count();
        if (duplicateCount <= 0) {
            return;
        }
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.WARNING,
            PlotI18n.tr("plugin.road.adopt_duplicate_path_hint", duplicateCount));
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
            RoadIntersectionInventory.count(network)));
    }
}
