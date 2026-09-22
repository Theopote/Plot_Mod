package com.plot.plugin.building.ui;

import com.plot.plugin.building.model.BuildingFootprint;

import java.util.List;

/** 记录一次 Preview 对应的目标建筑与参数指纹，用于判断 Preview 是否仍对当前 Scope 有效。 */
public final class BuildingPreviewIdentity {
    public enum Validity {
        NONE,
        VALID,
        STALE
    }

    private final List<String> targetIds;
    private final int contentFingerprint;

    private BuildingPreviewIdentity(List<String> targetIds, int contentFingerprint) {
        this.targetIds = targetIds;
        this.contentFingerprint = contentFingerprint;
    }

    public static BuildingPreviewIdentity capture(List<BuildingFootprint> targets) {
        if (targets == null || targets.isEmpty()) {
            return new BuildingPreviewIdentity(List.of(), 0);
        }
        return new BuildingPreviewIdentity(orderedIds(targets), computeContentFingerprint(targets));
    }

    public List<String> targetIds() {
        return targetIds;
    }

    public Validity validityAgainst(List<BuildingFootprint> currentTargets, boolean hasResult) {
        if (!hasResult) {
            return Validity.NONE;
        }
        if (currentTargets == null || currentTargets.isEmpty()) {
            return Validity.STALE;
        }
        if (!targetIds.equals(orderedIds(currentTargets))) {
            return Validity.STALE;
        }
        if (contentFingerprint != computeContentFingerprint(currentTargets)) {
            return Validity.STALE;
        }
        return Validity.VALID;
    }

    /** 生成顺序（District later-wins 与 selection 顺序相关，不做排序）。 */
    private static List<String> orderedIds(List<BuildingFootprint> targets) {
        return targets.stream()
            .map(BuildingFootprint::getId)
            .toList();
    }

    private static int computeContentFingerprint(List<BuildingFootprint> targets) {
        int hash = targets.size();
        for (BuildingFootprint building : targets) {
            hash = 31 * hash + building.generationFingerprint();
        }
        return hash;
    }
}
