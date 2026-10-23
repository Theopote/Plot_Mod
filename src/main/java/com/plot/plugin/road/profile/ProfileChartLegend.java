package com.plot.plugin.road.profile;

import com.plot.plugin.road.vertical.FlatElevationProfileOverlay;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.VerticalAlignmentProfileOverlay;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;

/** 纵断面图分层图例（raw / trend / road / design）。 */
public final class ProfileChartLegend {

    private static final int COLOR_INTERSECTION_AT_GRADE = 0xFF66CCFF;
    private static final int COLOR_INTERSECTION_CURRENT = 0xFFFF9966;
    private static final int COLOR_INTERSECTION_OTHER = 0xFFCC99FF;

    private ProfileChartLegend() {
    }

    /** 紧凑卡片单行图例：Overview 为地形+建造，Editor 为地形+设计（体素预览后可选建造）。 */
    public static void renderCompact(
            ProfileChartRenderMode chartMode,
            RoadVerticalMode verticalMode,
            boolean flatMode,
            boolean buildPreviewStale) {
        renderCompact(chartMode, verticalMode, flatMode, buildPreviewStale, null);
    }

    public static void renderCompact(
            ProfileChartRenderMode chartMode,
            RoadVerticalMode verticalMode,
            boolean flatMode,
            boolean buildPreviewStale,
            RoadProfileChartData chart) {
        renderCompact(
            chartMode,
            verticalMode,
            flatMode,
            buildPreviewStale,
            chartMode.showBuildProfileLine(),
            chart);
    }

    public static void renderCompact(
            ProfileChartRenderMode chartMode,
            RoadVerticalMode verticalMode,
            boolean flatMode,
            boolean buildPreviewStale,
            boolean showBuildProfile,
            RoadProfileChartData chart) {
        renderCompact(
            chartMode,
            verticalMode,
            flatMode,
            buildPreviewStale,
            showBuildProfile,
            chart,
            List.of());
    }

    /**
     * 编辑器 / 概览紧凑图例（单行）。
     * 编辑器顺序：地形 → 设计 →（可选）建造 → 当前道路/立交/平交 → 水面。
     */
    public static void renderCompact(
            ProfileChartRenderMode chartMode,
            RoadVerticalMode verticalMode,
            boolean flatMode,
            boolean buildPreviewStale,
            boolean showBuildProfile,
            RoadProfileChartData chart,
            List<RoadProfileIntersection> intersections) {
        boolean showWaterSurface = chart != null && chart.hasWaterElevations();
        if (flatMode) {
            renderFlatCompactLegend(buildPreviewStale, showBuildProfile, showWaterSurface, intersections);
            return;
        }
        if (chartMode == ProfileChartRenderMode.EDITOR) {
            renderCompactEditorLegend(
                buildPreviewStale, showBuildProfile, showWaterSurface, intersections);
            return;
        }
        renderCompactOverviewLegend(buildPreviewStale, showWaterSurface);
    }

    private static void renderCompactOverviewLegend(boolean buildPreviewStale, boolean showWaterSurface) {
        ImGui.textColored(
            ProfileChartSeriesStyle.RAW_TERRAIN,
            "\u25A0 " + PlotI18n.tr("plugin.road.profile_ground"));
        appendWaterSurfaceLegend(showWaterSurface);
        ImGui.sameLine();
        ImGui.textColored(
            ProfileChartSeriesStyle.BUILD_PROFILE,
            "\u25A0 " + buildProfileLabel(buildPreviewStale));
    }

    private static void renderCompactEditorLegend(
            boolean buildPreviewStale,
            boolean showBuildProfile,
            boolean showWaterSurface,
            List<RoadProfileIntersection> intersections) {
        ImGui.textColored(
            ProfileChartSeriesStyle.RAW_TERRAIN,
            "\u25A0 " + PlotI18n.tr("plugin.road.profile_ground"));
        ImGui.sameLine();
        ImGui.textColored(
            ProfileChartSeriesStyle.DESIGN_PROFILE,
            "\u25A0 " + PlotI18n.tr("plugin.road.profile_design_elevation"));
        if (showBuildProfile) {
            ImGui.sameLine();
            ImGui.textColored(
                ProfileChartSeriesStyle.BUILD_PROFILE,
                "\u25A0 " + buildProfileLabel(buildPreviewStale));
        }
        appendIntersectionLegendInline(intersections);
        appendWaterSurfaceLegend(showWaterSurface);
    }

    private static void renderFlatCompactLegend(
            boolean buildPreviewStale,
            boolean showBuildProfile,
            boolean showWaterSurface,
            List<RoadProfileIntersection> intersections) {
        ImGui.textColored(
            ProfileChartSeriesStyle.RAW_TERRAIN,
            "\u25A0 " + PlotI18n.tr("plugin.road.profile_ground"));
        if (showBuildProfile) {
            ImGui.sameLine();
            ImGui.textColored(
                ProfileChartSeriesStyle.BUILD_PROFILE,
                "\u25A0 " + buildProfileLabel(buildPreviewStale));
        }
        appendIntersectionLegendInline(intersections);
        appendWaterSurfaceLegend(showWaterSurface);
    }

    private static void appendWaterSurfaceLegend(boolean showWaterSurface) {
        if (!showWaterSurface) {
            return;
        }
        ImGui.sameLine();
        ImGui.textColored(
            ProfileChartSeriesStyle.WATER_SURFACE,
            "--- " + PlotI18n.tr("plugin.road.profile_water_surface"));
    }

    /**
     * 将路口图示接在当前行末尾（sameLine），避免另起一行。
     * 立交：当前道路 ○ + 相交道路 ◇；平交：平交交叉 ●。
     */
    private static void appendIntersectionLegendInline(List<RoadProfileIntersection> intersections) {
        if (intersections == null || intersections.isEmpty()) {
            return;
        }
        boolean hasGradeSeparated = false;
        boolean hasAtGrade = false;
        for (RoadProfileIntersection intersection : intersections) {
            if (intersection.gradeSeparated()) {
                hasGradeSeparated = true;
            } else {
                hasAtGrade = true;
            }
        }
        if (hasGradeSeparated) {
            ImGui.sameLine();
            ImGui.textColored(
                COLOR_INTERSECTION_CURRENT,
                "\u25CE " + PlotI18n.tr("plugin.road.profile_intersection_marker_current"));
            ImGui.sameLine();
            ImGui.textColored(
                COLOR_INTERSECTION_OTHER,
                "\u25C7 " + PlotI18n.tr("plugin.road.profile_intersection_marker_grade"));
        }
        if (hasAtGrade) {
            ImGui.sameLine();
            ImGui.textColored(
                COLOR_INTERSECTION_AT_GRADE,
                "\u25CF " + PlotI18n.tr("plugin.road.profile_intersection_marker"));
        }
    }

    public static void renderSeriesLegend(
            RoadVerticalMode verticalMode,
            boolean flatMode,
            VerticalAlignmentProfileOverlay design,
            FlatElevationProfileOverlay flatOverlay,
            boolean buildPreviewStale) {
        if (flatMode) {
            renderFlatLegend(flatOverlay, buildPreviewStale);
            return;
        }
        ProfileChartGuideSemantics guideSemantics = ProfileChartGuideSemantics.fromVerticalMode(verticalMode);
        if (guideSemantics == ProfileChartGuideSemantics.TERRAIN_TREND) {
            renderTerrainAdaptiveLegend(buildPreviewStale);
            return;
        }
        renderGeneralLegend(verticalMode, design, buildPreviewStale, guideSemantics);
    }

    private static void renderTerrainAdaptiveLegend(boolean buildPreviewStale) {
        ImGui.textColored(
            ProfileChartSeriesStyle.RAW_TERRAIN,
            "\u25A0 " + PlotI18n.tr("plugin.road.profile_terrain_raw"));
        ImGui.sameLine();
        ImGui.textColored(
            ProfileChartSeriesStyle.WATER_SURFACE,
            "--- " + PlotI18n.tr("plugin.road.profile_water_surface"));
        ImGui.sameLine();
        ImGui.textColored(
            ProfileChartSeriesStyle.TERRAIN_TREND,
            "--- " + PlotI18n.tr("plugin.road.profile_terrain_trend"));
        ImGui.sameLine();
        ImGui.textColored(
            ProfileChartSeriesStyle.DESIGN_PROFILE,
            "\u25A0 " + PlotI18n.tr("plugin.road.profile_design_elevation"));
        ImGui.sameLine();
        ImGui.textColored(
            ProfileChartSeriesStyle.BUILD_PROFILE,
            "\u25A0 " + buildProfileLabel(buildPreviewStale));
    }

    private static void renderGeneralLegend(
            RoadVerticalMode verticalMode,
            VerticalAlignmentProfileOverlay design,
            boolean buildPreviewStale,
            ProfileChartGuideSemantics guideSemantics) {
        ImGui.textColored(
            ProfileChartSeriesStyle.RAW_TERRAIN,
            "\u25A0 " + PlotI18n.tr("plugin.road.profile_ground"));
        ImGui.sameLine();
        ImGui.textColored(
            ProfileChartSeriesStyle.DESIGN_PROFILE,
            "\u25A0 " + PlotI18n.tr("plugin.road.profile_design_elevation"));
        ImGui.sameLine();
        ImGui.textColored(
            ProfileChartSeriesStyle.BUILD_PROFILE,
            "\u25A0 " + buildProfileLabel(buildPreviewStale));
        if (guideSemantics == ProfileChartGuideSemantics.GUIDE_LINE) {
            ImGui.sameLine();
            ImGui.textColored(
                ProfileChartSeriesStyle.GUIDE_LINE,
                "--- " + PlotI18n.tr("plugin.road.profile_guide"));
        }
    }

    private static void renderFlatLegend(
            FlatElevationProfileOverlay flatOverlay,
            boolean buildPreviewStale) {
        ImGui.textColored(
            ProfileChartSeriesStyle.RAW_TERRAIN,
            "\u25A0 " + PlotI18n.tr("plugin.road.profile_ground"));
        ImGui.sameLine();
        ImGui.textColored(
            ProfileChartSeriesStyle.BUILD_PROFILE,
            "\u25A0 " + buildProfileLabel(buildPreviewStale));
        if (flatOverlay != null && flatOverlay.showCurrent()) {
            ImGui.sameLine();
            ImGui.textColored(0xFF66D9EF, "--- " + PlotI18n.tr(
                "plugin.road.profile_flat_base_y",
                flatOverlay.currentElevation()));
        }
        if (flatOverlay != null && flatOverlay.showSuggested()) {
            ImGui.sameLine();
            ImGui.textColored(0xFFFFB84D, "=== " + PlotI18n.tr(
                "plugin.road.profile_flat_suggested",
                flatOverlay.suggestedElevation()));
        }
    }

    /** @deprecated 请用 {@link #renderCompact} 的 intersections 参数，保证图例单行。 */
    @Deprecated
    public static void renderIntersectionLegend(List<RoadProfileIntersection> intersections) {
        if (intersections == null || intersections.isEmpty()) {
            return;
        }
        boolean hasGradeSeparated = intersections.stream().anyMatch(RoadProfileIntersection::gradeSeparated);
        boolean hasAtGrade = intersections.stream().anyMatch(i -> !i.gradeSeparated());
        if (!hasGradeSeparated && !hasAtGrade) {
            return;
        }
        boolean first = true;
        if (hasGradeSeparated) {
            ImGui.textColored(
                COLOR_INTERSECTION_CURRENT,
                "\u25CE " + PlotI18n.tr("plugin.road.profile_intersection_marker_current"));
            first = false;
            ImGui.sameLine();
            ImGui.textColored(
                COLOR_INTERSECTION_OTHER,
                "\u25C7 " + PlotI18n.tr("plugin.road.profile_intersection_marker_grade"));
        }
        if (hasAtGrade) {
            if (!first) {
                ImGui.sameLine();
            }
            ImGui.textColored(
                COLOR_INTERSECTION_AT_GRADE,
                "\u25CF " + PlotI18n.tr("plugin.road.profile_intersection_marker"));
        }
    }

    private static String buildProfileLabel(boolean buildPreviewStale) {
        return buildPreviewStale
            ? PlotI18n.tr("plugin.road.profile_last_preview_stale")
            : PlotI18n.tr("plugin.road.profile_build_elevation");
    }

}
