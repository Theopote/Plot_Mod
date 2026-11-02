package com.plot.plugin.road.pipeline.crosssection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCrossSectionBuilderTest {

    @Test
    void classifiedBridgeGetsDeckUnderlayThroughAtGradeAbutment() {
        assertTrue(RoadCrossSectionBuilder.shouldPlaceDeckUnderlay(70, 67),
            "deep gap under a classified bridge must receive a deck slab");
        assertTrue(RoadCrossSectionBuilder.shouldPlaceDeckUnderlay(70, 68));
        assertTrue(RoadCrossSectionBuilder.shouldPlaceDeckUnderlay(70, 69),
            "1-block seat still needs generated slab — BRIDGE integrity rejects terrain");
        assertTrue(RoadCrossSectionBuilder.shouldPlaceDeckUnderlay(70, 70),
            "at-grade abutment still needs generated slab under pavement");
        assertFalse(RoadCrossSectionBuilder.shouldPlaceDeckUnderlay(70, 71),
            "cut above deck is not an underlay seat");
    }
}
