package com.plot.plugin.road;

import com.plot.plugin.config.RoadSystemConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadCrossSectionPreviewRendererTest {

    @Test
    void layoutIncludesShoulderSidewalkAndDrainage() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setRoadWidth(7);
        config.setIncludeSidewalk(true);
        config.setSidewalkWidth(2);
        config.setIncludeShoulder(true);
        config.setShoulderWidth(1);
        config.setIncludeDrainage(true);

        RoadCrossSectionPreviewRenderer.CrossSectionLayout layout =
            RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromConfig(config);

        assertEquals(7f, layout.roadBlocks);
        assertEquals(1f, layout.leftShoulderBlocks);
        assertEquals(2f, layout.leftSidewalkBlocks);
        assertEquals(0.5f, layout.drainageBlocks);
        assertEquals(14f, layout.totalWidthBlocks());
    }
}
