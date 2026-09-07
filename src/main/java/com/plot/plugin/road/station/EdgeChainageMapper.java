package com.plot.plugin.road.station;

/**
 * 将边内采样路径累计距离映射到道路桩号。
 */
public final class EdgeChainageMapper {

    private EdgeChainageMapper() {
    }

    public static double toChainage(
            OrientedRoadSegment oriented,
            double geometryLocalCanvasDistance,
            double sampledPathLength) {
        if (oriented == null) {
            return geometryLocalCanvasDistance;
        }
        double geometryLocal = geometryLocalCanvasDistance;
        if (sampledPathLength > 1e-9 && oriented.length() > 1e-9) {
            geometryLocal = geometryLocalCanvasDistance / sampledPathLength * oriented.length();
        }
        return oriented.roadStationAtGeometryLocal(geometryLocal);
    }
}
