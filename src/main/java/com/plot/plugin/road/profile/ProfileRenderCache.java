package com.plot.plugin.road.profile;

import com.plot.plugin.road.vertical.FlatElevationProfileOverlay;

import java.util.List;
import java.util.Objects;

/** 纵断面图静态层缓存（地形/目标线/范围/交叉口），PVI 拖动不重建。 */
public record ProfileRenderCache(
        long cacheKey,
        double totalStation,
        RoadProfilePlotRange staticPlotRange,
        double staticMinRaw,
        double staticMaxRaw,
        List<RoadProfileIntersection> intersections) {

    /** 兼容旧字段名。 */
    public RoadProfilePlotRange plotRange() {
        return staticPlotRange();
    }

    RoadProfileChartRenderer.ElevationBounds staticRawBounds() {
        return new RoadProfileChartRenderer.ElevationBounds(staticMinRaw, staticMaxRaw);
    }

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
            chart.previewElevations() != null ? chart.previewElevations().hashCode() : 0,
            chart.buildElevations() != null ? chart.buildElevations().hashCode() : 0,
            chart.buildSamples() != null ? chart.buildSamples().hashCode() : 0);
        return Objects.hash(roadId, networkRevision, terrainRevision, chartFingerprint);
    }

    public static ProfileRenderCache build(
            long cacheKey,
            RoadProfileChartData chart,
            List<RoadProfileIntersection> intersections,
            FlatElevationProfileOverlay flatOverlay) {
        if (chart == null || !chart.hasProfileData()) {
            return new ProfileRenderCache(cacheKey, 0.0, null, 62.0, 66.0, List.of());
        }
        RoadProfileChartRenderer.ElevationBounds staticBounds =
            RoadProfileChartRenderer.staticChartBounds(chart, flatOverlay);
        if (staticBounds == null) {
            staticBounds = new RoadProfileChartRenderer.ElevationBounds(62.0, 66.0);
        }
        RoadProfilePlotRange staticRange =
            RoadProfileChartRenderer.toPlotRange(chart.totalStation(), staticBounds);
        List<RoadProfileIntersection> cachedIntersections = intersections != null
            ? List.copyOf(intersections)
            : List.of();
        return new ProfileRenderCache(
            cacheKey,
            chart.totalStation(),
            staticRange,
            staticBounds.min(),
            staticBounds.max(),
            cachedIntersections);
    }
}
