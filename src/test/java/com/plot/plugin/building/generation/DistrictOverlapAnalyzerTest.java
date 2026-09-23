package com.plot.plugin.building.generation;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistrictOverlapAnalyzerTest {

    @Test
    void findTooClosePairsDetectsGapWithinThreshold() {
        BuildingFootprint a = square("a", 0, 0, 10);
        BuildingFootprint b = square("b", 11, 0, 10);

        List<DistrictOverlapAnalyzer.ClosePair> pairs =
            DistrictOverlapAnalyzer.findTooClosePairs(List.of(a, b), 2.0);

        assertEquals(1, pairs.size());
        assertEquals("a", pairs.getFirst().buildingIdA());
        assertEquals("b", pairs.getFirst().buildingIdB());
        assertTrue(pairs.getFirst().gapBlocks() <= 2.0);
    }

    @Test
    void findTooClosePairsIgnoresOverlappingFootprints() {
        BuildingFootprint a = square("a", 0, 0, 10);
        BuildingFootprint b = square("b", 5, 0, 10);

        List<DistrictOverlapAnalyzer.ClosePair> pairs =
            DistrictOverlapAnalyzer.findTooClosePairs(List.of(a, b), 2.0);

        assertTrue(pairs.isEmpty());
    }

    @Test
    void findTooClosePairsIgnoresSeparatedFootprints() {
        BuildingFootprint a = square("a", 0, 0, 10);
        BuildingFootprint b = square("b", 14, 0, 10);

        List<DistrictOverlapAnalyzer.ClosePair> pairs =
            DistrictOverlapAnalyzer.findTooClosePairs(List.of(a, b), 2.0);

        assertTrue(pairs.isEmpty());
    }

    private static BuildingFootprint square(String id, double x, double z, double size) {
        BuildingFootprint footprint = new BuildingFootprint(id, List.of(
            new Vec2d(x, z),
            new Vec2d(x + size, z),
            new Vec2d(x + size, z + size),
            new Vec2d(x, z + size)
        ), true);
        footprint.setName(id);
        return footprint;
    }
}
