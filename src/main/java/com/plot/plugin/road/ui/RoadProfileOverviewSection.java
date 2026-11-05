package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
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

/** 纵断面工作区总览：可桩号道路缩略图 + Level-1 地形读取入口。 */
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
        renderActiveTerrainToolbar(ctx, network, profileEditor, activeRoad, uniformFlat);

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
                flatOverlayForRoad.apply(road),
                uniformFlat);
            if (index + 1 < roads.size()) {
                ImGui.spacing();
            }
        }
    }

    private static void renderActiveTerrainToolbar(
            RoadUiContext ctx,
            RoadNetwork network,
            VerticalProfileEditor profileEditor,
            Road activeRoad,
            boolean uniformFlat) {
        if (activeRoad == null) {
            return;
        }
        renderTerrainStatus(ctx, network, activeRoad);
        boolean jobForActive = ctx.previewManager().isTerrainProfileJobForRoad(activeRoad.getId());
        if (jobForActive) {
            RoadUiWidgets.flowButtons(
                new String[] {
                    PlotI18n.tr("plugin.road.terrain_profile.cancel"),
                    PlotI18n.tr("plugin.road.profile_overview_edit")
                },
                new Runnable[] {
                    () -> ctx.previewManager().cancelTerrainProfileJob(),
                    () -> openProfileEditor(ctx, profileEditor, activeRoad, uniformFlat)
                });
        } else {
            RoadUiWidgets.flowButtons(
                new String[] {
                    PlotI18n.tr("plugin.road.terrain_profile.read_update"),
                    PlotI18n.tr("plugin.road.profile_overview_edit")
                },
                new Runnable[] {
                    () -> ctx.previewManager().refreshTerrainProfile(
                        network, activeRoad.getId(), true),
                    () -> openProfileEditor(ctx, profileEditor, activeRoad, uniformFlat)
                });
        }
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
                renderMissingTerrainActions(ctx, network, profileEditor, road, uniformFlat);
            } else {
                List<RoadProfileIntersection> intersections = chartData.intersections();
                ProfileChartRenderMode mode = ProfileChartRenderMode.MINI;
                boolean flatMode = uniformFlat
                    || RoadVerticalStrategy.fromRoad(road) == RoadVerticalStrategy.FLAT;
                boolean showDesign = !flatMode
                    && (road.getEffectiveProfileSource() == ProfileSource.EDITED
                        || profileEditor.hasUnconfirmedEditsForRoad(road.getId()));
                VerticalAlignmentProfileOverlay design = showDesign
                    ? VerticalAlignmentProfileOverlay.forRoad(network, road).orElse(null)
                    : null;
                boolean layersStale = chartData.hasStaleDesignReference()
                    || ctx.previewManager().needsPreviewRecalc();
                RoadProfileCompactCard.renderOverview(
                    chartData,
                    design,
                    intersections,
                    chartHeight,
                    flatOverlay,
                    mode,
                    road.getVerticalMode(),
                    flatMode,
                    layersStale,
                    showDesign && design != null);
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

    private static void renderMissingTerrainActions(
            RoadUiContext ctx,
            RoadNetwork network,
            VerticalProfileEditor profileEditor,
            Road road,
            boolean uniformFlat) {
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.terrain_profile.missing_hint"));
        RoadUiWidgets.flowButtons(
            new String[] {
                PlotI18n.tr("plugin.road.terrain_profile.read_update"),
                PlotI18n.tr("plugin.road.profile_overview_edit")
            },
            new Runnable[] {
                () -> ctx.previewManager().refreshTerrainProfile(network, road.getId(), true),
                () -> openProfileEditor(ctx, profileEditor, road, uniformFlat)
            });
    }
}
