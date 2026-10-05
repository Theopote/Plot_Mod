package com.plot.plugin.road.geometry;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadCorridorWidthTest {

    @Test
    void pavementHalfWidthIncludesOuterBands() {
        RoadSystemConfig config = new RoadSystemConfig("road_test");
        config.setRoadWidth(7);
        config.setIncludeShoulder(true);
        config.setShoulderWidth(1);
        config.setIncludeSidewalk(true);
        config.setSidewalkWidth(2);
        config.setIncludeSlopeBatter(false);

        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(config);

        assertEquals(6.5, RoadCorridorWidth.pavementHalfWidthBlocks(section), 0.01);
        assertEquals(13, RoadCorridorWidth.gradingEnvelopeWidthBlocks(section));
    }

    @Test
    void canvasPreviewHalfWidthIncludesTraversableBandsButNotShoulderOrSlope() {
        RoadSystemConfig config = new RoadSystemConfig("road_test");
        config.setRoadWidth(7);
        config.setIncludeShoulder(true);
        config.setShoulderWidth(1);
        config.setIncludeBikeLane(true);
        config.setBikeLaneWidth(2);
        config.setIncludeSidewalk(true);
        config.setSidewalkWidth(2);
        config.setIncludeSlopeBatter(true);
        config.setTerrainStyle(com.plot.plugin.road.terrain.RoadTerrainStyle.BALANCED);
        config.setCutSlopeRatio(1.0f);

        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(config);

        assertEquals(7.5, RoadCorridorWidth.canvasPreviewHalfWidthBlocks(section), 0.01);
        assertEquals(8.5, RoadCorridorWidth.pavementHalfWidthBlocks(section), 0.01);
    }

    @Test
    void bridgeDeckWidthIncludesOuterBands() {
        RoadSystemConfig config = new RoadSystemConfig("road_test");
        config.setRoadWidth(5);
        config.setIncludeShoulder(true);
        config.setShoulderWidth(1);
        config.setIncludeBikeLane(true);
        config.setBikeLaneWidth(2);
        config.setIncludeSidewalk(true);
        config.setSidewalkWidth(2);

        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(config);

        assertEquals(15, RoadCorridorWidth.bridgeDeckWidthBlocks(section));
        assertEquals(7.5, RoadCorridorWidth.bridgeDeckHalfWidthBlocks(section), 0.01);
        assertEquals(15, RoadCorridorWidth.gradingEnvelopeWidthBlocks(section));
    }

    @Test
    void decorationClearWidthExtendsBeyondPavementWhenSlopeBatterEnabled() {
        RoadSystemConfig config = new RoadSystemConfig("road_test");
        config.setRoadWidth(5);
        config.setIncludeShoulder(true);
        config.setShoulderWidth(1);
        config.setIncludeSidewalk(true);
        config.setSidewalkWidth(1);
        config.setIncludeSlopeBatter(true);
        config.setTerrainStyle(com.plot.plugin.road.terrain.RoadTerrainStyle.BALANCED);
        config.setCutSlopeRatio(1.0f);

        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(config);

        assertEquals(9, RoadCorridorWidth.gradingEnvelopeWidthBlocks(section));
        assertEquals(21, RoadCorridorWidth.decorationClearWidthBlocks(section, config));
    }
}
