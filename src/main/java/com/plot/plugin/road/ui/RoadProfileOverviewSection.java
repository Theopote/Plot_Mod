package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.profile.ProfileChartRenderMode;
import com.plot.plugin.road.profile.RoadProfileChartData;
import com.plot.plugin.road.profile.ProfileQualitySummary;
import com.plot.plugin.road.profile.RoadProfileCompactCard;
import com.plot.plugin.road.vertical.RoadVerticalStrategy;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.profile.RoadProfileRoadList;
import com.plot.plugin.road.vertical.FlatElevationProfileOverlay;
import com.plot.plugin.road.vertical.VerticalAlignmentProfileOverlay;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;
import java.util.function.Function;

/** Generate Tab 纵断面总览：全部可桩号化 Road 的缩略图卡片。 */
public final class RoadProfileOverviewSection {

    public static final float MINI_CHART_HEIGHT = RoadProfileCompactCard.CHART_HEIGHT;
    public static final float ACTIVE_CHART_HEIGHT = RoadProfileCompactCard.ACTIVE_CHART_HEIGHT;

    private RoadProfileOverviewSection() {
    }

    public static void render(
            RoadUiContext ctx,
            RoadNetwork network,
            VerticalProfileEditor profileEditor,
            Function<Road, FlatElevationProfileOverlay> flatOverlayForRoad) {
        List<Road> roads = RoadProfileRoadList.listStationableRoads(network);
        if (roads.isEmpty()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.profile_overview_empty"));
            return;
        }

        Road activeRoad = RoadProfileRoadList.resolveActiveRoad(network, ctx);
        String activeRoadId = activeRoad != null ? activeRoad.getId() : "";

        for (int index = 0; index < roads.size(); index++) {
            Road road = roads.get(index);
            boolean active = road.getId().equals(activeRoadId);
            renderRoadCard(
                ctx,
                network,
                profileEditor,
                road,
                index + 1,
                roads.size(),
                active,
                flatOverlayForRoad.apply(road));
            if (index + 1 < roads.size()) {
                ImGui.spacing();
            }
        }
    }

    private static void renderRoadCard(
            RoadUiContext ctx,
            RoadNetwork network,
            VerticalProfileEditor profileEditor,
            Road road,
            int roadIndex,
            int roadCount,
            boolean active,
            FlatElevationProfileOverlay flatOverlay) {
        ImGui.pushID(road.getId());
        try {
            String header = PlotI18n.tr(
                "plugin.road.profile_overview_road_index",
                roadIndex,
                roadCount);
            if (active) {
                header += "  " + PlotI18n.tr("plugin.road.profile_overview_active");
            }
            header += " · " + RoadProfileRoadList.formatProfileRoadSummary(network, road);

            float editButtonWidth = ImGui.calcTextSize(
                PlotI18n.tr("plugin.road.profile_overview_edit")).x
                + ImGui.getStyle().getFramePaddingX() * 2.0f;
            float headerWidth = ImGui.getContentRegionAvailX() - editButtonWidth - ImGui.getStyle().getItemSpacingX();
            if (ImGui.selectable(header + "##profile_overview_header", active, 0, headerWidth, 0)) {
                ctx.networkManager().selectRoad(road.getId(), false);
                ctx.requestOverlayRefresh();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("plugin.road.profile_overview_edit") + "##overview_edit")) {
                profileEditor.openEditorForRoad(ctx, road.getId());
            }

            float chartHeight = active ? ACTIVE_CHART_HEIGHT : MINI_CHART_HEIGHT;
            RoadProfileChartData chartData = ctx.previewManager().getRoadProfileChart(road.getId());
            if (chartData == null || !chartData.hasProfileData()) {
                profileEditor.renderCompactMissingProfile(ctx, network, road);
            } else {
                VerticalAlignmentProfileOverlay design =
                    VerticalAlignmentProfileOverlay.forRoad(network, road).orElse(null);
                List<RoadProfileIntersection> intersections = chartData.intersections();
                ProfileChartRenderMode mode = ProfileChartRenderMode.MINI;
                boolean flatMode = RoadVerticalStrategy.fromRoad(road) == RoadVerticalStrategy.FLAT;
                RoadProfileCompactCard.renderOverview(
                    chartData,
                    design,
                    intersections,
                    chartHeight,
                    flatOverlay,
                    mode,
                    road.getVerticalMode(),
                    flatMode,
                    ctx.previewManager().needsPreviewRecalc());
                if (active) {
                    String qualityLine = ProfileQualitySummary.compactLine(
                        chartData,
                        road,
                        ctx.networkManager().getConfig());
                    if (!qualityLine.isEmpty()) {
                        RoadUiWidgets.textWrappedColored(
                            PluginUiColors.HINT_GRAY,
                            qualityLine);
                    }
                }
            }
        } finally {
            ImGui.popID();
        }
    }
}
