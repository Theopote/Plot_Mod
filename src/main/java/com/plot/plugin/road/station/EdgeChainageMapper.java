package com.plot.plugin.road.station;

/**
 * 将边内采样路径累计距离映射到道路 instance chainage（OrientedRoadSegment 域）。
 * Canonical 转换由 {@link RoadStationing#toCanonicalChainage} 完成。
 */
public final class EdgeChainageMapper {

    private EdgeChainageMapper() {
    }

    /** @deprecated use {@link #toInstanceChainage} then canonical conversion */
    @Deprecated
    public static double toChainage(
            OrientedRoadSegment oriented,
            double geometryLocalCanvasDistance,
            double sampledPathLength) {
        return toInstanceChainage(oriented, geometryLocalCanvasDistance, sampledPathLength);
    }

    public static double toInstanceChainage(
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
