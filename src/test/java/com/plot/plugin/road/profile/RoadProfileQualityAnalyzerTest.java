package com.plot.plugin.road.profile;

import com.plot.plugin.road.pipeline.profile.BuildHeightSample;
import com.plot.plugin.road.pipeline.profile.RoadHeightRasterizer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadProfileQualityAnalyzerTest {

    @Test
    void estimatesFillWhenRoadbedIsAboveTerrain() {
        List<BuildHeightSample> samples = List.of(
            new BuildHeightSample(0.0, 64.0, 66),
            new BuildHeightSample(20.0, 66.0, 66));

        RoadProfileQualityMetrics metrics = RoadProfileQualityAnalyzer.analyze(
            chartWithSamples(samples, ground(64.0, 20.0)),
            8.0,
            4.0);

        assertEquals(20, metrics.fillBlockColumns());
        assertEquals(0, metrics.cutBlockColumns());
    }

    @Test
    void estimatesCutWhenRoadbedIsBelowTerrain() {
        List<BuildHeightSample> samples = List.of(
            new BuildHeightSample(0.0, 64.0, 62),
            new BuildHeightSample(20.0, 64.0, 62));

        RoadProfileQualityMetrics metrics = RoadProfileQualityAnalyzer.analyze(
            chartWithSamples(samples, ground(64.0, 20.0)),
            8.0,
            4.0);

        assertEquals(0, metrics.fillBlockColumns());
        assertEquals(20, metrics.cutBlockColumns());
    }

    @Test
    void detectsAbnormalMultiBlockStep() {
        List<BuildHeightSample> samples = List.of(
            new BuildHeightSample(0.0, 64.0, 64),
            new BuildHeightSample(10.0, 66.0, 66));

        RoadProfileChartData chart = chartWithSamples(samples, ground(64.0, 10.0));
        RoadProfileQualityMetrics metrics = RoadProfileQualityAnalyzer.analyze(chart, 8.0, 4.0);

        assertEquals(1, metrics.abnormalStepCount());
        assertEquals(1, metrics.stepCount());
        assertTrue(metrics.maxBuildGradePercent() > 15.0);
        assertEquals(10.0, metrics.longestConstantGradeRun(), 1e-6);
    }

    @Test
    void rasterizedProfileProducesGradeRunAndSteps() {
        RoadHeightRasterizer.RasterizationResult raster = RoadHeightRasterizer.rasterize(
            List.of(64.0, 65.0), List.of(40.0), List.of(5.0f), null);

        List<BuildHeightSample> samples = new ArrayList<>();
        for (BuildHeightSample sample : raster.samples()) {
            samples.add(new BuildHeightSample(
                sample.station(),
                sample.designElevation(),
                sample.buildY()));
        }
        RoadProfileChartData chart = chartWithSamples(samples, ground(64.0, 40.0));

        RoadProfileQualityMetrics metrics = RoadProfileQualityAnalyzer.analyze(chart, 8.0, 4.0);

        assertEquals(1, metrics.stepCount());
        assertEquals(0, metrics.abnormalStepCount());
        assertTrue(metrics.longestFlatRun() >= 18,
            "5% grade should accumulate a long flat run before the first step");
        assertTrue(metrics.longestConstantGradeRun() >= 18.0,
            "step spacing should define the constant-grade run length");
        assertTrue(metrics.maxBuildGradePercent() <= 8.0);
        assertTrue(metrics.maxDesignBuildDeviation() <= 1.0);
    }

    @Test
    void longestFlatRunMatchesRasterizerSemantics() {
        List<BuildHeightSample> samples = List.of(
            new BuildHeightSample(0.0, 64.0, 64),
            new BuildHeightSample(1.0, 64.0, 64),
            new BuildHeightSample(2.0, 64.0, 64),
            new BuildHeightSample(3.0, 65.0, 65));

        assertEquals(3, RoadProfileQualityAnalyzer.longestFlatRun(samples));
    }

    private static List<Double> ground(double elevation, double length) {
        List<Double> stations = List.of(0.0, length);
        return List.of(elevation, elevation);
    }

    private static RoadProfileChartData chartWithSamples(
            List<BuildHeightSample> samples,
            List<Double> groundElevations) {
        double total = samples.getLast().station();
        List<Double> stations = List.of(0.0, total);
        List<Double> design = List.of(
            samples.getFirst().designElevation(),
            samples.getLast().designElevation());
        List<Double> buildSummary = List.of(
            (double) samples.getFirst().buildY(),
            (double) samples.getLast().buildY());
        return new RoadProfileChartData(
            "test",
            total,
            stations,
            groundElevations,
            design,
            buildSummary,
            samples,
            groundElevations,
            List.of(),
            List.of(),
            true);
    }
}
