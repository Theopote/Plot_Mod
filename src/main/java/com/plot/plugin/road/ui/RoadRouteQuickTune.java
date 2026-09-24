package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.model.Road;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/**
 * 路线 Tab 主界面：宽度与车道数快速调节（默认参数或当前选中道路）。
 */
final class RoadRouteQuickTune {
    private RoadRouteQuickTune() {
    }

    static void renderConfigDefaults(RoadUiContext ctx) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        if (config == null) {
            return;
        }
        renderWidthLaneSliders(
            config.getRoadWidth(),
            config.getLaneCount(),
            (width, lanes) -> {
                config.setRoadWidth(width);
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
        renderWidthLaneSliders(
            resolved.carriagewayWidth,
            resolved.laneCount,
            (width, lanes) -> {
                if (onHistory != null) {
                    onHistory.run();
                }
                road.setWidth(width);
                road.setLaneCount(lanes);
                ctx.onGenerationConfigChanged();
            });
    }

    private static void renderWidthLaneSliders(
            int width,
            int laneCount,
            WidthLaneConsumer onChange) {
        int[] widthArr = {width};
        if (ImGui.sliderInt(
            PlotI18n.tr("plugin.road.road_width", widthArr[0]) + "##route_width",
            widthArr,
            RoadParameterLimits.MIN_CARRIAGEWAY_WIDTH,
            RoadParameterLimits.MAX_CARRIAGEWAY_WIDTH,
            "%d")) {
            onChange.accept(widthArr[0], laneCount);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.road_width"));
        }

        int[] laneArr = {laneCount};
        if (ImGui.sliderInt(
            PlotI18n.tr("plugin.road.lane_count", laneArr[0]) + "##route_lanes",
            laneArr,
            RoadParameterLimits.MIN_LANE_COUNT,
            RoadParameterLimits.MAX_LANE_COUNT,
            "%d")) {
            onChange.accept(widthArr[0], laneArr[0]);
        }
    }

    @FunctionalInterface
    private interface WidthLaneConsumer {
        void accept(int width, int laneCount);
    }
}
