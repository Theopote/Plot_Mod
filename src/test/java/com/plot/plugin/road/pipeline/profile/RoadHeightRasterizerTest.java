package com.plot.plugin.road.pipeline.profile;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadHeightRasterizerTest {

    @Test
    void fractionalSlopeBudgetDoesNotCeilToFullBlockStep() {
        List<Double> design = List.of(60.0, 60.5, 61.0, 61.5, 62.0);
        List<Double> distances = constantDistances(4, 10.0);
        List<Float> slopes = constantSlopes(4, 5.0f);

        RoadHeightRasterizer.RasterizationResult result = RoadHeightRasterizer.rasterize(
            design, distances, slopes, null, null);

        assertEquals(60, result.buildHeights().get(1),
            "5% over 10 m should not quantize to +1 block in the first segment");
        assertTrue(result.maxDesignBuildDeviation() <= 1.0);
    }

    @Test
    void infeasibleManualEndIsNotForcedOntoLastSegment() {
        List<Double> design = List.of(58.0, 61.0, 80.0);
        List<Double> distances = List.of(20.0, 20.0);
        List<Float> slopes = List.of(8.0f, 8.0f);

        RoadHeightRasterizer.RasterizationResult result = RoadHeightRasterizer.rasterize(
            design, distances, slopes, 58, 80);

        assertTrue(result.segmentBuildEnds().getLast() < 80);
        assertTrue(result.cumulativeGradeError() <= 20.0);
    }

    @Test
    void feasibleManualEndIsReachedWithinSlopeBudget() {
        List<Double> design = List.of(58.0, 59.0, 60.0);
        List<Double> distances = List.of(20.0, 20.0);
        List<Float> slopes = List.of(8.0f, 8.0f);

        RoadHeightRasterizer.RasterizationResult result = RoadHeightRasterizer.rasterize(
            design, distances, slopes, 58, 60);

        assertEquals(60, result.segmentBuildEnds().getLast());
        assertTrue(result.maxDesignBuildDeviation() <= 1.0);
    }

    @Test
    void cumulativeGradeErrorStaysWithinOneBlock() {
        List<Double> design = List.of(60.0, 61.0, 62.0, 63.0, 64.0);
        List<Double> distances = constantDistances(4, 10.0);
        List<Float> slopes = constantSlopes(4, 8.0f);

        RoadHeightRasterizer.RasterizationResult result = RoadHeightRasterizer.rasterize(
            design, distances, slopes, null, null);

        assertTrue(result.cumulativeGradeError() <= 1.0);
        assertTrue(result.maxDesignBuildDeviation() <= 1.0);
        assertFalse(result.buildHeights().isEmpty());
    }

    @Test
    void rasterizeFromIntegerDesignMatchesContinuousRasterize() {
        List<Integer> integerDesign = List.of(60, 61, 62, 63);
        List<Double> design = List.of(60.0, 61.0, 62.0, 63.0);
        List<Double> distances = constantDistances(3, 10.0);
        List<Float> slopes = constantSlopes(3, 8.0f);

        RoadHeightRasterizer.RasterizationResult fromInteger =
            RoadHeightRasterizer.rasterizeFromIntegerDesign(
                integerDesign, distances, slopes, null, null);
        RoadHeightRasterizer.RasterizationResult fromDouble =
            RoadHeightRasterizer.rasterize(design, distances, slopes, null, null);

        assertEquals(fromDouble.buildHeights(), fromInteger.buildHeights());
    }

    @Test
    void blockStepsAreIndependentOfPathSegmentation() {
        List<Double> stationsA = List.of(0.0, 10.0, 20.0, 30.0, 40.0);
        List<Double> designA = linearDesign(stationsA, 64.0, 0.05);
        List<Double> distancesA = List.of(10.0, 10.0, 10.0, 10.0);

        List<Double> stationsB = List.of(0.0, 5.0, 20.0, 28.0, 40.0);
        List<Double> designB = linearDesign(stationsB, 64.0, 0.05);
        List<Double> distancesB = List.of(5.0, 15.0, 8.0, 12.0);
        List<Float> slopes = constantSlopes(4, 8.0f);

        RoadHeightRasterizer.RasterizationResult resultA = RoadHeightRasterizer.rasterize(
            designA, distancesA, slopes, null, null);
        RoadHeightRasterizer.RasterizationResult resultB = RoadHeightRasterizer.rasterize(
            designB, distancesB, slopes, null, null);

        assertEquals(resultA.samples().size(), resultB.samples().size());
        for (int i = 0; i < resultA.samples().size(); i++) {
            BuildHeightSample sampleA = resultA.samples().get(i);
            BuildHeightSample sampleB = resultB.samples().get(i);
            assertEquals(sampleA.station(), sampleB.station(), 1e-9);
            assertEquals(sampleA.buildY(), sampleB.buildY(),
                () -> "block step at station " + sampleA.station() + " should not depend on segmentation");
            assertEquals(64.0 + 0.05 * sampleA.station(), sampleA.designElevation(), 1e-6);
        }
    }

    @Test
    void rasterizationResultIsInternallyConsistent() {
        List<Double> design = List.of(64.0, 65.0, 66.0, 66.0);
        List<Double> distances = List.of(10.0, 15.0, 15.0);
        List<Float> slopes = constantSlopes(3, 8.0f);

        RoadHeightRasterizer.RasterizationResult result = RoadHeightRasterizer.rasterize(
            design, distances, slopes, null, null);

        assertEquals(result.samples().getLast().buildY(), result.buildProfile().endElevation());
        for (int i = 0; i < result.buildHeights().size(); i++) {
            double station = i == 0 ? 0.0 : distances.stream().limit(i).mapToDouble(Double::doubleValue).sum();
            assertEquals(
                result.buildProfile().elevationAtWorldStation(station),
                result.buildHeights().get(i),
                () -> "chart build height at station " + station + " must match BuildHeightProfile");
        }
        for (int i = 0; i < result.segmentBuildEnds().size(); i++) {
            assertEquals(result.segmentBuildEnds().get(i), result.buildHeights().get(i + 1));
        }
    }

    @Test
    void buildProfilePreservesFractionalEndpointElevation() {
        List<Double> design = List.of(64.0, 65.0);
        List<Double> distances = List.of(18.7);
        List<Float> slopes = List.of(8.0f);

        RoadHeightRasterizer.RasterizationResult result = RoadHeightRasterizer.rasterize(
            design, distances, slopes, null, null);

        BuildHeightProfile profile = result.buildProfile();
        assertEquals(18.7, profile.endStation(), 1e-6);
        assertEquals(result.samples().getLast().buildY(), profile.endElevation());
        assertEquals(profile.endElevation(), profile.elevationAtWorldStation(18.7));
        assertEquals(profile.elevationAtWorldStation(18.0), profile.elevationAtWorldStation(18.69));
    }

    @Test
    void manualEndDoesNotRushAcrossEntireLastSegment() {
        List<Double> design = List.of(64.0, 70.0);
        List<Double> distances = List.of(100.0);
        List<Float> slopes = List.of(8.0f);

        RoadHeightRasterizer.RasterizationResult result = RoadHeightRasterizer.rasterize(
            design, distances, slopes, null, 70);

        assertTrue(result.longestFlatRun() < 25,
            () -> "endpoint should follow design slope, not rush then flatten; longest flat "
                + result.longestFlatRun());
        assertEquals(70, result.buildProfile().endElevation());
        assertTrue(result.samples().getLast().buildY() >= 68,
            "build should approach manual endpoint along the design profile");
    }

    @Test
    void rasterizeProducesBlockPlacementSamples() {
        List<Double> design = List.of(64.0, 65.6);
        List<Double> distances = List.of(18.0);
        List<Float> slopes = List.of(8.0f);

        RoadHeightRasterizer.RasterizationResult result = RoadHeightRasterizer.rasterize(
            design, distances, slopes, null, null);

        assertEquals(19, result.samples().size());
        assertTrue(result.buildProfile().isActive());
        assertEquals(64, result.samples().getFirst().buildY());
        assertEquals(result.samples().getLast().buildY(), result.segmentBuildEnds().getLast());
        for (int i = 1; i < result.samples().size(); i++) {
            assertTrue(Math.abs(result.samples().get(i).buildY() - result.samples().get(i - 1).buildY()) <= 1);
        }
    }

    @Test
    void fivePercentGradeRisesRoughlyEveryTwentyBlocks() {
        List<Double> design = List.of(64.0, 65.0);
        List<Double> distances = List.of(40.0);
        List<Float> slopes = List.of(5.0f);

        RoadHeightRasterizer.RasterizationResult result = RoadHeightRasterizer.rasterize(
            design, distances, slopes, null, null);

        List<Integer> riseStations = new ArrayList<>();
        int previous = result.samples().getFirst().buildY();
        for (BuildHeightSample sample : result.samples()) {
            if (sample.buildY() > previous) {
                riseStations.add((int) Math.round(sample.station()));
            }
            previous = sample.buildY();
        }
        assertEquals(1, riseStations.size());
        assertTrue(riseStations.getFirst() >= 18 && riseStations.getFirst() <= 22,
            () -> "5% grade should place a step near every 20 blocks, got station " + riseStations);
    }

    private static List<Double> linearDesign(List<Double> stations, double intercept, double slope) {
        List<Double> design = new ArrayList<>(stations.size());
        for (double station : stations) {
            design.add(intercept + slope * station);
        }
        return design;
    }

    private static List<Double> constantDistances(int count, double distance) {
        List<Double> distances = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            distances.add(distance);
        }
        return distances;
    }

    private static List<Float> constantSlopes(int count, float slope) {
        List<Float> slopes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            slopes.add(slope);
        }
        return slopes;
    }
}
