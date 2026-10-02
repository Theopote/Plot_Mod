package com.plot.plugin.road.profile;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.pipeline.profile.terrain.TerrainFollowPreset;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 纵断面质量指标紧凑摘要（编辑器 / 卡片下方）。 */
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
        double maxGrade = resolveMaxGrade(road, config);
        double maxGradeChange = resolveMaxGradeChange(road);
        RoadProfileQualityMetrics metrics = RoadProfileQualityAnalyzer.analyze(
            chart, maxGrade, maxGradeChange);
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

        renderMetric(
            PlotI18n.tr("plugin.road.profile_quality_cut", metrics.cutBlockColumns()),
            false);
        ImGui.sameLine();
        renderMetric(
            PlotI18n.tr("plugin.road.profile_quality_fill", metrics.fillBlockColumns()),
            false);
        ImGui.sameLine();
        renderMetric(
            PlotI18n.tr("plugin.road.profile_quality_max_grade", metrics.maxBuildGradePercent()),
            metrics.exceedsMaxGrade(maxGrade));

        renderMetric(
            PlotI18n.tr(
                "plugin.road.profile_quality_longest_grade_run",
                metrics.longestConstantGradeRun()),
            false);
        ImGui.sameLine();
        renderMetric(
            PlotI18n.tr(
                "plugin.road.profile_quality_max_grade_change",
                metrics.maxGradeChangePercent()),
            metrics.exceedsMaxGradeChange(maxGradeChange));
        ImGui.sameLine();
        renderMetric(
            PlotI18n.tr("plugin.road.profile_quality_steps", metrics.stepCount()),
            false);

        if (metrics.hasAbnormalSteps() || metrics.hasDesignBuildMismatch()) {
            if (metrics.hasAbnormalSteps()) {
                renderMetric(
                    PlotI18n.tr(
                        "plugin.road.profile_quality_abnormal_steps",
                        metrics.abnormalStepCount()),
                    true);
                ImGui.sameLine();
            }
            if (metrics.hasDesignBuildMismatch()) {
                renderMetric(
                    PlotI18n.tr(
                        "plugin.road.profile_quality_design_deviation",
                        metrics.maxDesignBuildDeviation()),
                    true);
            }
        }
    }

    public static String compactLine(
            RoadProfileChartData chart,
            Road road,
            RoadSystemConfig config) {
        if (chart == null || !chart.hasProfileData()) {
            return "";
        }
        RoadProfileQualityMetrics metrics = RoadProfileQualityAnalyzer.analyze(
            chart,
            resolveMaxGrade(road, config),
            resolveMaxGradeChange(road));
        return PlotI18n.tr(
            "plugin.road.profile_quality_compact_line",
            metrics.cutBlockColumns(),
            metrics.fillBlockColumns(),
            metrics.maxBuildGradePercent(),
            metrics.stepCount());
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
