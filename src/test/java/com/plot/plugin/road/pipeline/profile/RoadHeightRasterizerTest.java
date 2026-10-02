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
