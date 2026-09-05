package com.plot.plugin.building.site;

import java.util.Collections;
import java.util.EnumSet;
import java.util.OptionalInt;
import java.util.Set;

/**
 * 单栋建筑场地分析结果（只读）。
 */
public record BuildingSiteAnalysis(
        int sampledColumnCount,
        int minGroundElevation,
        int maxGroundElevation,
        int dominantGroundElevation,
        int medianGroundElevation,
        int balancedGroundElevation,
        int groundElevationRange,
        int waterColumnCount,
        double waterCoverageRatio,
        Integer dominantWaterElevation,
        Integer maxWaterElevation,
        int naturalDecorationCount,
        int structureConflictCount,
        int estimatedCutVolume,
        int estimatedFillVolume,
        Set<SiteIssue> issues) {

    public BuildingSiteAnalysis {
        issues = issues == null || issues.isEmpty()
            ? Set.of()
            : Collections.unmodifiableSet(EnumSet.copyOf(issues));
    }

    public static BuildingSiteAnalysis emptyFallback(int defaultElevation) {
        return new BuildingSiteAnalysis(
            0,
            defaultElevation,
            defaultElevation,
            defaultElevation,
            defaultElevation,
            defaultElevation,
            0,
            0,
            0.0,
            null,
            null,
            0,
            0,
            0,
            0,
            Set.of());
    }

    public boolean hasIssue(SiteIssue issue) {
        return issues.contains(issue);
    }

    public OptionalInt dominantWaterElevationOptional() {
        return dominantWaterElevation == null
            ? OptionalInt.empty()
            : OptionalInt.of(dominantWaterElevation);
    }
}
