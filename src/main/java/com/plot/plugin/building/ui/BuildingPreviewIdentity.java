package com.plot.plugin.building.ui;

import com.plot.plugin.building.model.BuildingFootprint;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

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
        List<String> ids = sortedIds(targets);
        return new BuildingPreviewIdentity(ids, computeContentFingerprint(targets));
    }

    public Validity validityAgainst(List<BuildingFootprint> currentTargets, boolean hasResult) {
        if (!hasResult) {
            return Validity.NONE;
        }
        if (currentTargets == null || currentTargets.isEmpty()) {
            return Validity.STALE;
        }
        if (!targetIds.equals(sortedIds(currentTargets))) {
            return Validity.STALE;
        }
        if (contentFingerprint != computeContentFingerprint(currentTargets)) {
            return Validity.STALE;
        }
        return Validity.VALID;
    }

    private static List<String> sortedIds(List<BuildingFootprint> targets) {
        return targets.stream()
            .map(BuildingFootprint::getId)
            .sorted()
            .toList();
    }

    private static int computeContentFingerprint(List<BuildingFootprint> targets) {
        int hash = targets.size();
        List<BuildingFootprint> sorted = targets.stream()
            .sorted(Comparator.comparing(BuildingFootprint::getId))
            .toList();
        for (BuildingFootprint building : sorted) {
            hash = 31 * hash + building.geometryFingerprint();
            hash = 31 * hash + building.getFloors();
            hash = 31 * hash + building.getFloorHeight();
            hash = 31 * hash + building.getWallThickness();
            hash = 31 * hash + Objects.hashCode(building.getRoofType());
            hash = 31 * hash + building.getRoofPitchRatio();
            hash = 31 * hash + Objects.hashCode(building.getPresetId());
        }
        return hash;
    }
}
