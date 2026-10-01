package com.plot.plugin.road.profile;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProfileRenderCacheTest {

    @Test
    void sameKeyReusesBuiltCache() {
        RoadProfileChartData chart = sampleChart(100.0);
        long key = ProfileRenderCache.computeKey("road-a", 1L, 2L, chart);
        ProfileRenderCache first = ProfileRenderCache.build(key, chart, null, List.of(), List.of(), null);
        ProfileRenderCache second = ProfileRenderCache.build(key, chart, null, List.of(), List.of(), null);

        assertEquals(first.cacheKey(), second.cacheKey());
        assertNotNull(first.plotRange());
        assertEquals(first.plotRange().totalStation(), second.plotRange().totalStation());
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

    private static RoadProfileChartData sampleChart(double totalStation) {
        List<Double> stations = List.of(0.0, totalStation);
        List<Double> ground = List.of(60.0, 62.0);
        return new RoadProfileChartData(
            "road-a",
            totalStation,
            stations,
            ground,
            ground,
            List.of(),
            List.of(),
            List.of());
    }
}
