package com.plot.plugin.road.pipeline.crosssection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCrossSectionBuilderTest {

    @Test
    void shallowBridgeGapStillGetsDeckUnderlay() {
        assertTrue(RoadCrossSectionBuilder.shouldPlaceDeckUnderlay(70, 67),
            "3-block gap under a classified bridge must still receive a deck slab");
        assertTrue(RoadCrossSectionBuilder.shouldPlaceDeckUnderlay(70, 68));
        assertFalse(RoadCrossSectionBuilder.shouldPlaceDeckUnderlay(70, 69),
            "no room for an underlay when ground is already at deck Y");
        assertFalse(RoadCrossSectionBuilder.shouldPlaceDeckUnderlay(70, 70));
    }
}
