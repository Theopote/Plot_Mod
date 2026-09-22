package com.plot.plugin.building.ui;

import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.DistrictGenerationResult;
import com.plot.plugin.building.generation.DistrictOverlapAnalyzer;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.BuildingProject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 从 Preview 结果提取可操作的生成问题列表。 */
public final class BuildingGenerationIssues {
    public enum Kind {
        SKIPPED,
        BUILDING_WARNING,
        OVERLAP
    }

    public record Issue(
            Kind kind,
            String primaryBuildingId,
            String primaryBuildingName,
            String secondaryBuildingId,
            String secondaryBuildingName,
            String detailKey,
            String detailArg) {
    }

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

    private static List<Issue> collectDistrict(DistrictGenerationResult district) {
        List<Issue> issues = new ArrayList<>();
        for (DistrictGenerationResult.BuildingOutcome skipped : district.skippedOutcomes()) {
            issues.add(new Issue(
                Kind.SKIPPED,
                skipped.buildingId(),
                skipped.buildingName(),
                "",
                "",
                skipped.skipReason() != null ? skipped.skipReason().i18nKey() : "",
                skipped.errorDetail() != null ? skipped.errorDetail() : ""));
        }
        for (DistrictOverlapAnalyzer.OverlapPair pair : district.overlappingBuildingPairs()) {
            issues.add(new Issue(
                Kind.OVERLAP,
                pair.buildingIdA(),
                pair.buildingNameA(),
                pair.buildingIdB(),
                pair.buildingNameB(),
                "plugin.building.issue.overlap_detail",
                ""));
        }
        for (DistrictGenerationResult.BuildingOutcome outcome : district.outcomes()) {
            if (!outcome.success() || outcome.result() == null) {
                continue;
            }
            for (String warningKey : outcome.result().warnings) {
                if (warningKey == null || warningKey.isBlank()) {
                    continue;
                }
                issues.add(new Issue(
                    Kind.BUILDING_WARNING,
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
        for (String warningKey : single.warnings) {
            if (warningKey == null || warningKey.isBlank()) {
                continue;
            }
            issues.add(new Issue(
                Kind.BUILDING_WARNING,
                buildingId,
                name,
                "",
                "",
                warningKey,
                ""));
        }
        return issues;
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

    public static Set<String> warningBuildingIds(List<Issue> issues) {
        Set<String> ids = new LinkedHashSet<>();
        for (Issue issue : issues) {
            switch (issue.kind()) {
                case SKIPPED, BUILDING_WARNING -> {
                    if (issue.primaryBuildingId() != null && !issue.primaryBuildingId().isBlank()) {
                        ids.add(issue.primaryBuildingId());
                    }
                }
                case OVERLAP -> {
                    if (issue.primaryBuildingId() != null && !issue.primaryBuildingId().isBlank()) {
                        ids.add(issue.primaryBuildingId());
                    }
                    if (issue.secondaryBuildingId() != null && !issue.secondaryBuildingId().isBlank()) {
                        ids.add(issue.secondaryBuildingId());
                    }
                }
            }
        }
        return ids;
    }
}
