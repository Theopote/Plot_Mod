package com.plot.plugin.road.profile;

import com.plot.plugin.road.model.section.ResolvedCrossSection;

/**
 * 纵剖面上的道路交叉事件：将路网节点处的平面交叉关系投影到当前边的局部里程。
 */
public record RoadProfileIntersection(
        String nodeId,
        String currentRoadId,
        String otherRoadId,
        String otherRoadLabel,
        double localDistance,
        double roadStation,
        double currentRoadElevation,
        double otherRoadElevation,
        ResolvedCrossSection otherCrossSection,
        boolean gradeSeparated,
        boolean currentRoadElevated,
        double clearance) {

    public double clearanceGap() {
        return Math.abs(currentRoadElevation - otherRoadElevation);
    }
}
