package com.plot.plugin.road.profile;

import com.plot.plugin.road.profile.RoadProfileChartRenderer.ElevationBounds;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileRenderCacheTest {

    @Test
    void sameKeyReusesBuiltCache() {
        RoadProfileChartData chart = sampleChart(100.0);
        long key = ProfileRenderCache.computeKey("road-a", 1L, 2L, chart);
        ProfileRenderCache first = ProfileRenderCache.build(key, chart, List.of(), null);
        ProfileRenderCache second = ProfileRenderCache.build(key, chart, List.of(), null);

        assertEquals(first.cacheKey(), second.cacheKey());
        assertNotNull(first.staticPlotRange());
        assertEquals(first.staticPlotRange().totalStation(), second.staticPlotRange().totalStation());
    }

    @Test
    void cacheKeyChangesWhenRevisionOrChartChanges() {
        RoadProfileChartData chartA = sampleChart(100.0);
        RoadProfileChartData chartB = sampleChart(200.0);
        long keyA = ProfileRenderCache.computeKey("road-a", 1L, 2L, chartA);
        long keyB = ProfileRenderCache.computeKey("road-a", 2L, 2L, chartA);
        long keyC = ProfileRenderCache.computeKey("road-a", 1L, 2L, chartB);

        assertNotEquals(keyA, keyB);
        assertNotEquals(keyA, keyC);
    }

    @Test
    void resolvePlotRangeReusesStaticRangeWhenDraftFits() {
        RoadProfileChartData chart = sampleChart(100.0);
        ProfileRenderCache cache = ProfileRenderCache.build(
            ProfileRenderCache.computeKey("road-a", 1L, 2L, chart), chart, List.of(), null);
        RoadProfilePlotRange resolved = RoadProfileChartRenderer.resolvePlotRange(
            cache, chart, null, List.of(), List.of(), null);

        assertEquals(cache.staticPlotRange(), resolved);
    }

    @Test
    void resolvePlotRangeExpandsWhenDraftExceedsStaticBounds() {
        RoadProfileChartData chart = sampleChart(100.0);
        ProfileRenderCache cache = ProfileRenderCache.build(
            ProfileRenderCache.computeKey("road-a", 1L, 2L, chart), chart, List.of(), null);
        ElevationBounds draftOnly = new ElevationBounds(10.0, 120.0);
        RoadProfilePlotRange expanded = RoadProfileChartRenderer.toPlotRange(chart.totalStation(), draftOnly);

        RoadProfilePlotRange resolved = RoadProfileChartRenderer.resolvePlotRange(
            cache,
            chart,
            new com.plot.plugin.road.vertical.VerticalAlignmentProfileOverlay(
                List.of(0.0, 100.0), List.of(10.0, 120.0)),
            List.of(),
            List.of(),
            null);

        assertTrue(resolved.minElevation() <= expanded.minElevation());
        assertTrue(resolved.maxElevation() >= expanded.maxElevation());
        assertTrue(resolved.minElevation() < cache.staticPlotRange().minElevation()
            || resolved.maxElevation() > cache.staticPlotRange().maxElevation());
    }

    private static RoadProfileChartData sampleChart(double totalStation) {
        List<Double> stations = List.of(0.0, totalStation);
        List<Double> ground = List.of(60.0, 62.0);
        return new RoadProfileChartData(
            "road-a",
            totalStation,
            stations,
            ground,
            ground,
            ground,
            List.of(),
            ground,
            List.of(),
            List.of(),
            true);
    }
}
