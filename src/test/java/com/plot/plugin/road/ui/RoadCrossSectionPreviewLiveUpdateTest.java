package com.plot.plugin.road.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadCrossSectionPreviewRenderer;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.style.RoadStyleCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void previewShowsEffectiveGradeEvenWhenRoadInheritsGlobalMaxSlope() {
        RoadSystemConfig config = new RoadSystemConfig("xs-grade");
        config.setMaxSlope(5.0f);
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("main");
        road.setMaxSlope(null);

        var layout = RoadCrossSectionEditor.previewLayoutForRoad(network, road, config);
        assertEquals(5.0f, layout.maxSlopePercent, 1e-3f);
        assertTrue(layout.minSlopePercent < 0f);
        assertNotNull(layout.gradeOverlayLabel());
    }

    @Test
    void previewShowsGradeRangeWhenSegmentOverridesDiffer() {
        RoadSystemConfig config = new RoadSystemConfig("xs-grade-range");
        config.setMaxSlope(5.0f);
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("main");
        road.setMaxSlope(5.0f);
        var a = network.createNode(new Vec2d(0, 0));
        var b = network.createNode(new Vec2d(40, 0));
        RoadEdge edge = network.createEdge(
            a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(40, 0)), road.getId());
        edge.setSlopeOverrides(List.of(new RoadEdge.SlopeOverride(0, 20, 8.0f)));

        var layout = RoadCrossSectionEditor.previewLayoutForRoad(network, road, config);
        assertEquals(8.0f, layout.maxSlopePercent, 1e-3f);
        assertEquals(5.0f, layout.minSlopePercent, 1e-3f);
        assertNotNull(layout.gradeOverlayLabel());
    }
}
