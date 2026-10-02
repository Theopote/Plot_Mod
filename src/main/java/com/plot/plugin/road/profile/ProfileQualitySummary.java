package com.plot.plugin.road.profile;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.pipeline.profile.terrain.TerrainFollowPreset;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 纵断面质量指标紧凑摘要（Design / Build / Terrain 三组）。 */
public final class ProfileQualitySummary {

    private ProfileQualitySummary() {
    }

    public static void render(
            RoadProfileChartData chart,
            Road road,
            RoadSystemConfig config,
            boolean previewStale) {
        if (chart == null || !chart.hasProfileData()) {
            return;
        }
        double maxGradeLimit = resolveMaxGrade(road, config);
        double maxGradeChangeLimit = resolveMaxGradeChange(road);
        RoadProfileQualityMetrics metrics = RoadProfileQualityAnalyzer.analyze(chart);
        if (metrics.isEmpty()) {
            return;
        }

        ImGui.spacing();
        if (previewStale) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.profile_quality_preview_stale"));
        }
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.profile_quality_heading"));

        renderSectionLabel("plugin.road.profile_quality_section_design");
        renderMetric(
            PlotI18n.tr(
                "plugin.road.profile_quality_design_max_grade",
                metrics.design().maxGradePercent()),
            metrics.design().exceedsMaxGrade(maxGradeLimit));
        ImGui.sameLine();
        renderMetric(
            PlotI18n.tr(
                "plugin.road.profile_quality_design_max_grade_change",
                metrics.design().maxGradeChangePercent()),
            metrics.design().exceedsMaxGradeChange(maxGradeChangeLimit));
        ImGui.sameLine();
        renderMetric(
            PlotI18n.tr(
                "plugin.road.profile_quality_design_longest_grade_run",
                metrics.design().longestGradeRun()),
            false);

        renderSectionLabel("plugin.road.profile_quality_section_build");
        renderMetric(
            PlotI18n.tr("plugin.road.profile_quality_build_steps", metrics.build().stepCount()),
            false);
        ImGui.sameLine();
        renderMetric(
            PlotI18n.tr(
                "plugin.road.profile_quality_build_longest_flat",
                metrics.build().longestFlatRun()),
            false);
        ImGui.sameLine();
        renderMetric(
            PlotI18n.tr(
                "plugin.road.profile_quality_build_max_deviation",
                metrics.build().maxDesignBuildDeviation()),
            metrics.build().hasDesignBuildMismatch());
        if (metrics.build().hasAbnormalSteps()) {
            ImGui.sameLine();
            renderMetric(
                PlotI18n.tr(
                    "plugin.road.profile_quality_build_abnormal_steps",
                    metrics.build().abnormalStepCount()),
                true);
        }

        renderSectionLabel("plugin.road.profile_quality_section_terrain");
        renderMetric(
            PlotI18n.tr(
                "plugin.road.profile_quality_terrain_cut",
                metrics.terrain().cutBlockColumns()),
            false);
        ImGui.sameLine();
        renderMetric(
            PlotI18n.tr(
                "plugin.road.profile_quality_terrain_fill",
                metrics.terrain().fillBlockColumns()),
            false);
        ImGui.sameLine();
        renderMetric(
            PlotI18n.tr(
                "plugin.road.profile_quality_terrain_balance",
                metrics.terrain().balanceBlockColumns()),
            false);
    }

    public static String compactLine(
            RoadProfileChartData chart,
            Road road,
            RoadSystemConfig config) {
        if (chart == null || !chart.hasProfileData()) {
            return "";
        }
        RoadProfileQualityMetrics metrics = RoadProfileQualityAnalyzer.analyze(chart);
        return PlotI18n.tr(
            "plugin.road.profile_quality_compact_line",
            metrics.terrain().cutBlockColumns(),
            metrics.terrain().fillBlockColumns(),
            metrics.design().maxGradePercent(),
            metrics.build().stepCount());
    }

    private static void renderSectionLabel(String key) {
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(key));
    }

    private static void renderMetric(String label, boolean warning) {
        ImGui.textColored(warning ? PluginUiColors.ERROR : PluginUiColors.LEGEND, label);
    }

    private static double resolveMaxGrade(Road road, RoadSystemConfig config) {
        if (road != null && road.getMaxSlope() != null) {
            return road.getMaxSlope();
        }
        return config != null ? config.getMaxSlope() : 8.0f;
    }

    private static double resolveMaxGradeChange(Road road) {
        if (road != null && road.getVerticalMode() == RoadVerticalMode.FIT_TERRAIN) {
            return road.getEffectiveTerrainFollowPreset().maxGradeChangePercent();
        }
        return TerrainFollowPreset.STANDARD.maxGradeChangePercent();
    }
}
