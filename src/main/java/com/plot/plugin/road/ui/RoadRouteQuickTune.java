package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/**
 * 路线 Tab 主界面：单车道宽与车道数快速调节（默认参数或当前选中道路）。
 */
final class RoadRouteQuickTune {
    private RoadRouteQuickTune() {
    }

    static void renderConfigDefaults(RoadUiContext ctx) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        if (config == null) {
            return;
        }
        renderLaneWidthLaneSliders(
            config.getDefaultLaneWidth(),
            config.getLaneCount(),
            (laneWidth, lanes) -> {
                config.setDefaultLaneWidth(laneWidth);
                config.setLaneCount(lanes);
                config.markCustom();
                ctx.adoptIncludeSidewalkRef().set(config.isIncludeSidewalk());
                ctx.onGenerationConfigChanged();
            });
    }

    static void renderForRoad(RoadUiContext ctx, Road road, Runnable onHistory) {
        if (road == null) {
            return;
        }
        RoadSystemConfig config = ctx.networkManager().getConfig();
        var resolved = road.getCrossSection().resolve(config);
        int laneWidth = road.getCrossSection().getCarriageway().getEffectiveDefaultLaneWidth();
        renderLaneWidthLaneSliders(
            laneWidth,
            resolved.laneCount,
            (perLane, lanes) -> {
                road.getCrossSection().getCarriageway().setDefaultLaneWidth(perLane);
                road.setLaneCount(lanes);
                CenterLineStyle style = road.getCenterLineStyle() != null
                    ? road.getCenterLineStyle()
                    : CenterLineStyle.NONE;
                boolean dividers = road.getLaneDividers() != null
                    ? road.getLaneDividers()
                    : lanes > 1;
                road.getCrossSection().getCarriageway().syncDerivedWidth(style, dividers, 0);
                ctx.onGenerationConfigChanged();
            },
            onHistory);
    }

    private static void renderLaneWidthLaneSliders(
            int laneWidth,
            int laneCount,
            LaneWidthLaneConsumer onChange) {
        renderLaneWidthLaneSliders(laneWidth, laneCount, onChange, null);
    }

    private static void renderLaneWidthLaneSliders(
            int laneWidth,
            int laneCount,
            LaneWidthLaneConsumer onChange,
            Runnable onHistory) {
        int[] laneWidthArr = {laneWidth};
        boolean laneWidthChanged = ImGui.sliderInt(
            PlotI18n.tr("plugin.road.lane_travel_width") + "##route_lane_w",
            laneWidthArr,
            RoadParameterLimits.MIN_LANE_WIDTH_BLOCKS,
            RoadParameterLimits.MAX_LANE_WIDTH_BLOCKS,
            "%d");
        if (ImGui.isItemActivated() && onHistory != null) {
            onHistory.run();
        }
        if (laneWidthChanged) {
            onChange.accept(laneWidthArr[0], laneCount);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.lane_travel_width"));
        }

        int[] laneArr = {laneCount};
        boolean laneChanged = ImGui.sliderInt(
            PlotI18n.tr("plugin.road.lane_count") + "##route_lanes",
            laneArr,
            RoadParameterLimits.MIN_LANE_COUNT,
            RoadParameterLimits.MAX_LANE_COUNT,
            "%d");
        if (ImGui.isItemActivated() && onHistory != null) {
            onHistory.run();
        }
        if (laneChanged) {
            onChange.accept(laneWidthArr[0], laneArr[0]);
        }
    }

    @FunctionalInterface
    private interface LaneWidthLaneConsumer {
        void accept(int laneWidth, int laneCount);
    }
}
