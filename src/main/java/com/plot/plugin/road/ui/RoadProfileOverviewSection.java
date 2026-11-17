package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.profile.ProfileChartLegend;
import com.plot.plugin.road.profile.ProfileChartRenderMode;
import com.plot.plugin.road.profile.RoadProfileChartData;
import com.plot.plugin.road.profile.ProfileQualitySummary;
import com.plot.plugin.road.profile.RoadProfileCompactCard;
import com.plot.plugin.road.profile.terrain.RoadTerrainProfileData;
import com.plot.plugin.road.vertical.NetworkElevationAuthority;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.RoadVerticalStrategy;
import com.plot.plugin.road.vertical.VerticalAlignmentProfileOverlay;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.profile.RoadProfileRoadList;
import com.plot.plugin.road.vertical.FlatElevationProfileOverlay;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;
import java.util.function.Function;

/** 纵断面工作区总览：共享地形入口 + 一组图例 + 多路缩略图（仅观察）。 */
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
        List<Road> roads = RoadProfileRoadList.listOverviewRoads(network, ctx);
        if (roads.isEmpty()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.profile_overview_empty"));
            return;
        }

        boolean uniformFlat = NetworkElevationAuthority.isUniformFlat(network);
        if (uniformFlat) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.terrain_profile.uniform_flat_overview_hint"));
        }

        Road activeRoad = RoadProfileRoadList.resolveActiveRoad(network, ctx);
        if (activeRoad != null) {
            ctx.previewManager().maybeAutoRefreshTerrainProfile(network, activeRoad);
        }

        // 整区唯一地形入口：有选中则更新选中路，否则更新首条仍缺/过期的概览路。
        Road terrainTarget = resolveTerrainToolbarTarget(ctx, network, roads, activeRoad);
        renderSharedTerrainToolbar(ctx, network, terrainTarget);
        renderSharedLegend(ctx, network, roads);

        String activeRoadId = activeRoad != null ? activeRoad.getId() : "";
        for (int index = 0; index < roads.size(); index++) {
            Road road = roads.get(index);
            boolean active = !activeRoadId.isEmpty() && road.getId().equals(activeRoadId);
            renderRoadCard(
                ctx,
                network,
                profileEditor,
                road,
                index + 1,
                roads.size(),
                active,
                flatOverlayForRoad.apply(road),
                uniformFlat);
            if (index + 1 < roads.size()) {
                ImGui.spacing();
            }
        }
    }

    private static Road resolveTerrainToolbarTarget(
            RoadUiContext ctx,
            RoadNetwork network,
            List<Road> roads,
            Road activeRoad) {
        if (activeRoad != null) {
            return activeRoad;
        }
        for (Road road : roads) {
            if (road == null) {
                continue;
            }
            RoadProfileChartData chart = ctx.previewManager().getOverviewChart(network, road.getId());
            if (chart == null || !chart.canRenderChart()
                    || ctx.previewManager().isTerrainProfileStale(network, road)) {
                return road;
            }
        }
        return roads.isEmpty() ? null : roads.getFirst();
    }

    private static void renderSharedTerrainToolbar(
            RoadUiContext ctx,
            RoadNetwork network,
            Road targetRoad) {
        if (targetRoad == null) {
            return;
        }
        renderTerrainStatus(ctx, network, targetRoad);
        boolean jobForTarget = ctx.previewManager().isTerrainProfileJobForRoad(targetRoad.getId());
        if (jobForTarget) {
            if (ImGui.button(PlotI18n.tr("plugin.road.terrain_profile.cancel")
                    + "##profile_terrain_cancel")) {
                ctx.previewManager().cancelTerrainProfileJob();
            }
        } else if (ImGui.button(PlotI18n.tr("plugin.road.terrain_profile.read_update")
                + "##profile_terrain_read")) {
            ctx.previewManager().refreshTerrainProfile(network, targetRoad.getId(), true);
        }
        ImGui.spacing();
    }

    /** 侧栏共用一组图例；各缩略卡不再重复。 */
    private static void renderSharedLegend(
            RoadUiContext ctx, RoadNetwork network, List<Road> roads) {
        RoadProfileChartData legendChart = null;
        boolean buildPreviewStale = ctx.previewManager().needsPreviewRecalc();
        for (Road road : roads) {
            if (road == null) {
                continue;
            }
            RoadProfileChartData chart = ctx.previewManager().getOverviewChart(network, road.getId());
            if (chart == null || !chart.canRenderChart()) {
                continue;
            }
            if (legendChart == null) {
                legendChart = chart;
            }
            if (chart.hasWaterElevations()) {
                legendChart = chart;
                break;
            }
        }
        if (legendChart == null) {
            ImGui.separator();
            ImGui.spacing();
            return;
        }
        ProfileChartLegend.renderCompact(
            ProfileChartRenderMode.MINI,
            null,
            false,
            buildPreviewStale,
            legendChart);
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
    }

    private static void renderTerrainStatus(RoadUiContext ctx, RoadNetwork network, Road road) {
        if (ctx.previewManager().isTerrainProfileJobForRoad(road.getId())) {
            var job = ctx.previewManager().terrainProfileJob();
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr(
                    "plugin.road.terrain_profile.status_sampling",
                    job != null ? job.processedWorkUnits() : 0,
                    job != null ? job.totalWorkUnits() : 0));
            return;
        }
        if (ctx.previewManager().isTerrainProfileJobRunning()) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.terrain_profile.status_sampling_other"));
            return;
        }
        RoadTerrainProfileData data = ctx.previewManager().getTerrainProfile(network, road.getId());
        if (ctx.previewManager().isTerrainProfileFresh(network, road)) {
            String key = data != null && !data.fullyLoaded()
                ? "plugin.road.terrain_profile.status_partial"
                : "plugin.road.terrain_profile.status_ready";
            ImGui.textColored(PluginUiColors.STATUS_OK, PlotI18n.tr(key));
            return;
        }
        if (data != null && data.hasAnyValidSample() && !data.hasDrawableSegment()) {
            ImGui.textColored(
                PluginUiColors.WARNING_LIGHT,
                PlotI18n.tr("plugin.road.terrain_profile.status_sparse"));
            return;
        }
        if (ctx.previewManager().isTerrainProfileStale(network, road)) {
            ImGui.textColored(
                PluginUiColors.WARNING_LIGHT,
                PlotI18n.tr("plugin.road.terrain_profile.status_stale"));
            return;
        }
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.terrain_profile.status_none"));
    }

    private static void openProfileEditor(
            RoadUiContext ctx,
            VerticalProfileEditor profileEditor,
            Road road,
            boolean uniformFlat) {
        if (uniformFlat) {
            ctx.status().warning(PlotI18n.tr("plugin.road.uniform_flat_profile_editor_blocked"));
            return;
        }
        profileEditor.openEditorForRoad(ctx, road.getId());
    }

    private static void renderRoadCard(
            RoadUiContext ctx,
            RoadNetwork network,
            VerticalProfileEditor profileEditor,
            Road road,
            int roadIndex,
            int roadCount,
            boolean active,
            FlatElevationProfileOverlay flatOverlay,
            boolean uniformFlat) {
        ImGui.pushID(road.getId());
        try {
            String header = PlotI18n.tr(
                "plugin.road.profile_overview_road_index",
                roadIndex,
                roadCount);
            if (active) {
                header += "  " + PlotI18n.tr("plugin.road.profile_overview_active");
            }
            if (profileEditor.hasUnconfirmedEditsForRoad(road.getId())) {
                header += "  " + PlotI18n.tr("plugin.road.profile_overview_editing");
            }
            header += " · " + RoadProfileRoadList.formatProfileRoadSummary(
                network, road, ctx.host().coordinates());

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
                openProfileEditor(ctx, profileEditor, road, uniformFlat);
            }

            float chartHeight = active ? ACTIVE_CHART_HEIGHT : MINI_CHART_HEIGHT;
            RoadProfileChartData chartData = ctx.previewManager().getOverviewChart(network, road.getId());
            if (chartData == null || !chartData.canRenderChart()) {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.HINT_GRAY,
                    PlotI18n.tr("plugin.road.terrain_profile.missing_hint"));
            } else {
                List<RoadProfileIntersection> intersections = chartData.intersections();
                boolean flatMode = uniformFlat
                    || RoadVerticalStrategy.fromRoad(road) == RoadVerticalStrategy.FLAT;
                boolean showDesign = !flatMode
                    && (road.getEffectiveProfileSource() == ProfileSource.EDITED
                        || profileEditor.hasUnconfirmedEditsForRoad(road.getId()));
                VerticalAlignmentProfileOverlay design = showDesign
                    ? VerticalAlignmentProfileOverlay.forRoad(network, road).orElse(null)
                    : null;
                RoadProfileCompactCard.renderOverviewChart(
                    chartData,
                    design,
                    intersections,
                    chartHeight,
                    flatOverlay,
                    ProfileChartRenderMode.MINI,
                    road.getVerticalMode());
                if (active && chartData.hasStaleDesignReference()) {
                    RoadUiWidgets.textWrappedColored(
                        PluginUiColors.WARNING_LIGHT,
                        PlotI18n.tr("plugin.road.terrain_profile.design_reference_stale"));
                } else if (active && chartData.hasProfileData()) {
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
