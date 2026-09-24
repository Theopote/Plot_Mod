package com.plot.plugin.building.ui;

import com.plot.plugin.building.model.BuildingFootprint;

import java.util.List;

/** 记录一次 Preview 对应的目标建筑、参数与投影指纹，用于判断 Preview 是否仍对当前 Scope 有效。 */
public final class BuildingPreviewIdentity {
    public enum Validity {
        NONE,
        VALID,
        STALE
    }

    private final List<String> targetIds;
    private final int contentFingerprint;
    private final boolean frameOnly;
    private final int projectionFingerprint;

    private BuildingPreviewIdentity(
            List<String> targetIds,
            int contentFingerprint,
            boolean frameOnly,
            int projectionFingerprint) {
        this.targetIds = targetIds;
        this.contentFingerprint = contentFingerprint;
        this.frameOnly = frameOnly;
        this.projectionFingerprint = projectionFingerprint;
    }

    public static BuildingPreviewIdentity capture(List<BuildingFootprint> targets) {
        return capture(targets, false, 0);
    }

    public static BuildingPreviewIdentity capture(
            List<BuildingFootprint> targets,
            boolean frameOnly,
            int projectionFingerprint) {
        if (targets == null || targets.isEmpty()) {
            return new BuildingPreviewIdentity(List.of(), 0, frameOnly, projectionFingerprint);
        }
        return new BuildingPreviewIdentity(
            normalizedTargetIds(targets),
            computeContentFingerprint(targets),
            frameOnly,
            projectionFingerprint);
    }

    public List<String> targetIds() {
        return targetIds;
    }

    public Validity validityAgainst(List<BuildingFootprint> currentTargets, boolean hasResult) {
        return validityAgainst(currentTargets, hasResult, false, 0);
    }

    public Validity validityAgainst(
            List<BuildingFootprint> currentTargets,
            boolean hasResult,
            boolean frameOnly) {
        return validityAgainst(currentTargets, hasResult, frameOnly, 0);
    }

    public Validity validityAgainst(
            List<BuildingFootprint> currentTargets,
            boolean hasResult,
            boolean frameOnly,
            int projectionFingerprint) {
        if (!hasResult) {
            return Validity.NONE;
        }
        if (currentTargets == null || currentTargets.isEmpty()) {
            return Validity.STALE;
        }
        if (!targetIds.equals(normalizedTargetIds(currentTargets))) {
            return Validity.STALE;
        }
        if (contentFingerprint != computeContentFingerprint(currentTargets)) {
            return Validity.STALE;
        }
        if (this.frameOnly != frameOnly) {
            return Validity.STALE;
        }
        if (this.projectionFingerprint != projectionFingerprint) {
            return Validity.STALE;
        }
        return Validity.VALID;
    }

    /** 目标与生成参数仍匹配，仅投影（视图范围等）可能已变化。 */
    public boolean matchesTargetsAndContent(List<BuildingFootprint> currentTargets, boolean frameOnly) {
        if (currentTargets == null || currentTargets.isEmpty()) {
            return false;
        }
        return targetIds.equals(normalizedTargetIds(currentTargets))
            && contentFingerprint == computeContentFingerprint(currentTargets)
            && this.frameOnly == frameOnly;
    }

    public boolean matchesProjection(int projectionFingerprint) {
        return this.projectionFingerprint == projectionFingerprint;
    }

    /** 目标 id 按字典序归一化；片区生成内部会重排，Scope 校验与列表顺序无关。 */
    private static List<String> normalizedTargetIds(List<BuildingFootprint> targets) {
        return targets.stream()
            .map(BuildingFootprint::getId)
            .sorted()
            .toList();
    }

    private static int computeContentFingerprint(List<BuildingFootprint> targets) {
        int hash = targets.size();
        for (BuildingFootprint building : targets) {
            hash ^= 31 * building.generationFingerprint();
        }
        return hash;
    }
}
