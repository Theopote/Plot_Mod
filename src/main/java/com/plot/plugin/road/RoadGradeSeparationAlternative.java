package com.plot.plugin.road;

/**
 * 某一立交方案（指定道路在上跨）的粗略可行性估计。
 */
public record RoadGradeSeparationAlternative(
        String elevatedRoadId,
        String underpassRoadId,
        float estimatedMaxGradePercent,
        float elevationLiftBlocks,
        boolean exceedsSlopeLimit,
        float score) {
}
