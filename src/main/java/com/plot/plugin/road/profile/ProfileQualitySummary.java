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

    private record MetricSegment(String text, boolean warning) {
    }

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

        renderHeading(previewStale);
        renderMetricRow(
            "plugin.road.profile_quality_section_design",
            designSegments(metrics, maxGradeLimit, maxGradeChangeLimit));
        renderBuildTerrainRow(metrics);
    }

    public static String compactLine(
            RoadProfileChartData chart,
            Road road,
            RoadSystemConfig config) {
        if (chart == null || !chart.hasProfileData()) {
            return "";
        }
        RoadProfileQualityMetrics metrics = RoadProfileQualityAnalyzer.analyze(chart);
        if (metrics.isEmpty()) {
            return "";
        }
        return PlotI18n.tr(
            "plugin.road.profile_quality_compact_line",
            metrics.design().maxGradePercent(),
            metrics.design().maxGradeChangePercent(),
            metrics.terrain().cutBlockColumns(),
            metrics.terrain().fillBlockColumns(),
            metrics.build().stepCount());
    }

    private static void renderHeading(boolean previewStale) {
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.profile_quality_heading_short"));
        if (previewStale) {
            ImGui.sameLine(0, 0);
            ImGui.textColored(PluginUiColors.HINT_GRAY, "*");
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(PlotI18n.tr("plugin.road.profile_quality_preview_stale"));
            }
        } else if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.profile_quality_heading"));
        }
    }

    private static MetricSegment[] designSegments(
            RoadProfileQualityMetrics metrics,
            double maxGradeLimit,
            double maxGradeChangeLimit) {
        return new MetricSegment[] {
            new MetricSegment(
                PlotI18n.tr(
                    "plugin.road.profile_quality_metric_grade",
                    metrics.design().maxGradePercent()),
                metrics.design().exceedsMaxGrade(maxGradeLimit)),
            new MetricSegment(
                PlotI18n.tr(
                    "plugin.road.profile_quality_metric_grade_change",
                    metrics.design().maxGradeChangePercent()),
                metrics.design().exceedsMaxGradeChange(maxGradeChangeLimit)),
            new MetricSegment(
                PlotI18n.tr(
                    "plugin.road.profile_quality_metric_grade_run",
                    metrics.design().longestGradeRun()),
                false)
        };
    }

    private static void renderBuildTerrainRow(RoadProfileQualityMetrics metrics) {
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.profile_quality_section_build"));
        appendSegments(buildSegments(metrics));

        ImGui.sameLine();
        ImGui.textColored(PluginUiColors.HINT_GRAY, " | ");
        ImGui.sameLine();
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.profile_quality_section_terrain"));
        appendSegments(terrainSegments(metrics));
    }

    private static MetricSegment[] buildSegments(RoadProfileQualityMetrics metrics) {
        RoadProfileQualityMetrics.BuildMetrics build = metrics.build();
        if (build.hasAbnormalSteps()) {
            return new MetricSegment[] {
                new MetricSegment(
                    PlotI18n.tr("plugin.road.profile_quality_metric_steps", build.stepCount()),
                    false),
                new MetricSegment(
                    PlotI18n.tr("plugin.road.profile_quality_metric_flat", build.longestFlatRun()),
                    false),
                new MetricSegment(
                    PlotI18n.tr(
                        "plugin.road.profile_quality_metric_deviation",
                        build.maxDesignBuildDeviation()),
                    build.hasDesignBuildMismatch()),
                new MetricSegment(
                    PlotI18n.tr("plugin.road.profile_quality_metric_abnormal", build.abnormalStepCount()),
                    true)
            };
        }
        return new MetricSegment[] {
            new MetricSegment(
                PlotI18n.tr("plugin.road.profile_quality_metric_steps", build.stepCount()),
                false),
            new MetricSegment(
                PlotI18n.tr("plugin.road.profile_quality_metric_flat", build.longestFlatRun()),
                false),
            new MetricSegment(
                PlotI18n.tr(
                    "plugin.road.profile_quality_metric_deviation",
                    build.maxDesignBuildDeviation()),
                build.hasDesignBuildMismatch())
        };
    }

    private static MetricSegment[] terrainSegments(RoadProfileQualityMetrics metrics) {
        RoadProfileQualityMetrics.TerrainMetrics terrain = metrics.terrain();
        return new MetricSegment[] {
            new MetricSegment(
                PlotI18n.tr("plugin.road.profile_quality_metric_cut", terrain.cutBlockColumns()),
                false),
            new MetricSegment(
                PlotI18n.tr("plugin.road.profile_quality_metric_fill", terrain.fillBlockColumns()),
                false),
            new MetricSegment(
                PlotI18n.tr("plugin.road.profile_quality_metric_balance", terrain.balanceBlockColumns()),
                false)
        };
    }

    private static void renderMetricRow(String groupLabelKey, MetricSegment... segments) {
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(groupLabelKey));
        appendSegments(segments);
    }

    private static void appendSegments(MetricSegment... segments) {
        for (int i = 0; i < segments.length; i++) {
            ImGui.sameLine();
            ImGui.textColored(PluginUiColors.HINT_GRAY, i == 0 ? " " : " · ");
            ImGui.sameLine(0, 0);
            renderMetric(segments[i].text(), segments[i].warning());
        }
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
