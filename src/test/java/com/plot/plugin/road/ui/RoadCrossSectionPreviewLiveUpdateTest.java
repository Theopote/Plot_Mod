package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadCrossSectionPreviewRenderer;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.style.RoadStyleCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 有预设 styleId 时，预览布局仍须跟随道路当前断面，而不是锁死在 style 快照。
 */
class RoadCrossSectionPreviewLiveUpdateTest {

    @Test
    void previewLayoutTracksRoadEditsEvenWhenStylePresetAssigned() {
        RoadSystemConfig config = new RoadSystemConfig("xs-live-preview");
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("main");
        var boulevard = RoadStyleCatalog.boulevard();
        assertNotNull(boulevard);
        boulevard.applyTo(road);

        RoadCrossSectionPreviewRenderer.CrossSectionLayout before =
            RoadCrossSectionEditor.previewLayoutForRoad(road, config);
        float widthBefore = before.totalWidthBlocks();

        int resolvedWidth = road.getCrossSection().resolve(config).carriagewayWidth;
        road.setWidth(Math.max(resolvedWidth + 4, 12));
        road.setIncludeSidewalk(true);
        road.setSidewalkWidth(3);

        RoadCrossSectionPreviewRenderer.CrossSectionLayout after =
            RoadCrossSectionEditor.previewLayoutForRoad(road, config);
        assertNotEquals(widthBefore, after.totalWidthBlocks());
        assertEquals(
            road.getCrossSection().resolve(config).carriagewayWidth,
            after.roadBlocks,
            1e-3f);
    }
}
