package com.plot.plugin.road.profile;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.pipeline.profile.terrain.TerrainFollowPreset;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.ArrayList;
import java.util.List;

/** 纵断面三态产品摘要：坡度 / 平顺 / 地形改动。 */
public final class ProfileQualityStatus {

    public enum Level {
        GOOD,
        WARNING
    }

    public record Dimension(String labelKey, Level level) {
    }

    public record Summary(List<Dimension> dimensions, boolean hasWarnings) {
        public static Summary empty() {
            return new Summary(List.of(), false);
        }
    }

    private static final double TERRAIN_MODIFICATION_RATIO = 0.35;

    private ProfileQualityStatus() {
    }

    public static Summary evaluate(
            RoadProfileChartData chart,
            Road road,
            RoadSystemConfig config) {
        if (chart == null || !chart.hasProfileData()) {
            return Summary.empty();
        }
        RoadProfileQualityMetrics metrics = RoadProfileQualityAnalyzer.analyze(chart);
        if (metrics.isEmpty()) {
            return Summary.empty();
        }
        double maxGradeLimit = resolveMaxGrade(road, config);
        double maxGradeChangeLimit = resolveMaxGradeChange(road);
        int sampleCount = Math.max(1, chart.stations().size());

        List<Dimension> dimensions = new ArrayList<>(3);
        dimensions.add(new Dimension(
            gradeLabelKey(metrics, maxGradeLimit),
            gradeLevel(metrics, maxGradeLimit)));
        dimensions.add(new Dimension(
            smoothnessLabelKey(metrics, maxGradeChangeLimit),
            smoothnessLevel(metrics, maxGradeChangeLimit)));
        dimensions.add(new Dimension(
            terrainLabelKey(metrics, sampleCount),
            terrainLevel(metrics, sampleCount)));

        boolean hasWarnings = dimensions.stream().anyMatch(d -> d.level() == Level.WARNING);
        return new Summary(dimensions, hasWarnings);
    }

    public static void render(
            RoadProfileChartData chart,
            Road road,
            RoadSystemConfig config,
            boolean previewStale) {
        Summary summary = evaluate(chart, road, config);
        if (summary.dimensions().isEmpty()) {
            return;
        }
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.profile_status_heading"));
        if (previewStale) {
            ImGui.sameLine(0, 0);
            ImGui.textColored(PluginUiColors.HINT_GRAY, "*");
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(PlotI18n.tr("plugin.road.profile_quality_preview_stale"));
            }
        }
        for (int i = 0; i < summary.dimensions().size(); i++) {
            if (i > 0) {
                ImGui.sameLine();
                ImGui.textColored(PluginUiColors.HINT_GRAY, " ");
            } else {
                ImGui.sameLine();
            }
            Dimension dimension = summary.dimensions().get(i);
            int color = dimension.level() == Level.WARNING
                ? PluginUiColors.WARNING
                : PluginUiColors.LEGEND;
            ImGui.sameLine(0, 0);
            ImGui.textColored(color, PlotI18n.tr(dimension.labelKey()));
        }
        if (!summary.hasWarnings()) {
            ImGui.textColored(PluginUiColors.LEGEND, PlotI18n.tr("plugin.road.profile_status_all_good"));
        }
    }

    public static String compactLine(
            RoadProfileChartData chart,
            Road road,
            RoadSystemConfig config) {
        Summary summary = evaluate(chart, road, config);
        if (summary.dimensions().isEmpty()) {
            return "";
        }
        if (!summary.hasWarnings()) {
            return PlotI18n.tr("plugin.road.profile_status_all_good");
        }
        List<String> warnings = summary.dimensions().stream()
            .filter(d -> d.level() == Level.WARNING)
            .map(d -> PlotI18n.tr(d.labelKey()))
            .toList();
        return String.join(" · ", warnings);
    }

    private static Level gradeLevel(RoadProfileQualityMetrics metrics, double maxGradeLimit) {
        return metrics.design().exceedsMaxGrade(maxGradeLimit) ? Level.WARNING : Level.GOOD;
    }

    private static String gradeLabelKey(RoadProfileQualityMetrics metrics, double maxGradeLimit) {
        return gradeLevel(metrics, maxGradeLimit) == Level.WARNING
            ? "plugin.road.profile_status_grade_warning"
            : "plugin.road.profile_status_grade_good";
    }

    private static Level smoothnessLevel(RoadProfileQualityMetrics metrics, double maxGradeChangeLimit) {
        if (metrics.build().hasAbnormalSteps()
                || metrics.build().hasDesignBuildMismatch()
                || metrics.design().exceedsMaxGradeChange(maxGradeChangeLimit)) {
            return Level.WARNING;
        }
        return Level.GOOD;
    }

    private static String smoothnessLabelKey(RoadProfileQualityMetrics metrics, double maxGradeChangeLimit) {
        return smoothnessLevel(metrics, maxGradeChangeLimit) == Level.WARNING
            ? "plugin.road.profile_status_smoothness_warning"
            : "plugin.road.profile_status_smoothness_good";
    }

    private static Level terrainLevel(RoadProfileQualityMetrics metrics, int sampleCount) {
        int earthwork = metrics.terrain().earthworkBlockColumns();
        double threshold = sampleCount * TERRAIN_MODIFICATION_RATIO;
        return earthwork > threshold ? Level.WARNING : Level.GOOD;
    }

    private static String terrainLabelKey(RoadProfileQualityMetrics metrics, int sampleCount) {
        return terrainLevel(metrics, sampleCount) == Level.WARNING
            ? "plugin.road.profile_status_terrain_warning"
            : "plugin.road.profile_status_terrain_good";
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
