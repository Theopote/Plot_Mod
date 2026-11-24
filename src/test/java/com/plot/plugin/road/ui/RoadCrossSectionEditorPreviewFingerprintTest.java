package com.plot.plugin.road.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.section.CrossSectionDraft;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.model.section.StationCrossSection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RoadCrossSectionEditorPreviewFingerprintTest {

    @Test
    void roadFingerprintChangesWhenBridgeGuardrailHeightChanges() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        Road road = new Road("r1");
        long before = RoadCrossSectionEditor.previewFingerprint(road, config);
        road.setBridgeGuardrailHeightBlocks(road.getEffectiveBridgeGuardrailHeightBlocks(config) + 1);
        assertNotEquals(before, RoadCrossSectionEditor.previewFingerprint(road, config));
    }

    @Test
    void roadFingerprintChangesWhenSlopeMaterialChanges() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        Road road = new Road("r1");
        long before = RoadCrossSectionEditor.previewFingerprint(road, config);
        road.setFillSlopeMaterial("minecraft:dirt");
        assertNotEquals(before, RoadCrossSectionEditor.previewFingerprint(road, config));
    }

    @Test
    void roadFingerprintChangesWhenStreetlightSpacingChanges() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        Road road = new Road("r1");
        long before = RoadCrossSectionEditor.previewFingerprint(road, config);
        road.setStreetlightSpacing(24);
        assertNotEquals(before, RoadCrossSectionEditor.previewFingerprint(road, config));
    }

    @Test
    void roadFingerprintChangesWhenBridgeMaterialsChange() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        Road road = new Road("r1");
        long before = RoadCrossSectionEditor.previewFingerprint(road, config);
        road.setBridgePierMaterial("minecraft:stone_bricks");
        assertNotEquals(before, RoadCrossSectionEditor.previewFingerprint(road, config));

        before = RoadCrossSectionEditor.previewFingerprint(road, config);
        road.setBridgeGuardrailMaterial("minecraft:iron_bars");
        assertNotEquals(before, RoadCrossSectionEditor.previewFingerprint(road, config));
    }

    @Test
    void draftFingerprintAlreadyCoversSlopeMaterials() {
        CrossSectionDraft draft = CrossSectionDraft.fromConfig(new RoadSystemConfig("road_system"));
        long before = RoadCrossSectionEditor.previewFingerprint(draft);
        draft.setCutSlopeMaterial("minecraft:cobblestone");
        assertNotEquals(before, RoadCrossSectionEditor.previewFingerprint(draft));

        before = RoadCrossSectionEditor.previewFingerprint(draft);
        draft.setRetainingWallMaterial("minecraft:polished_andesite");
        assertNotEquals(before, RoadCrossSectionEditor.previewFingerprint(draft));
    }

    @Test
    void typicalPreviewFollowsEditedDefaultSectionEvenWithVariableStations() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("main");
        var a = network.createNode(new Vec2d(0, 0));
        var b = network.createNode(new Vec2d(100, 0));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        road.setWidth(10);
        RoadCrossSection stationSection = road.getCrossSection().copy();
        stationSection.getCarriageway().setWidth(20);
        road.setVariableCrossSections(new RoadVariableCrossSections(List.of(
            StationCrossSection.at(0, stationSection))));

        ResolvedCrossSection mid = RoadCrossSectionEditor.resolvePreviewCrossSection(network, road, config);
        assertEquals(20, mid.carriagewayWidth, "overview mid-station still uses variable template");

        ResolvedCrossSection typicalBefore =
            RoadCrossSectionEditor.resolveTypicalPreviewCrossSection(road, config);
        assertEquals(10, typicalBefore.carriagewayWidth);

        road.setWidth(14);
        ResolvedCrossSection typicalAfter =
            RoadCrossSectionEditor.resolveTypicalPreviewCrossSection(road, config);
        assertEquals(14, typicalAfter.carriagewayWidth);
        assertNotEquals(
            RoadCrossSectionEditor.previewFingerprint(road, config),
            0L);
    }
}
