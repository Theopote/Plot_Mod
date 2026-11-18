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

    private boolean historyPushed;
    private boolean mixed;
    private int draftValue = RoadParameterLimits.DEFAULT_SLOPE_BATTER_RUN_BLOCKS;
    private String selectionKey = "";

    void render(RoadUiContext ctx) {
        RoadSelectionHeader.Mode mode = RoadSelectionHeader.resolveMode(ctx);
        if (mode == RoadSelectionHeader.Mode.NONE) {
            historyPushed = false;
            return;
        }
        LinkedHashSet<String> selectedIds = ctx.networkManager().getSelectedRoadIds();
        if (selectedIds == null || selectedIds.isEmpty()) {
            historyPushed = false;
            return;
        }
        RoadNetwork network = ctx.networkManager().getNetwork();
        RoadSystemConfig config = ctx.networkManager().getConfig();
        if (network == null || config == null) {
            historyPushed = false;
            return;
        }

        List<Road> roads = resolveSelectedRoads(network, selectedIds);
        if (roads.isEmpty()) {
            historyPushed = false;
            return;
        }

        String key = String.join(",", selectedIds);
        if (!key.equals(selectionKey)) {
            selectionKey = key;
            historyPushed = false;
            syncDraftFromRoads(roads, config);
        }

        ImGui.spacing();
        RoadUiSections.section("plugin.road.overlay.slope_batter_distance");
        if (mixed && !historyPushed) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.overlay.slope_batter_distance_mixed_hint"));
        }
        if (!ctx.showSlopeBatterOverlay().get()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.overlay.slope_batter_distance_preview_hint"));
        }

        // 标签不得含变化数值：ImGui 用整段 label 算 ID，数字一变控件 ID 就变。
        String distanceLabel = PlotI18n.tr("plugin.road.overlay.slope_batter_distance");
        String format = mixed && !historyPushed
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
        if (changed) {
            if (!historyPushed) {
                // 推入拖动前快照并提交为一步撤销；拖动中只改 live + 刷叠加。
                ctx.networkManager().pushHistory(RoadChangeKind.CROSS_SECTION);
                historyPushed = true;
            }
            draftValue = RoadParameterLimits.clampSlopeBatterRunBlocks(runBlocks[0]);
            mixed = false;
            applyDraftToRoads(ctx.networkManager().getNetwork(), selectedIds, draftValue);
            ctx.requestOverlayRefresh();
        }
        if (ImGui.isItemDeactivated()) {
            historyPushed = false;
            List<Road> liveRoads = resolveSelectedRoads(ctx.networkManager().getNetwork(), selectedIds);
            syncDraftFromRoads(liveRoads, config);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.overlay.slope_batter_distance_hint"));
        }
    }

    private static List<Road> resolveSelectedRoads(RoadNetwork network, LinkedHashSet<String> selectedIds) {
        List<Road> roads = new ArrayList<>();
        if (network == null || selectedIds == null) {
            return roads;
        }
        for (String roadId : selectedIds) {
            Road road = network.getRoad(roadId);
            if (road != null) {
                roads.add(road);
            }
        }
        return roads;
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

    private static void applyDraftToRoads(
            RoadNetwork network,
            LinkedHashSet<String> selectedIds,
            int value) {
        if (network == null || selectedIds == null) {
            return;
        }
        int clamped = RoadParameterLimits.clampSlopeBatterRunBlocks(value);
        for (String roadId : selectedIds) {
            Road road = network.getRoad(roadId);
            if (road != null) {
                road.getCrossSection().getSlopeBatter().setMaxRunBlocks(clamped);
            }
        }
    }
}
