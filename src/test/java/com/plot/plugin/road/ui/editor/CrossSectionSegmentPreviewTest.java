package com.plot.plugin.road.ui.editor;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.CrossSectionDraft;
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
