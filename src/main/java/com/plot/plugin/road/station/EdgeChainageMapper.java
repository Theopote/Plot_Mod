package com.plot.plugin.road.station;

/**
 * 将边内采样路径累计距离映射到道路桩号。
 */
public final class EdgeChainageMapper {

    private EdgeChainageMapper() {
    }

    public static double toChainage(
            double segmentStartChainage,
            double localCanvasDistance,
            double sampledPathLength,
            double edgeLength) {
        if (localCanvasDistance <= 0.0) {
            return segmentStartChainage;
        }
        if (sampledPathLength <= 1e-9) {
            return segmentStartChainage + localCanvasDistance;
        }
        if (edgeLength <= 1e-9) {
            return segmentStartChainage + localCanvasDistance;
        }
        return segmentStartChainage + localCanvasDistance / sampledPathLength * edgeLength;
    }
}
