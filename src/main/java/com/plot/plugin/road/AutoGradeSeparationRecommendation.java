package com.plot.plugin.road;

/**
 * 立体交叉「自动判断」模式下，程序推荐的高架道路。
 */
public record AutoGradeSeparationRecommendation(String elevatedRoadId) {

    public static AutoGradeSeparationRecommendation none() {
        return new AutoGradeSeparationRecommendation(null);
    }

    public boolean hasRecommendation() {
        return elevatedRoadId != null && !elevatedRoadId.isBlank();
    }
}
