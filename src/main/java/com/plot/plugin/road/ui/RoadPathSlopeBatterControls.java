package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.manager.RoadChangeKind;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 路径 Tab：按所选道路编辑边坡最大水平跑距；多选值不一致时滑条显示「混合」。
 */
final class RoadPathSlopeBatterControls {

    private boolean dragActive;
    private boolean mixed;
    private int draftValue = RoadParameterLimits.DEFAULT_SLOPE_BATTER_RUN_BLOCKS;
    private String selectionKey = "";

    void render(RoadUiContext ctx) {
        RoadSelectionHeader.Mode mode = RoadSelectionHeader.resolveMode(ctx);
        if (mode == RoadSelectionHeader.Mode.NONE) {
            endDragIfNeeded(ctx, false);
            return;
        }
        LinkedHashSet<String> selectedIds = ctx.networkManager().getSelectedRoadIds();
        if (selectedIds == null || selectedIds.isEmpty()) {
            endDragIfNeeded(ctx, false);
            return;
        }
        RoadNetwork network = ctx.networkManager().getNetwork();
        RoadSystemConfig config = ctx.networkManager().getConfig();
        if (network == null || config == null) {
            endDragIfNeeded(ctx, false);
            return;
        }

        List<Road> roads = new ArrayList<>();
        for (String roadId : selectedIds) {
            Road road = network.getRoad(roadId);
            if (road != null) {
                roads.add(road);
            }
        }
        if (roads.isEmpty()) {
            endDragIfNeeded(ctx, false);
            return;
        }

        String key = String.join(",", selectedIds);
        if (!key.equals(selectionKey)) {
            selectionKey = key;
            endDragIfNeeded(ctx, false);
            syncDraftFromRoads(roads, config);
        }

        ImGui.spacing();
        RoadUiSections.section("plugin.road.overlay.slope_batter_distance");
        if (mixed && !dragActive) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.overlay.slope_batter_distance_mixed_hint"));
        }

        // 标签不得含变化数值：ImGui 用整段 label 算 ID，数字一变控件 ID 就变。
        String distanceLabel = PlotI18n.tr("plugin.road.overlay.slope_batter_distance");
        String format = mixed && !dragActive
            ? PlotI18n.tr("plugin.road.overlay.slope_batter_distance_mixed")
            : "%d";
        int[] runBlocks = {draftValue};
        float sliderWidth = Math.clamp(ImGui.getContentRegionAvailX() * 0.55f, 140f, 280f);
        ImGui.setNextItemWidth(sliderWidth);
        boolean changed = ImGui.sliderInt(
            distanceLabel + "##path_road_slope_batter_run",
            runBlocks,
            RoadParameterLimits.MIN_SLOPE_BATTER_RUN_BLOCKS,
            RoadParameterLimits.MAX_SLOPE_BATTER_RUN_BLOCKS,
            format);
        if (ImGui.isItemActivated()) {
            beginDrag(ctx);
        }
        if (changed) {
            if (!dragActive) {
                beginDrag(ctx);
            }
            draftValue = RoadParameterLimits.clampSlopeBatterRunBlocks(runBlocks[0]);
            mixed = false;
            if (dragActive) {
                applyDraftToRoads(roads, draftValue);
                // 拖动中每帧刷新叠加，边坡虚线实时跟手。
                ctx.requestOverlayRefresh();
            }
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            endDragIfNeeded(ctx, true);
            syncDraftFromRoads(roads, config);
        } else if (ImGui.isItemDeactivated()) {
            endDragIfNeeded(ctx, false);
            syncDraftFromRoads(roads, config);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.overlay.slope_batter_distance_hint"));
        }
    }

    private void syncDraftFromRoads(List<Road> roads, RoadSystemConfig config) {
        Integer shared = null;
        boolean anyMixed = false;
        for (Road road : roads) {
            int value = effectiveMaxRun(road, config);
            if (shared == null) {
                shared = value;
            } else if (shared != value) {
                anyMixed = true;
            }
        }
        draftValue = shared != null
            ? shared
            : config.getSlopeBatterMaxRunBlocks();
        mixed = anyMixed;
    }

    private static int effectiveMaxRun(Road road, RoadSystemConfig config) {
        if (road == null) {
            return config.getSlopeBatterMaxRunBlocks();
        }
        Integer override = road.getCrossSection().getSlopeBatter().getMaxRunBlocks();
        if (override != null) {
            return RoadParameterLimits.clampSlopeBatterRunBlocks(override);
        }
        return ResolvedCrossSection.resolve(road.getCrossSection(), config).slopeBatterMaxRunBlocks;
    }

    private static void applyDraftToRoads(List<Road> roads, int value) {
        int clamped = RoadParameterLimits.clampSlopeBatterRunBlocks(value);
        for (Road road : roads) {
            road.getCrossSection().getSlopeBatter().setMaxRunBlocks(clamped);
        }
    }

    private void beginDrag(RoadUiContext ctx) {
        if (dragActive) {
            return;
        }
        if (ctx.networkManager().hasPendingNetworkEdit()) {
            return;
        }
        ctx.beginNetworkEdit(RoadChangeKind.CROSS_SECTION);
        dragActive = true;
    }

    private void endDragIfNeeded(RoadUiContext ctx, boolean commit) {
        if (!dragActive) {
            return;
        }
        dragActive = false;
        if (!ctx.networkManager().hasPendingNetworkEdit()) {
            return;
        }
        if (commit) {
            ctx.finishNetworkEdit();
        } else {
            ctx.networkManager().cancelPendingNetworkEdit();
        }
    }
}
