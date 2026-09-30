package com.plot.plugin.road;

import com.plot.plugin.road.model.RoadNode;

/**
 * 立体交叉推荐：自动模式下的上跨道路，以及锁定模式下的坡度警告。
 */
public record AutoGradeSeparationRecommendation(
        String elevatedRoadId,
        RoadGradeSeparationEvaluation evaluation) {

    public static AutoGradeSeparationRecommendation none() {
        return new AutoGradeSeparationRecommendation(null, null);
    }

    public static AutoGradeSeparationRecommendation fromEvaluation(
            RoadGradeSeparationEvaluation evaluation,
            RoadNode node) {
        if (evaluation == null || node == null || !node.isGradeSeparated()) {
            return none();
        }
        return fromEvaluation(evaluation, node.getElevatedRoadId());
    }

    public static AutoGradeSeparationRecommendation fromEvaluation(
            RoadGradeSeparationEvaluation evaluation,
            String lockedElevatedRoadId) {
        if (evaluation == null) {
            return none();
        }
        if (lockedElevatedRoadId != null && !lockedElevatedRoadId.isBlank()) {
            return new AutoGradeSeparationRecommendation(null, evaluation);
        }
        return new AutoGradeSeparationRecommendation(evaluation.recommendedElevatedRoadId(), evaluation);
    }

    public boolean hasRecommendation() {
        return elevatedRoadId != null && !elevatedRoadId.isBlank();
    }

    public boolean warnsLockedChoice(RoadNode node) {
        return node != null && warnsLockedChoice(node.getElevatedRoadId());
    }

    public boolean warnsLockedChoice(String lockedElevatedRoadId) {
        return evaluation != null && evaluation.isLockedChoiceSteep(lockedElevatedRoadId);
    }

    public String adoptSuggestedElevatedRoadId(RoadNode node) {
        return node == null ? null : adoptSuggestedElevatedRoadId(node.getElevatedRoadId());
    }

    public String adoptSuggestedElevatedRoadId(String lockedElevatedRoadId) {
        return evaluation != null ? evaluation.recommendedIfDifferentFromLock(lockedElevatedRoadId) : null;
    }
}
