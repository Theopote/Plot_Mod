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
            chartWithSamples(samples, ground(64.0, 20.0)));

        assertEquals(20, metrics.terrain().fillBlockColumns());
        assertEquals(0, metrics.terrain().cutBlockColumns());
    }

    @Test
    void estimatesCutWhenRoadbedIsBelowTerrain() {
        List<BuildHeightSample> samples = List.of(
            new BuildHeightSample(0.0, 64.0, 62),
            new BuildHeightSample(20.0, 64.0, 62));

        RoadProfileQualityMetrics metrics = RoadProfileQualityAnalyzer.analyze(
            chartWithSamples(samples, ground(64.0, 20.0)));

        assertEquals(0, metrics.terrain().fillBlockColumns());
        assertEquals(20, metrics.terrain().cutBlockColumns());
    }

    @Test
    void detectsAbnormalMultiBlockStep() {
        List<BuildHeightSample> samples = List.of(
            new BuildHeightSample(0.0, 64.0, 64),
            new BuildHeightSample(10.0, 66.0, 66));

        RoadProfileQualityMetrics metrics = RoadProfileQualityAnalyzer.analyze(
            chartWithSamples(samples, ground(64.0, 10.0)));

        assertEquals(1, metrics.build().abnormalStepCount());
        assertEquals(1, metrics.build().stepCount());
    }

    @Test
    void designMetricsDetectCrestGradeChange() {
        RoadProfileChartData chart = chartWithDesignProfile(
            List.of(0.0, 10.0, 20.0, 30.0),
            List.of(64.0, 64.5, 65.0, 64.5),
            List.of(
                new BuildHeightSample(0.0, 64.0, 64),
                new BuildHeightSample(30.0, 64.5, 64)),
            List.of(64.0, 64.0, 64.0, 64.0));

        RoadProfileQualityMetrics metrics = RoadProfileQualityAnalyzer.analyze(chart);

        assertEquals(5.0, metrics.design().maxGradePercent(), 0.1);
        assertEquals(10.0, metrics.design().maxGradeChangePercent(), 0.1);
    }

    @Test
    void rasterizedProfileProducesExpectedBuildMetrics() {
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

        RoadProfileQualityMetrics metrics = RoadProfileQualityAnalyzer.analyze(chart);

        assertEquals(1, metrics.build().stepCount());
        assertEquals(0, metrics.build().abnormalStepCount());
        assertTrue(metrics.build().longestFlatRun() >= 18);
        assertTrue(metrics.design().maxGradePercent() <= 8.0);
        assertTrue(metrics.build().maxDesignBuildDeviation() <= 1.0);
    }

    @Test
    void earthworkUsesContinuousSpanAcrossSubBlockSampling() {
        List<BuildHeightSample> oneMeterSamples = uniformSamples(100.0, 66, 101);
        List<BuildHeightSample> halfMeterSamples = uniformSamples(100.0, 66, 201);

        double oneMeterFill = RoadProfileQualityAnalyzer.analyze(
            chartWithSamples(oneMeterSamples, ground(64.0, 100.0))).terrain().fillBlockColumns();
        double halfMeterFill = RoadProfileQualityAnalyzer.analyze(
            chartWithSamples(halfMeterSamples, ground(64.0, 100.0))).terrain().fillBlockColumns();

        assertEquals(oneMeterFill, halfMeterFill, 1e-6,
            "earthwork volume should not double when sample spacing drops below 1 block");
        assertEquals(100.0, oneMeterFill, 1e-6);
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
        return List.of(elevation, elevation);
    }

    private static List<BuildHeightSample> uniformSamples(double totalLength, int buildY, int count) {
        List<BuildHeightSample> samples = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double station = totalLength * i / (count - 1);
            samples.add(new BuildHeightSample(station, buildY, buildY));
        }
        return samples;
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

    private static RoadProfileChartData chartWithDesignProfile(
            List<Double> stations,
            List<Double> design,
            List<BuildHeightSample> samples,
            List<Double> groundElevations) {
        double total = stations.getLast();
        List<Double> buildSummary = stations.stream()
            .map(station -> buildElevationFromSamples(samples, station))
            .toList();
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

    private static double buildElevationFromSamples(List<BuildHeightSample> samples, double station) {
        BuildHeightSample first = samples.getFirst();
        if (station <= first.station()) {
            return first.buildY();
        }
        BuildHeightSample last = samples.getLast();
        if (station >= last.station()) {
            return last.buildY();
        }
        for (int i = 1; i < samples.size(); i++) {
            BuildHeightSample previous = samples.get(i - 1);
            BuildHeightSample current = samples.get(i);
            if (station <= current.station()) {
                return previous.buildY();
            }
        }
        return last.buildY();
    }
}
