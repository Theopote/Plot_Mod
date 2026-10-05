package com.plot.plugin.road.profile;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.pipeline.profile.BuildHeightSample;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileQualityStatusTest {

    @Test
    void reportsWarningsForSteepGrade() {
        RoadProfileChartData chart = new RoadProfileChartData(
            "r1",
            100.0,
            List.of(0.0, 50.0, 100.0),
            List.of(64.0, 64.0, 64.0),
            List.of(64.0, 80.0, 64.0),
            List.of(64.0, 80.0, 64.0),
            List.of(),
            List.of(64.0, 64.0, 64.0),
            List.of(),
            List.of(),
            true,
            true,
            List.of(),
            List.of());
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setMaxSlope(5.0f);

        ProfileQualityStatus.Summary summary = ProfileQualityStatus.evaluate(chart, null, config);
        assertTrue(summary.hasWarnings());
        assertTrue(summary.dimensions().stream()
            .anyMatch(d -> d.labelKey().contains("grade_warning")));
    }

    @Test
    void flatProfileReportsAllGood() {
        RoadProfileChartData chart = new RoadProfileChartData(
            "r1",
            100.0,
            List.of(0.0, 50.0, 100.0),
            List.of(64.0, 64.0, 64.0),
            List.of(64.0, 65.0, 66.0),
            List.of(64.0, 65.0, 66.0),
            List.of(),
            List.of(64.0, 64.0, 64.0),
            List.of(),
            List.of(),
            true,
            true,
            List.of(),
            List.of());
        RoadSystemConfig config = new RoadSystemConfig("test");

        ProfileQualityStatus.Summary summary = ProfileQualityStatus.evaluate(chart, null, config);
        assertFalse(summary.hasWarnings());
    }

    @Test
    void smallTerrainAdjustmentRemainsGood() {
        RoadProfileChartData chart = chartWithUniformTerrainOffset(100.0, 64.0, 65.0, 11);

        ProfileQualityStatus.Summary summary = ProfileQualityStatus.evaluate(
            chart, null, new RoadSystemConfig("test"));

        assertEquals(
            ProfileQualityStatus.Level.GOOD,
            terrainLevel(summary),
            "minimal terrain adjustment should stay GOOD");
    }

    @Test
    void moderateTerrainFollowingDoesNotAlwaysWarn() {
        RoadProfileChartData chart = chartWithUniformTerrainOffset(100.0, 64.0, 66.0, 11);

        ProfileQualityStatus.Summary summary = ProfileQualityStatus.evaluate(
            chart, null, new RoadSystemConfig("test"));

        assertEquals(
            ProfileQualityStatus.Level.GOOD,
            terrainLevel(summary),
            "moderate terrain following should remain below warning threshold");
    }

    @Test
    void largeTerrainModificationWarns() {
        RoadProfileChartData chart = chartWithUniformTerrainOffset(100.0, 64.0, 67.0, 11);

        ProfileQualityStatus.Summary summary = ProfileQualityStatus.evaluate(
            chart, null, new RoadSystemConfig("test"));

        assertEquals(
            ProfileQualityStatus.Level.WARNING,
            terrainLevel(summary),
            "3 block average modification should warn");
    }

    @Test
    void terrainStatusIsStableAcrossSampleDensity() {
        RoadProfileChartData sparse = chartWithUniformTerrainOffset(100.0, 64.0, 65.0, 6);
        RoadProfileChartData dense = chartWithUniformTerrainOffset(100.0, 64.0, 65.0, 101);

        ProfileQualityStatus.Level sparseLevel = terrainLevel(
            ProfileQualityStatus.evaluate(sparse, null, new RoadSystemConfig("test")));
        ProfileQualityStatus.Level denseLevel = terrainLevel(
            ProfileQualityStatus.evaluate(dense, null, new RoadSystemConfig("test")));

        assertEquals(sparseLevel, denseLevel,
            "same average terrain modification should yield the same terrain status");
    }

    private static ProfileQualityStatus.Level terrainLevel(ProfileQualityStatus.Summary summary) {
        return summary.dimensions().stream()
            .filter(d -> d.labelKey().contains("terrain"))
            .map(ProfileQualityStatus.Dimension::level)
            .findFirst()
            .orElseThrow();
    }

    private static RoadProfileChartData chartWithUniformTerrainOffset(
            double totalLength,
            double groundY,
            double buildY,
            int stationCount) {
        List<Double> stations = new ArrayList<>();
        List<Double> ground = new ArrayList<>();
        List<Double> preview = new ArrayList<>();
        List<BuildHeightSample> buildSamples = new ArrayList<>();
        for (int i = 0; i < stationCount; i++) {
            double station = totalLength * i / (stationCount - 1);
            stations.add(station);
            ground.add(groundY);
            preview.add(buildY);
            buildSamples.add(new BuildHeightSample(station, buildY, (int) Math.round(buildY)));
        }
        return new RoadProfileChartData(
            "terrain-test",
            totalLength,
            stations,
            ground,
            preview,
            preview,
            buildSamples,
            ground,
            List.of(),
            List.of(),
            true,
            true,
            List.of(),
            List.of());
    }
}
