package com.plot.plugin.road;

import com.plot.plugin.road.model.RoadNode;

/**
 * 两条道路在简单十字交叉点上的两种立交方案对比。
 */
public record RoadGradeSeparationEvaluation(
        String roadIdA,
        String roadIdB,
        RoadGradeSeparationAlternative roadAOverB,
        RoadGradeSeparationAlternative roadBOverA,
        boolean terrainAnalyzed) {

    public String recommendedElevatedRoadId() {
        if (roadAOverB == null || roadBOverA == null) {
            return null;
        }
        if (roadAOverB.score() < roadBOverA.score()) {
            return roadAOverB.elevatedRoadId();
        }
        if (roadBOverA.score() < roadAOverB.score()) {
            return roadBOverA.elevatedRoadId();
        }
        return roadAOverB.elevatedRoadId();
    }

    public RoadGradeSeparationAlternative alternativeFor(String elevatedRoadId) {
        if (elevatedRoadId == null || elevatedRoadId.isBlank()) {
            return null;
        }
        if (roadAOverB != null && elevatedRoadId.equals(roadAOverB.elevatedRoadId())) {
            return roadAOverB;
        }
        if (roadBOverA != null && elevatedRoadId.equals(roadBOverA.elevatedRoadId())) {
            return roadBOverA;
        }
        return null;
    }

    public boolean isLockedChoiceSteep(RoadNode node) {
        return node != null && isLockedChoiceSteep(node.getElevatedRoadId());
    }

    public boolean isLockedChoiceSteep(String lockedElevatedRoadId) {
        if (lockedElevatedRoadId == null || lockedElevatedRoadId.isBlank()) {
            return false;
        }
        RoadGradeSeparationAlternative locked = alternativeFor(lockedElevatedRoadId);
        return locked != null && locked.exceedsSlopeLimit();
    }

    public String recommendedIfDifferentFromLock(RoadNode node) {
        return node == null ? recommendedElevatedRoadId() : recommendedIfDifferentFromLock(node.getElevatedRoadId());
    }

    public String recommendedIfDifferentFromLock(String lockedElevatedRoadId) {
        if (lockedElevatedRoadId == null || lockedElevatedRoadId.isBlank()) {
            return recommendedElevatedRoadId();
        }
        String recommended = recommendedElevatedRoadId();
        if (recommended == null || recommended.equals(lockedElevatedRoadId)) {
            return null;
        }
        return recommended;
    }
}
