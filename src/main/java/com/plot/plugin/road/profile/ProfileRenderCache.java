package com.plot.plugin.road.profile;

import com.plot.plugin.road.vertical.FlatElevationProfileOverlay;
import com.plot.plugin.road.vertical.VerticalAlignmentProfileOverlay;

import java.util.List;
import java.util.Objects;

/** 纵断面图静态层缓存（地形/目标线/范围/交叉口），PVI 拖动不重建。 */
public record ProfileRenderCache(
        long cacheKey,
        RoadProfilePlotRange plotRange,
        List<RoadProfileIntersection> intersections) {

    public static long computeKey(
            String roadId,
            long networkRevision,
            long terrainRevision,
            RoadProfileChartData chart) {
        Objects.requireNonNull(roadId, "roadId");
        int chartFingerprint = chart == null ? 0 : Objects.hash(
            chart.totalStation(),
            chart.stations() != null ? chart.stations().size() : 0,
            chart.groundElevations() != null ? chart.groundElevations().hashCode() : 0,
            chart.previewElevations() != null ? chart.previewElevations().hashCode() : 0);
        return Objects.hash(roadId, networkRevision, terrainRevision, chartFingerprint);
    }

    public static ProfileRenderCache build(
            long cacheKey,
            RoadProfileChartData chart,
            VerticalAlignmentProfileOverlay design,
            List<ProfileControlPoint> controls,
            List<RoadProfileIntersection> intersections,
            FlatElevationProfileOverlay flatOverlay) {
        if (chart == null || !chart.hasProfileData()) {
            return new ProfileRenderCache(cacheKey, null, List.of());
        }
        RoadProfilePlotRange range = RoadProfileChartRenderer.plotRange(
            chart, design, controls, intersections, flatOverlay);
        List<RoadProfileIntersection> cachedIntersections = intersections != null
            ? List.copyOf(intersections)
            : List.of();
        return new ProfileRenderCache(cacheKey, range, cachedIntersections);
    }
}
