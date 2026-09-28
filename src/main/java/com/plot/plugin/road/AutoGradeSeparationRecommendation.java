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
        if (node.getElevatedRoadId() != null && !node.getElevatedRoadId().isBlank()) {
            return new AutoGradeSeparationRecommendation(null, evaluation);
        }
        return new AutoGradeSeparationRecommendation(evaluation.recommendedElevatedRoadId(), evaluation);
    }

    public boolean hasRecommendation() {
        return elevatedRoadId != null && !elevatedRoadId.isBlank();
    }

    public boolean warnsLockedChoice(RoadNode node) {
        return evaluation != null && evaluation.isLockedChoiceSteep(node);
    }

    public String adoptSuggestedElevatedRoadId(RoadNode node) {
        return evaluation != null ? evaluation.recommendedIfDifferentFromLock(node) : null;
    }
}
