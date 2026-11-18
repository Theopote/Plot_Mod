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
import imgui.ImColor;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

import java.util.List;
import java.util.function.Function;

/** 纵断面工作区总览：共享地形入口 + 一组图例 + 多路缩略图（仅观察）。 */
public final class RoadProfileOverviewSection {

    public static final float MINI_CHART_HEIGHT = RoadProfileCompactCard.CHART_HEIGHT;
    public static final float ACTIVE_CHART_HEIGHT = RoadProfileCompactCard.ACTIVE_CHART_HEIGHT;
    /** 未选中标题行底色（略亮于面板底，保证每条路都有可见底）。 */
    private static final int OVERVIEW_HEADER_IDLE = ImColor.rgba(48, 48, 52, 220);
    private static final int OVERVIEW_HEADER_IDLE_HOVER = ImColor.rgba(58, 58, 64, 230);
    /** 选中标题行：蓝色高亮。 */
    private static final int OVERVIEW_HEADER_ACTIVE = ImColor.rgba(40, 96, 168, 230);
    private static final int OVERVIEW_HEADER_ACTIVE_HOVER = ImColor.rgba(56, 120, 196, 240);

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

        // 整区唯一地形入口：一次排队更新当前概览列表中的全部道路。
        renderSharedTerrainToolbar(ctx, network, roads, activeRoad);
        renderSharedLegend(ctx, network, roads);

        var selectedRoadIds = ctx.networkManager().getSelectedRoadIds();
        for (int index = 0; index < roads.size(); index++) {
            Road road = roads.get(index);
            // 多选时所有选中道路标题都高亮，不只 primary。
            boolean selected = selectedRoadIds != null && selectedRoadIds.contains(road.getId());
            renderRoadCard(
                ctx,
                network,
                profileEditor,
                road,
                selected,
                flatOverlayForRoad.apply(road),
                uniformFlat);
            if (index + 1 < roads.size()) {
                ImGui.spacing();
            }
        }
    }

    private static void renderSharedTerrainToolbar(
            RoadUiContext ctx,
            RoadNetwork network,
            List<Road> roads,
            Road activeRoad) {
        if (roads == null || roads.isEmpty()) {
            return;
        }
        renderTerrainStatus(ctx, network, roads, activeRoad);
        boolean jobRunning = ctx.previewManager().isTerrainProfileJobRunning();
        if (jobRunning) {
            if (ImGui.button(PlotI18n.tr("plugin.road.terrain_profile.cancel")
                    + "##profile_terrain_cancel")) {
                ctx.previewManager().cancelTerrainProfileJob();
            }
        } else if (ImGui.button(PlotI18n.tr("plugin.road.terrain_profile.read_update")
                + "##profile_terrain_read")) {
            List<String> roadIds = new java.util.ArrayList<>(roads.size());
            for (Road road : roads) {
                if (road != null) {
                    roadIds.add(road.getId());
                }
            }
            ctx.previewManager().refreshTerrainProfiles(network, roadIds, true);
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

    private static void renderTerrainStatus(
            RoadUiContext ctx,
            RoadNetwork network,
            List<Road> roads,
            Road activeRoad) {
        if (ctx.previewManager().isTerrainProfileJobRunning()) {
            int total = ctx.previewManager().terrainRefreshBatchTotal();
            int completed = ctx.previewManager().terrainRefreshBatchCompleted();
            var job = ctx.previewManager().terrainProfileJob();
            if (total > 1) {
                ImGui.textColored(
                    PluginUiColors.HINT_GRAY,
                    PlotI18n.tr(
                        "plugin.road.terrain_profile.status_sampling_batch",
                        Math.min(total, completed + 1),
                        total,
                        job != null ? job.processedWorkUnits() : 0,
                        job != null ? job.totalWorkUnits() : 0));
            } else {
                ImGui.textColored(
                    PluginUiColors.HINT_GRAY,
                    PlotI18n.tr(
                        "plugin.road.terrain_profile.status_sampling",
                        job != null ? job.processedWorkUnits() : 0,
                        job != null ? job.totalWorkUnits() : 0));
            }
            return;
        }

        Road statusRoad = activeRoad != null ? activeRoad : roads.getFirst();
        RoadTerrainProfileData data = ctx.previewManager().getTerrainProfile(network, statusRoad.getId());
        if (ctx.previewManager().isTerrainProfileFresh(network, statusRoad)) {
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
        if (ctx.previewManager().isTerrainProfileStale(network, statusRoad)) {
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
            boolean active,
            FlatElevationProfileOverlay flatOverlay,
            boolean uniformFlat) {
        ImGui.pushID(road.getId());
        try {
            String header = RoadProfileRoadList.formatProfileRoadSummary(
                network, road, ctx.host().coordinates());
            if (profileEditor.hasUnconfirmedEditsForRoad(road.getId())) {
                header += "  " + PlotI18n.tr("plugin.road.profile_overview_editing");
            }

            String editLabel = PlotI18n.tr("plugin.road.profile_overview_edit");
            float editButtonWidth = ImGui.calcTextSize(editLabel).x
                + ImGui.getStyle().getFramePaddingX() * 2.0f;
            float spacing = ImGui.getStyle().getItemSpacingX();
            float headerWidth = Math.max(1f, ImGui.getContentRegionAvailX() - editButtonWidth - spacing);
            float rowHeight = ImGui.getFrameHeight();
            // 只用自绘底色表达选中态；selectable 固定 false，避免 Header 再叠一层更高的蓝块。
            var origin = ImGui.getCursorScreenPos();
            ImGui.getWindowDrawList().addRectFilled(
                origin.x,
                origin.y,
                origin.x + headerWidth,
                origin.y + rowHeight,
                active ? OVERVIEW_HEADER_ACTIVE : OVERVIEW_HEADER_IDLE,
                3f);
            ImGui.pushStyleColor(ImGuiCol.Header, 0);
            ImGui.pushStyleColor(
                ImGuiCol.HeaderHovered,
                active ? OVERVIEW_HEADER_ACTIVE_HOVER : OVERVIEW_HEADER_IDLE_HOVER);
            ImGui.pushStyleColor(
                ImGuiCol.HeaderActive,
                active ? OVERVIEW_HEADER_ACTIVE_HOVER : OVERVIEW_HEADER_IDLE_HOVER);
            if (ImGui.selectable(
                    header + "##profile_overview_header",
                    false,
                    0,
                    headerWidth,
                    rowHeight)) {
                ctx.networkManager().selectRoad(road.getId(), false);
                ctx.requestOverlayRefresh();
            }
            ImGui.popStyleColor(3);
            ImGui.sameLine(0f, spacing);
            if (ImGui.button(editLabel + "##overview_edit", editButtonWidth, rowHeight)) {
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
