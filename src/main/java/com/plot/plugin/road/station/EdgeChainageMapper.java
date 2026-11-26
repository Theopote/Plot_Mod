package com.plot.plugin.road.station;

/**
 * 将边内采样路径累计距离映射到道路 instance chainage（OrientedRoadSegment 域）。
 * Canonical 转换由 {@link RoadStationing#toCanonicalChainage} 完成。
 * <p>
 * Prefer scaling by the <em>physical Edge</em> length when known; never scale a full-edge
 * canvas distance by a LOOP seam slice length.
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
        return toInstanceChainage(oriented, geometryLocalCanvasDistance, sampledPathLength, Double.NaN);
    }

    /**
     * @param physicalEdgeLength when finite and &gt; 0, used for canvas→geometryLocal scaling
     *                           instead of {@code oriented.length()} (slice-safe).
     */
    public static double toInstanceChainage(
            OrientedRoadSegment oriented,
            double geometryLocalCanvasDistance,
            double sampledPathLength,
            double physicalEdgeLength) {
        if (oriented == null) {
            return geometryLocalCanvasDistance;
        }
        double scaleLength = Double.isFinite(physicalEdgeLength) && physicalEdgeLength > 1e-9
            ? physicalEdgeLength
            : oriented.length();
        double geometryLocal = geometryLocalCanvasDistance;
        if (sampledPathLength > 1e-9 && scaleLength > 1e-9) {
            geometryLocal = geometryLocalCanvasDistance / sampledPathLength * scaleLength;
        }
        return oriented.roadStationAtGeometryLocal(geometryLocal);
    }
}
