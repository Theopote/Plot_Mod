package com.plot.plugin.building.ui;

import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.DistrictGenerationResult;
import com.plot.plugin.building.generation.DistrictOverlapAnalyzer;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.BuildingProject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 从 Preview 结果提取用户可操作的生成问题（已做产品层收敛）。 */
public final class BuildingGenerationIssues {
    /** 用户可见严重级别：不能生成 / 可生成但需注意 / 仅供参考。 */
    public enum Severity {
        ERROR,
        WARNING,
        INFO
    }

    public enum Kind {
        SKIPPED,
        TOO_CLOSE,
        EXCESSIVE_HEIGHT,
        EXCESSIVE_AREA,
        BUILDING_WARNING
    }

    public record Issue(
            Kind kind,
            Severity severity,
            String primaryBuildingId,
            String primaryBuildingName,
            String secondaryBuildingId,
            String secondaryBuildingName,
            String messageKey,
            String messageArg) {
    }

    public record Summary(
            int generated,
            int attempted,
            int skipped,
            int warningCount,
            int infoCount,
            List<Issue> issues) {

        public boolean hasIssues() {
            return !issues.isEmpty();
        }
    }

    static final double MAX_TOO_CLOSE_GAP_BLOCKS = 2.0;
    static final int MAX_WARNING_HEIGHT_BLOCKS = 240;
    static final int MAX_WARNING_AREA_BLOCKS = 36000;

    private static final Set<String> SUPPRESSED_WARNING_KEYS = Set.of(
        "plugin.building.warn.district_partial",
        "plugin.building.warn.district_overlap",
        "plugin.building.warn.floor_plate_coverage_gap",
        "plugin.building.warn.using_earthwork_pad_elevation",
        "plugin.building.warn.earthwork_pad_unresolved_using_terrain",
        "plugin.building.warn.site_analysis_failed",
        "plugin.building.warn.site_analysis_unavailable_skip",
        "plugin.building.warn.tree_clear_limit",
        "plugin.building.warn.water_site",
        "plugin.building.warn.partial_water_site",
        "plugin.building.warn.steep_site",
        "plugin.building.warn.severe_steep_site",
        "plugin.building.warn.heavy_earthwork",
        "plugin.building.warn.structure_conflict",
        "plugin.building.warn.chunk_unloaded",
        "plugin.building.warn.manual_below_water",
        "plugin.building.warn.earthwork_pad_below_water",
        "plugin.building.warn.foundation_raised_above_water");

    private BuildingGenerationIssues() {
    }

    public static List<Issue> collect(
            BuildingProject project,
            DistrictGenerationResult district,
            BuildingGenerationResult single,
            BuildingPreviewIdentity previewIdentity,
            boolean districtMode,
            Map<String, Double> previewHeights) {
        if (districtMode && district != null) {
            return collectDistrict(project, district, previewHeights);
        }
        if (single != null && previewIdentity != null && !previewIdentity.targetIds().isEmpty()) {
            return collectSingle(project, single, previewIdentity.targetIds().getFirst(), previewHeights);
        }
        return List.of();
    }

    public static Summary summarize(DistrictGenerationResult district, List<Issue> issues, int fallbackAttempted) {
        int attempted;
        int generated;
        int skipped;
        if (district != null && district.buildingsAttempted() > 0) {
            attempted = district.buildingsAttempted();
            generated = district.buildingsGenerated();
            skipped = district.buildingsSkipped();
        } else {
            attempted = Math.max(1, fallbackAttempted);
            skipped = countSkipped(issues);
            generated = Math.max(0, attempted - skipped);
        }
        int warnings = 0;
        int infos = 0;
        for (Issue issue : issues) {
            if (issue.severity() == Severity.WARNING) {
                warnings++;
            } else if (issue.severity() == Severity.INFO) {
                infos++;
            }
        }
        return new Summary(generated, attempted, skipped, warnings, infos, issues);
    }

    private static int countSkipped(List<Issue> issues) {
        int count = 0;
        for (Issue issue : issues) {
            if (issue.severity() == Severity.ERROR) {
                count++;
            }
        }
        return count;
    }

    private static List<Issue> collectDistrict(
            BuildingProject project,
            DistrictGenerationResult district,
            Map<String, Double> previewHeights) {
        List<Issue> issues = new ArrayList<>();
        for (DistrictGenerationResult.BuildingOutcome skipped : district.skippedOutcomes()) {
            issues.add(new Issue(
                Kind.SKIPPED,
                Severity.ERROR,
                skipped.buildingId(),
                skipped.buildingName(),
                "",
                "",
                skipMessageKey(skipped.skipReason()),
                skipped.errorDetail() != null ? skipped.errorDetail() : ""));
        }
        List<BuildingFootprint> previewed = previewedBuildings(project, district);
        for (DistrictOverlapAnalyzer.ClosePair pair : DistrictOverlapAnalyzer.findTooClosePairs(
                previewed, MAX_TOO_CLOSE_GAP_BLOCKS)) {
            issues.add(new Issue(
                Kind.TOO_CLOSE,
                Severity.WARNING,
                pair.buildingIdA(),
                pair.buildingNameA(),
                pair.buildingIdB(),
                pair.buildingNameB(),
                "plugin.building.issue.too_close",
                formatGapBlocks(pair.gapBlocks())));
        }
        for (DistrictGenerationResult.BuildingOutcome outcome : district.outcomes()) {
            if (!outcome.success()) {
                continue;
            }
            BuildingFootprint building = project != null ? project.getBuilding(outcome.buildingId()) : null;
            collectSizeWarnings(issues, building, outcome.buildingId(), outcome.buildingName(), previewHeights);
            if (outcome.result() == null) {
                continue;
            }
            for (String warningKey : outcome.result().warnings) {
                if (warningKey == null || warningKey.isBlank() || SUPPRESSED_WARNING_KEYS.contains(warningKey)) {
                    continue;
                }
                issues.add(new Issue(
                    Kind.BUILDING_WARNING,
                    Severity.WARNING,
                    outcome.buildingId(),
                    outcome.buildingName(),
                    "",
                    "",
                    warningKey,
                    ""));
            }
        }
        return issues;
    }

    private static List<Issue> collectSingle(
            BuildingProject project,
            BuildingGenerationResult single,
            String buildingId,
            Map<String, Double> previewHeights) {
        BuildingFootprint building = project != null ? project.getBuilding(buildingId) : null;
        String name = building != null ? building.getName() : buildingId;
        List<Issue> issues = new ArrayList<>();
        collectSizeWarnings(issues, building, buildingId, name, previewHeights);
        for (String warningKey : single.warnings) {
            if (warningKey == null || warningKey.isBlank() || SUPPRESSED_WARNING_KEYS.contains(warningKey)) {
                continue;
            }
            issues.add(new Issue(
                Kind.BUILDING_WARNING,
                Severity.WARNING,
                buildingId,
                name,
                "",
                "",
                warningKey,
                ""));
        }
        return issues;
    }

    private static void collectSizeWarnings(
            List<Issue> issues,
            BuildingFootprint building,
            String buildingId,
            String buildingName,
            Map<String, Double> previewHeights) {
        if (building == null) {
            return;
        }
        double height = BuildingMassingPreviewHeights.forBuilding(building, previewHeights);
        if (height > MAX_WARNING_HEIGHT_BLOCKS) {
            issues.add(new Issue(
                Kind.EXCESSIVE_HEIGHT,
                Severity.WARNING,
                buildingId,
                buildingName,
                "",
                "",
                "plugin.building.issue.excessive_height",
                Integer.toString((int) Math.ceil(height))));
        }
        double area = building.computeArea();
        if (area > MAX_WARNING_AREA_BLOCKS) {
            issues.add(new Issue(
                Kind.EXCESSIVE_AREA,
                Severity.WARNING,
                buildingId,
                buildingName,
                "",
                "",
                "plugin.building.issue.excessive_area",
                Integer.toString((int) Math.ceil(area))));
        }
    }

    private static List<BuildingFootprint> previewedBuildings(
            BuildingProject project,
            DistrictGenerationResult district) {
        List<BuildingFootprint> buildings = new ArrayList<>();
        if (project == null || district == null) {
            return buildings;
        }
        for (DistrictGenerationResult.BuildingOutcome outcome : district.outcomes()) {
            if (!outcome.success()) {
                continue;
            }
            BuildingFootprint building = project.getBuilding(outcome.buildingId());
            if (building != null) {
                buildings.add(building);
            }
        }
        return buildings;
    }

    private static String formatGapBlocks(double gapBlocks) {
        if (gapBlocks < 0.05) {
            return "0";
        }
        if (Math.abs(gapBlocks - Math.round(gapBlocks)) < 0.05) {
            return Integer.toString((int) Math.round(gapBlocks));
        }
        return String.format("%.1f", gapBlocks);
    }

    private static String skipMessageKey(DistrictGenerationResult.SkipReason reason) {
        if (reason == null) {
            return "plugin.building.issue.skip_error";
        }
        return switch (reason) {
            case EMPTY -> "plugin.building.issue.skip_empty";
            case INVALID -> "plugin.building.issue.skip_invalid";
            case SITE_ANALYSIS_FAILED -> "plugin.building.issue.skip_no_placement";
            case ERROR -> "plugin.building.issue.skip_error";
        };
    }

    public static Set<String> previewedBuildingIds(DistrictGenerationResult district, String singleBuildingId) {
        Set<String> ids = new LinkedHashSet<>();
        if (district != null) {
            for (DistrictGenerationResult.BuildingOutcome outcome : district.outcomes()) {
                if (outcome.success()) {
                    ids.add(outcome.buildingId());
                }
            }
            return ids;
        }
        if (singleBuildingId != null && !singleBuildingId.isBlank()) {
            ids.add(singleBuildingId);
        }
        return ids;
    }

    /** 地图高亮：仅 WARNING（跳过不在此标黄）。 */
    public static Set<String> warningBuildingIds(List<Issue> issues) {
        Set<String> ids = new LinkedHashSet<>();
        for (Issue issue : issues) {
            if (issue.severity() != Severity.WARNING) {
                continue;
            }
            if (issue.primaryBuildingId() != null && !issue.primaryBuildingId().isBlank()) {
                ids.add(issue.primaryBuildingId());
            }
            if (issue.kind() == Kind.TOO_CLOSE
                    && issue.secondaryBuildingId() != null
                    && !issue.secondaryBuildingId().isBlank()) {
                ids.add(issue.secondaryBuildingId());
            }
        }
        return ids;
    }
}
