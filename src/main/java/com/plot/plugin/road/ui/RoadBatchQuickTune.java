package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.Collection;

/** 多选 Edit：宽度与车道数快速批量调节。 */
final class RoadBatchQuickTune {
    private RoadBatchQuickTune() {
    }

    static void render(RoadUiContext ctx, Collection<String> roadIds, Runnable onHistory) {
        if (roadIds == null || roadIds.isEmpty()) {
            return;
        }
        RoadNetwork network = ctx.networkManager().getNetwork();
        RoadSystemConfig config = ctx.networkManager().getConfig();
        WidthLaneState state = resolveWidthLane(network, roadIds, config);
        RoadNetworkManager.BatchEditDefaults draft = ctx.networkManager().loadBatchEditDefaults();

        RoadUiSections.section("plugin.road.edit.quick_tune");
        renderWidthField(ctx, draft, state, onHistory);
        renderLaneField(ctx, draft, state, onHistory);

        if (ImGui.button(PlotI18n.tr("plugin.road.apply_batch"), ImGui.getContentRegionAvailX(), 0)) {
            ctx.networkManager().applyBatchEdit(ctx.networkManager().currentBatchEditDefaults());
        }
        ImGui.spacing();
    }

    private static void renderWidthField(
            RoadUiContext ctx,
            RoadNetworkManager.BatchEditDefaults draft,
            WidthLaneState state,
            Runnable onHistory) {
        if (state.mixedWidth) {
            ImGui.text(PlotI18n.tr("plugin.road.edit.batch_width"));
            ImGui.sameLine();
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.vertical_batch_strategy_mixed"));
            return;
        }
        int[] widthArr = {draft.width()};
        if (ImGui.sliderInt(
            PlotI18n.tr("plugin.road.road_width", widthArr[0]) + "##batch_quick_width",
            widthArr,
            RoadParameterLimits.MIN_CARRIAGEWAY_WIDTH,
            RoadParameterLimits.MAX_CARRIAGEWAY_WIDTH,
            "%d")) {
            if (ImGui.isItemActivated() && onHistory != null) {
                onHistory.run();
            }
            ctx.networkManager().updateBatchEditDraft(draft.withWidth(widthArr[0]));
        }
    }

    private static void renderLaneField(
            RoadUiContext ctx,
            RoadNetworkManager.BatchEditDefaults draft,
            WidthLaneState state,
            Runnable onHistory) {
        if (state.mixedLaneCount) {
            ImGui.text(PlotI18n.tr("plugin.road.edit.batch_lane_count"));
            ImGui.sameLine();
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.vertical_batch_strategy_mixed"));
            return;
        }
        int[] laneArr = {draft.laneCount()};
        if (ImGui.sliderInt(
            PlotI18n.tr("plugin.road.lane_count", laneArr[0]) + "##batch_quick_lanes",
            laneArr,
            RoadParameterLimits.MIN_LANE_COUNT,
            RoadParameterLimits.MAX_LANE_COUNT,
            "%d")) {
            if (ImGui.isItemActivated() && onHistory != null) {
                onHistory.run();
            }
            ctx.networkManager().updateBatchEditDraft(draft.withLaneCount(laneArr[0]));
        }
    }

    private static WidthLaneState resolveWidthLane(
            RoadNetwork network,
            Collection<String> roadIds,
            RoadSystemConfig config) {
        Integer sharedWidth = null;
        Integer sharedLanes = null;
        boolean mixedWidth = false;
        boolean mixedLaneCount = false;
        for (String roadId : roadIds) {
            Road road = network.getRoad(roadId);
            if (road == null) {
                continue;
            }
            int width = road.getWidth() != null ? road.getWidth() : config.getRoadWidth();
            int lanes = road.getCrossSection().getCarriageway().getEffectiveLaneCount();
            if (sharedWidth == null) {
                sharedWidth = width;
            } else if (sharedWidth != width) {
                mixedWidth = true;
            }
            if (sharedLanes == null) {
                sharedLanes = lanes;
            } else if (sharedLanes != lanes) {
                mixedLaneCount = true;
            }
        }
        return new WidthLaneState(mixedWidth, mixedLaneCount);
    }

    private record WidthLaneState(boolean mixedWidth, boolean mixedLaneCount) {
    }
}
