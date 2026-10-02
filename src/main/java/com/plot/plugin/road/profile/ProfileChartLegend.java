package com.plot.plugin.road.profile;

import com.plot.plugin.road.vertical.FlatElevationProfileOverlay;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.VerticalAlignmentProfileOverlay;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;

/** 纵断面图分层图例（raw / trend / road / design）。 */
public final class ProfileChartLegend {

    private ProfileChartLegend() {
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
        if (verticalMode == RoadVerticalMode.MANUAL_PROFILE && design != null && !design.isEmpty()) {
            ImGui.sameLine();
            ImGui.textColored(
                ProfileChartSeriesStyle.DESIGN_PROFILE,
                "\u25A0 " + PlotI18n.tr("plugin.road.profile_design"));
        }
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

    public static void renderIntersectionLegend(List<RoadProfileIntersection> intersections) {
        if (intersections == null || intersections.isEmpty()) {
            return;
        }
        boolean hasGradeSeparated = intersections.stream().anyMatch(RoadProfileIntersection::gradeSeparated);
        boolean hasAtGrade = intersections.stream().anyMatch(intersection -> !intersection.gradeSeparated());
        if (hasAtGrade) {
            ImGui.textColored(0xFF66CCFF, "\u25CF " + PlotI18n.tr("plugin.road.profile_intersection_marker"));
        }
        if (hasGradeSeparated) {
            if (hasAtGrade) {
                ImGui.sameLine();
            }
            ImGui.textColored(0xFFFF9966, "\u25CE " + PlotI18n.tr(
                "plugin.road.profile_intersection_marker_current"));
            ImGui.sameLine();
            ImGui.textColored(0xFFCC99FF, "\u25C7 " + PlotI18n.tr(
                "plugin.road.profile_intersection_marker_grade"));
        }
    }

    private static String buildProfileLabel(boolean buildPreviewStale) {
        return buildPreviewStale
            ? PlotI18n.tr("plugin.road.profile_last_preview_stale")
            : PlotI18n.tr("plugin.road.profile_build_elevation");
    }

}
