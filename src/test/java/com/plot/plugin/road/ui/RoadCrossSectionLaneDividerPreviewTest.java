package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.section.CenterLineStyle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCrossSectionLaneDividerPreviewTest {

    @Test
    void turningOffLaneDividersClearsMarkingColumnsAndShrinksWidth() {
        RoadSystemConfig config = new RoadSystemConfig("divider-preview");
        config.setLaneDividers(true);
        config.setCenterLineStyle(CenterLineStyle.NONE);
        config.setDefaultLaneWidth(3);
        config.setLaneCount(2);
        config.setRoadWidth(7);

        Road road = new Road("r1");
        road.setLaneCount(2);
        road.setLaneDividers(true);
        road.setCenterLineStyle(CenterLineStyle.NONE);
        road.getCrossSection().getCarriageway().setDefaultLaneWidth(3);
        road.getCrossSection().getCarriageway().syncDerivedWidth(CenterLineStyle.NONE, true, 0);

        var before = RoadCrossSectionEditor.previewLayoutForRoad(road, config);
        assertEquals(7f, before.roadBlocks, 1e-3f);
        assertFalse(before.markingLineRatios.isEmpty(), "dividers on should paint marking columns");

        long fpBefore = RoadCrossSectionEditor.previewFingerprint(road, config);
        CrossSectionDraftMutator.forRoad(road, config, null).setLaneDividers(false);

        var after = RoadCrossSectionEditor.previewLayoutForRoad(road, config);
        long fpAfter = RoadCrossSectionEditor.previewFingerprint(road, config);

        assertEquals(Boolean.FALSE, road.getLaneDividers());
        assertEquals(6, road.getWidth());
        assertEquals(6f, after.roadBlocks, 1e-3f);
        assertTrue(after.markingLineRatios.isEmpty(), "dividers off + no center line → no marking columns");
        assertNotEquals(fpBefore, fpAfter, "fingerprint must change so preview cache invalidates");
    }

    @Test
    void nullLaneDividersFingerprintMustTrackEffectiveResolvedState() {
        RoadSystemConfig config = new RoadSystemConfig("divider-null-fp");
        config.setLaneDividers(true);
        config.setCenterLineStyle(CenterLineStyle.NONE);
        config.setDefaultLaneWidth(3);
        config.setLaneCount(2);
        config.setRoadWidth(7);

        Road withNull = new Road("null");
        withNull.setLaneCount(2);
        withNull.setLaneDividers(null);
        withNull.setCenterLineStyle(CenterLineStyle.NONE);
        withNull.getCrossSection().getCarriageway().setDefaultLaneWidth(3);
        withNull.getCrossSection().getCarriageway().setWidth(7);

        Road withFalse = new Road("off");
        withFalse.setLaneCount(2);
        withFalse.setLaneDividers(false);
        withFalse.setCenterLineStyle(CenterLineStyle.NONE);
        withFalse.getCrossSection().getCarriageway().setDefaultLaneWidth(3);
        withFalse.getCrossSection().getCarriageway().setWidth(7);

        var nullLayout = RoadCrossSectionEditor.previewLayoutForRoad(withNull, config);
        var falseLayout = RoadCrossSectionEditor.previewLayoutForRoad(withFalse, config);

        assertTrue(withNull.getCrossSection().resolve(config).laneDividers);
        assertFalse(withFalse.getCrossSection().resolve(config).laneDividers);
        assertFalse(nullLayout.markingLineRatios.isEmpty(), "null inherits config dividers → markings");
        assertTrue(falseLayout.markingLineRatios.isEmpty(), "explicit false → no markings");

        // 若指纹把 null 当成 false，关闭分割线时可能不刷新缓存，留下旧标线列。
        assertNotEquals(
            RoadCrossSectionEditor.previewFingerprint(withNull, config),
            RoadCrossSectionEditor.previewFingerprint(withFalse, config),
            "null(inherit on) and explicit false must fingerprint differently");
    }

    @Test
    void threeLanesTurningOffDividersRemovesSideSeamMarkings() {
        RoadSystemConfig config = new RoadSystemConfig("divider-3lane");
        config.setLaneDividers(true);
        config.setCenterLineStyle(CenterLineStyle.NONE);
        config.setDefaultLaneWidth(3);

        Road road = new Road("r3");
        road.setLaneCount(3);
        road.setLaneDividers(true);
        road.setCenterLineStyle(CenterLineStyle.NONE);
        road.getCrossSection().getCarriageway().setDefaultLaneWidth(3);
        road.getCrossSection().getCarriageway().syncDerivedWidth(CenterLineStyle.NONE, true, 0);

        var before = RoadCrossSectionEditor.previewLayoutForRoad(road, config);
        assertEquals(2, before.markingLineRatios.size());
        assertEquals(11f, before.roadBlocks, 1e-3f); // 3*3 + 2 seams

        CrossSectionDraftMutator.forRoad(road, config, null).setLaneDividers(false);
        var after = RoadCrossSectionEditor.previewLayoutForRoad(road, config);
        assertTrue(after.markingLineRatios.isEmpty());
        assertEquals(9f, after.roadBlocks, 1e-3f);
    }

    @Test
    void staleWidthWithDividersOffDoesNotAbsorbSeamIntoLaneTravel() {
        RoadSystemConfig config = new RoadSystemConfig("divider-stale-width");
        config.setLaneDividers(false);
        config.setCenterLineStyle(CenterLineStyle.NONE);
        config.setDefaultLaneWidth(3);

        // 模拟：关掉分割线后总宽仍停在含分隔格的 7，且未及时 syncDerivedWidth。
        Road road = new Road("stale");
        road.setLaneCount(2);
        road.getCrossSection().getMarkings().setLaneDividers(false);
        road.getCrossSection().getMarkings().setCenterLineStyle(CenterLineStyle.NONE);
        road.getCrossSection().getCarriageway().setDefaultLaneWidth(3);
        road.getCrossSection().getCarriageway().setWidth(7);

        var layout = RoadCrossSectionEditor.previewLayoutForRoad(road, config);
        assertTrue(layout.markingLineRatios.isEmpty());
        assertEquals(6f, layout.roadBlocks, 1e-3f);
        assertEquals(
            List.of(3, 3),
            road.getCrossSection().getCarriageway().resolveLaneWidths(),
            "不得把过期分隔格 pack 成 3+4 行驶宽");
    }

    @Test
    void setLaneDividersSyncsDerivedWidthOnRoad() {
        Road road = new Road("sync");
        road.setLaneCount(2);
        road.setCenterLineStyle(CenterLineStyle.NONE);
        road.getCrossSection().getCarriageway().setDefaultLaneWidth(3);
        road.getCrossSection().getCarriageway().syncDerivedWidth(CenterLineStyle.NONE, true, 0);
        road.getCrossSection().getMarkings().setLaneDividers(true);
        assertEquals(7, road.getWidth());

        road.setLaneDividers(false);
        assertEquals(6, road.getWidth());
        assertEquals(List.of(3, 3), road.getCrossSection().getCarriageway().resolveLaneWidths());
    }
}
