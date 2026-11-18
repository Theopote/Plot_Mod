package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.manager.RoadBatchEditField;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.Collection;
import java.util.Set;

/** 多选 Edit：单车道宽与车道数快速批量调节。 */
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
        Set<RoadBatchEditField> dirty = ctx.networkManager().batchEditDirtyFields();

        RoadUiSections.section("plugin.road.edit.quick_tune");
        renderLaneWidthField(ctx, draft, state, dirty, onHistory);
        renderLaneField(ctx, draft, state, dirty, onHistory);

        RoadBatchCrossSectionEditor.renderApplyButton(
            ctx,
            ctx.networkManager().currentBatchEditDefaults());
        ImGui.spacing();
    }

    private static void renderLaneWidthField(
            RoadUiContext ctx,
            RoadNetworkManager.BatchEditDefaults draft,
            WidthLaneState state,
            Set<RoadBatchEditField> dirty,
            Runnable onHistory) {
        boolean mixedUnset = state.mixedLaneWidth && !dirty.contains(RoadBatchEditField.WIDTH);
        if (state.mixedLaneWidth) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.edit.batch_lane_width_mixed_hint"));
        }
        // Batch defaults still store derived carriageway width; map slider to inferred uniform lane width.
        int uniform = Math.clamp(
                draft.width() / Math.max(1, draft.laneCount()),
                RoadParameterLimits.MIN_LANE_WIDTH_BLOCKS,
                RoadParameterLimits.MAX_LANE_WIDTH_BLOCKS);
        int[] laneWidthArr = {uniform};
        String label = mixedUnset
            ? PlotI18n.tr("plugin.road.edit.batch_lane_width_set_unified")
            : state.mixedLaneWidth
                ? PlotI18n.tr("plugin.road.edit.batch_lane_width_target", laneWidthArr[0])
                : PlotI18n.tr("plugin.road.lane_travel_width");
        String format = mixedUnset
            ? PlotI18n.tr("plugin.road.edit.batch_target_unset")
            : "%d";
        if (ImGui.sliderInt(
            label + "##batch_quick_lane_w",
            laneWidthArr,
            RoadParameterLimits.MIN_LANE_WIDTH_BLOCKS,
            RoadParameterLimits.MAX_LANE_WIDTH_BLOCKS,
            format)) {
            if (ImGui.isItemActivated() && onHistory != null) {
                onHistory.run();
            }
            int derived = laneWidthArr[0] * Math.max(1, draft.laneCount());
            ctx.networkManager().updateBatchEditDraft(draft.withWidth(derived));
        }
    }

    private static void renderLaneField(
            RoadUiContext ctx,
            RoadNetworkManager.BatchEditDefaults draft,
            WidthLaneState state,
            Set<RoadBatchEditField> dirty,
            Runnable onHistory) {
        boolean mixedUnset = state.mixedLaneCount && !dirty.contains(RoadBatchEditField.LANE_COUNT);
        if (state.mixedLaneCount) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.edit.batch_lane_count_mixed_hint"));
        }
        int[] laneArr = {draft.laneCount()};
        String label = mixedUnset
            ? PlotI18n.tr("plugin.road.edit.batch_lane_count_set_unified")
            : state.mixedLaneCount
                ? PlotI18n.tr("plugin.road.edit.batch_lane_count_target", laneArr[0])
                : PlotI18n.tr("plugin.road.lane_count");
        String format = mixedUnset
            ? PlotI18n.tr("plugin.road.edit.batch_target_unset")
            : "%d";
        if (ImGui.sliderInt(
            label + "##batch_quick_lanes",
            laneArr,
            RoadParameterLimits.MIN_LANE_COUNT,
            RoadParameterLimits.MAX_LANE_COUNT,
            format)) {
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
        Integer sharedLaneWidth = null;
        Integer sharedLanes = null;
        boolean mixedLaneWidth = false;
        boolean mixedLaneCount = false;
        for (String roadId : roadIds) {
            Road road = network.getRoad(roadId);
            if (road == null) {
                continue;
            }
            int laneWidth = road.getCrossSection().getCarriageway().getEffectiveDefaultLaneWidth();
            int lanes = road.getCrossSection().getCarriageway().getEffectiveLaneCount();
            if (sharedLaneWidth == null) {
                sharedLaneWidth = laneWidth;
            } else if (sharedLaneWidth != laneWidth) {
                mixedLaneWidth = true;
            }
            if (sharedLanes == null) {
                sharedLanes = lanes;
            } else if (sharedLanes != lanes) {
                mixedLaneCount = true;
            }
        }
        return new WidthLaneState(mixedLaneWidth, mixedLaneCount);
    }

    private record WidthLaneState(boolean mixedLaneWidth, boolean mixedLaneCount) {
    }
}
