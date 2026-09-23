package com.plot.plugin.building.ui;

import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.DistrictGenerationResult;
import com.plot.plugin.building.generation.DistrictOverlapAnalyzer;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.BuildingProject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
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
        TERRAIN_FIT,
        BUILDING_WARNING,
        OVERLAP
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

    private static final Set<String> TERRAIN_WARNING_KEYS = Set.of(
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

    private static final Set<String> SUPPRESSED_WARNING_KEYS = Set.of(
        "plugin.building.warn.district_partial",
        "plugin.building.warn.district_overlap",
        "plugin.building.warn.floor_plate_coverage_gap",
        "plugin.building.warn.using_earthwork_pad_elevation",
        "plugin.building.warn.earthwork_pad_unresolved_using_terrain",
        "plugin.building.warn.site_analysis_failed",
        "plugin.building.warn.site_analysis_unavailable_skip",
        "plugin.building.warn.tree_clear_limit");

    private BuildingGenerationIssues() {
    }

    public static List<Issue> collect(
            BuildingProject project,
            DistrictGenerationResult district,
            BuildingGenerationResult single,
            BuildingPreviewIdentity previewIdentity,
            boolean districtMode) {
        if (districtMode && district != null) {
            return collectDistrict(district);
        }
        if (single != null && previewIdentity != null && !previewIdentity.targetIds().isEmpty()) {
            return collectSingle(project, single, previewIdentity.targetIds().getFirst());
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

    private static List<Issue> collectDistrict(DistrictGenerationResult district) {
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
        for (DistrictOverlapAnalyzer.OverlapPair pair : district.overlappingBuildingPairs()) {
            issues.add(new Issue(
                Kind.OVERLAP,
                Severity.INFO,
                pair.buildingIdA(),
                pair.buildingNameA(),
                pair.buildingIdB(),
                pair.buildingNameB(),
                "plugin.building.issue.overlap_info",
                ""));
        }
        Set<String> terrainNoted = new HashSet<>();
        for (DistrictGenerationResult.BuildingOutcome outcome : district.outcomes()) {
            if (!outcome.success() || outcome.result() == null) {
                continue;
            }
            boolean terrainAdded = false;
            for (String warningKey : outcome.result().warnings) {
                if (warningKey == null || warningKey.isBlank() || SUPPRESSED_WARNING_KEYS.contains(warningKey)) {
                    continue;
                }
                if (TERRAIN_WARNING_KEYS.contains(warningKey)) {
                    if (!terrainAdded && terrainNoted.add(outcome.buildingId())) {
                        terrainAdded = true;
                        issues.add(new Issue(
                            Kind.TERRAIN_FIT,
                            Severity.WARNING,
                            outcome.buildingId(),
                            outcome.buildingName(),
                            "",
                            "",
                            "plugin.building.issue.terrain_fit",
                            ""));
                    }
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
            String buildingId) {
        BuildingFootprint building = project != null ? project.getBuilding(buildingId) : null;
        String name = building != null ? building.getName() : buildingId;
        List<Issue> issues = new ArrayList<>();
        boolean terrainAdded = false;
        for (String warningKey : single.warnings) {
            if (warningKey == null || warningKey.isBlank() || SUPPRESSED_WARNING_KEYS.contains(warningKey)) {
                continue;
            }
            if (TERRAIN_WARNING_KEYS.contains(warningKey)) {
                if (!terrainAdded) {
                    terrainAdded = true;
                    issues.add(new Issue(
                        Kind.TERRAIN_FIT,
                        Severity.WARNING,
                        buildingId,
                        name,
                        "",
                        "",
                        "plugin.building.issue.terrain_fit",
                        ""));
                }
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

    /** 地图高亮：仅 WARNING（跳过与重叠不在此标黄）。 */
    public static Set<String> warningBuildingIds(List<Issue> issues) {
        Set<String> ids = new LinkedHashSet<>();
        for (Issue issue : issues) {
            if (issue.severity() != Severity.WARNING) {
                continue;
            }
            if (issue.primaryBuildingId() != null && !issue.primaryBuildingId().isBlank()) {
                ids.add(issue.primaryBuildingId());
            }
        }
        return ids;
    }
}
