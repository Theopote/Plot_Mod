package com.plot.plugin.road.ui.editor;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.CrossSectionDraft;
import com.plot.plugin.road.model.section.CrossSectionGeometryRules;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossSectionSegmentPreviewTest {

    @Test
    void buildSurfaceSegmentUsesLongRoadAndLampStubs() {
        RoadSystemConfig config = new RoadSystemConfig("seg-preview");
        config.setLaneCount(2);
        config.setDefaultLaneWidth(3);
        config.setLaneDividers(false);
        config.setCenterLineStyle(CenterLineStyle.NONE);
        config.setIncludeSidewalk(true);
        config.setSidewalkWidth(2);
        config.setStreetlightSpacing(8);
        CrossSectionDraft draft = CrossSectionDraft.fromConfig(config);
        ResolvedCrossSection section = ResolvedCrossSection.resolve(draft.toCrossSection(), config);

        var scene = CrossSectionSegmentPreview.buildSurfaceSegment(
            section, CrossSectionSegmentPreview.SEGMENT_LENGTH_Z);
        assertFalse(scene.isEmpty());
        assertTrue(CrossSectionSegmentPreview.SEGMENT_LENGTH_Z > 20);
        assertEquals(
            CrossSectionSegmentPreview.SEGMENT_LENGTH_Z,
            scene.stream().map(v -> v.position().getZ()).distinct().count());
        long stubs = scene.stream()
            .filter(v -> "minecraft:iron_bars".equals(v.block()))
            .count();
        assertTrue(stubs >= 8, "both sides should show thin lamp stubs along the segment");
    }

    @Test
    void laneDividersEmbedInRoadSurfaceNotOnTop() {
        RoadSystemConfig config = previewConfig("seg-markings");
        config.setLaneCount(2);
        config.setDefaultLaneWidth(5);
        config.setLaneDividers(true);
        config.setCenterLineStyle(CenterLineStyle.NONE);
        CrossSectionDraft draft = CrossSectionDraft.fromConfig(config);
        ResolvedCrossSection section = ResolvedCrossSection.resolve(draft.toCrossSection(), config);

        var scene = CrossSectionSegmentPreview.buildSurfaceSegment(
            section, CrossSectionSegmentPreview.SEGMENT_LENGTH_Z);
        // 无中央标线时分隔线用默认白块，与灰路面区分。
        long markingAtRoadY = scene.stream()
            .filter(v -> ResolvedCrossSection.DEFAULT_MARKING_MATERIAL.equals(v.block()))
            .filter(v -> v.position().getY() == -1)
            .count();
        long markingAboveRoad = scene.stream()
            .filter(v -> ResolvedCrossSection.DEFAULT_MARKING_MATERIAL.equals(v.block()))
            .filter(v -> v.position().getY() == 0)
            .count();
        assertTrue(markingAtRoadY > 0, "lane dividers should occupy the road surface layer");
        assertEquals(0, markingAboveRoad, "lane dividers must not sit on top of the roadway");
    }

    @Test
    void laneDividersRenderDashedNotContinuousSolid() {
        RoadSystemConfig config = previewConfig("seg-dashed");
        config.setLaneCount(2);
        config.setDefaultLaneWidth(5);
        config.setLaneDividers(true);
        config.setCenterLineStyle(CenterLineStyle.NONE);
        CrossSectionDraft draft = CrossSectionDraft.fromConfig(config);
        ResolvedCrossSection section = ResolvedCrossSection.resolve(draft.toCrossSection(), config);

        var scene = CrossSectionSegmentPreview.buildSurfaceSegment(
            section, CrossSectionSegmentPreview.SEGMENT_LENGTH_Z);
        long dividerBlocks = scene.stream()
            .filter(v -> ResolvedCrossSection.DEFAULT_MARKING_MATERIAL.equals(v.block()))
            .filter(v -> v.position().getY() == -1)
            .count();
        assertTrue(dividerBlocks > 0);
        assertTrue(
            dividerBlocks < CrossSectionSegmentPreview.SEGMENT_LENGTH_Z,
            "lane dividers alone must look dashed, not a solid center line");
    }

    @Test
    void centerLineAndLaneDividersUseDistinctPreviewBlocks() {
        RoadSystemConfig config = previewConfig("seg-distinct");
        config.setLaneCount(4);
        config.setDefaultLaneWidth(3);
        config.setLaneDividers(true);
        config.setCenterLineStyle(CenterLineStyle.DOUBLE_SOLID);
        config.setMarkingMaterial("minecraft:yellow_concrete");
        CrossSectionDraft draft = CrossSectionDraft.fromConfig(config);
        ResolvedCrossSection section = ResolvedCrossSection.resolve(draft.toCrossSection(), config);

        var scene = CrossSectionSegmentPreview.buildSurfaceSegment(
            section, CrossSectionSegmentPreview.SEGMENT_LENGTH_Z);
        long yellow = scene.stream()
            .filter(v -> "minecraft:yellow_concrete".equals(v.block()))
            .count();
        long white = scene.stream()
            .filter(v -> ResolvedCrossSection.DEFAULT_MARKING_MATERIAL.equals(v.block()))
            .filter(v -> v.position().getY() == -1)
            .count();
        assertTrue(yellow > 0, "double solid center line should use yellow marking material");
        assertTrue(white > 0, "side lane dividers should stay white, not yellow center paint");
        // 双实线中央两列连续铺满纵深；侧向分隔为虚线，总量应少于两列实线。
        assertTrue(yellow >= CrossSectionSegmentPreview.SEGMENT_LENGTH_Z * 2);
        assertTrue(white < CrossSectionSegmentPreview.SEGMENT_LENGTH_Z * 2);
    }

    @Test
    void oddLaneCenterLineUsesYellowNotWhiteDividerPaint() {
        RoadSystemConfig config = previewConfig("seg-3lane");
        config.setLaneCount(3);
        config.setDefaultLaneWidth(5);
        config.setLaneDividers(true);
        config.setCenterLineStyle(CenterLineStyle.SINGLE_DASHED);
        config.setMarkingMaterial("minecraft:yellow_concrete");
        CrossSectionDraft draft = CrossSectionDraft.fromConfig(config);
        ResolvedCrossSection section = ResolvedCrossSection.resolve(draft.toCrossSection(), config);

        var scene = CrossSectionSegmentPreview.buildSurfaceSegment(
            section, CrossSectionSegmentPreview.SEGMENT_LENGTH_Z);
        long yellow = scene.stream()
            .filter(v -> "minecraft:yellow_concrete".equals(v.block()))
            .filter(v -> v.position().getY() == -1)
            .count();
        assertTrue(yellow > 0, "3-lane single yellow must still paint center seam yellow");

        var pavement = scene.stream()
            .filter(v -> v.position().getY() == -1)
            .collect(java.util.stream.Collectors.toMap(
                v -> v.position(),
                v -> v.block(),
                (previous, next) -> next));
        assertTrue(
            RoadLampPreview.pavementLayerHasMixedMaterials(pavement),
            "embedded markings must mark pavement as mixed so preview skips Z-strip merge");
    }

    @Test
    void threeLaneDoubleYellowPaintsCenterNotSideDivider() {
        RoadSystemConfig config = previewConfig("seg-3lane-double");
        config.setLaneCount(3);
        config.setDefaultLaneWidth(6);
        config.setLaneDividers(true);
        config.setCenterLineStyle(CenterLineStyle.DOUBLE_SOLID);
        config.setCenterLineSolid(true);
        config.setMarkingMaterial("minecraft:yellow_concrete");
        CrossSectionDraft draft = CrossSectionDraft.fromConfig(config);
        ResolvedCrossSection section = ResolvedCrossSection.resolve(draft.toCrossSection(), config);
        int[] center = CrossSectionGeometryRules.centerMarkingLateralRange(
            section.laneWidths, section.centerLineStyle, section.laneDividers);
        assertEquals(3, center[0]);
        assertEquals(4, center[1]);

        var scene = CrossSectionSegmentPreview.buildSurfaceSegment(
            section, CrossSectionSegmentPreview.SEGMENT_LENGTH_Z);
        long yellowOnCenter = scene.stream()
            .filter(v -> "minecraft:yellow_concrete".equals(v.block()))
            .filter(v -> v.position().getY() == -1)
            .filter(v -> v.position().getX() >= center[0] && v.position().getX() <= center[1])
            .count();
        long whiteOnCenter = scene.stream()
            .filter(v -> ResolvedCrossSection.DEFAULT_MARKING_MATERIAL.equals(v.block()))
            .filter(v -> v.position().getY() == -1)
            .filter(v -> v.position().getX() >= center[0] && v.position().getX() <= center[1])
            .count();
        assertTrue(yellowOnCenter > 0, "double yellow must occupy the center seam columns");
        assertEquals(0, whiteOnCenter, "center seam must not keep white lane-divider paint");
    }

    /** 路面用灰混凝土，避免与白标线同材质导致断言误判。 */
    private static RoadSystemConfig previewConfig(String id) {
        RoadSystemConfig config = new RoadSystemConfig(id);
        config.setSelectedMaterial("minecraft:gray_concrete");
        config.setIncludeSidewalk(false);
        config.setIncludeShoulder(false);
        return config;
    }

    @Test
    void draftRoundTripsStripMaterialsOntoSection() {
        CrossSectionDraft draft = CrossSectionDraft.fromConfig(new RoadSystemConfig("mats"));
        draft.setIncludeShoulder(true);
        draft.setShoulderMaterial("minecraft:cobblestone");
        draft.setIncludeBikeLane(true);
        draft.setBikeLaneMaterial("minecraft:cyan_concrete");
        draft.setIncludeMedian(true);
        draft.setMedianMaterial("minecraft:moss_block");

        var section = draft.toCrossSection();
        assertEquals("minecraft:cobblestone", section.getShoulder().getMaterial());
        assertEquals("minecraft:cyan_concrete", section.getBikeLane().getMaterial());
        assertEquals("minecraft:moss_block", section.getMedian().getMaterial());
    }
}
