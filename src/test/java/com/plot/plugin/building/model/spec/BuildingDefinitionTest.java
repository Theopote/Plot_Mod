package com.plot.plugin.building.model.spec;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingDefinitionTest {

    @Test
    void fromFootprintMapsAllSpecSections() {
        BuildingFootprint footprint = sampleFootprint();
        BuildingDefinition definition = BuildingDefinition.fromFootprint(footprint);

        assertEquals(footprint.getId(), definition.id());
        assertEquals(footprint.getName(), definition.footprint().name());
        assertEquals(4, definition.footprint().outerPoints().size());
        assertTrue(definition.footprint().rectangular());

        assertEquals(3, definition.massing().floors());
        assertEquals(4, definition.massing().floorHeight());
        assertEquals(12, definition.massing().totalHeight());

        assertEquals(2, definition.envelope().wallThickness());
        assertEquals("minecraft:stone_bricks", definition.envelope().wallMaterial().getPrimaryMaterial());
        assertEquals("minecraft:oak_planks", definition.envelope().floorMaterial().getPrimaryMaterial());

        assertEquals(5, definition.facade().defaultWindowPattern().spacing());
        assertEquals(2, definition.facade().defaultWindowPattern().width());
        assertEquals(1, definition.facade().doors().size());
        assertEquals(1, definition.facade().doors().getFirst().wallSegmentIndex());

        assertEquals(BuildingFootprint.RoofType.GABLE, definition.roof().type());
        assertEquals(2, definition.roof().pitchRatio());
        assertEquals("minecraft:dark_oak_planks", definition.roof().material());

        assertEquals("minecraft:cobblestone", definition.foundation().fillMaterial());
        assertEquals(72, definition.foundation().manualBaseElevation());
    }

    @Test
    void mapperRoundTripToFootprintPreservesEditableFields() {
        BuildingFootprint original = sampleFootprint();
        BuildingDefinition definition = BuildingDefinitionMapper.fromFootprint(original);

        BuildingFootprint target = new BuildingFootprint(
            original.getId(),
            original.getOuterPoints(),
            original.isRectangular()
        );
        BuildingDefinitionMapper.applyMassingEnvelopeFacadeRoofFoundation(definition, target);

        assertEquals(original.getFloors(), target.getFloors());
        assertEquals(original.getFloorHeight(), target.getFloorHeight());
        assertEquals(original.getWallThickness(), target.getWallThickness());
        assertEquals(original.getWallMaterial().getPrimaryMaterial(), target.getWallMaterial().getPrimaryMaterial());
        assertEquals(original.getWindowSpacing(), target.getWindowSpacing());
        assertEquals(original.getRoofType(), target.getRoofType());
        assertEquals(original.getRoofPitchRatio(), target.getRoofPitchRatio());
        assertEquals(original.getFoundationFillMaterial(), target.getFoundationFillMaterial());
        assertEquals(original.getManualBaseElevation(), target.getManualBaseElevation());
        assertEquals(1, target.getDoors().size());
    }

    @Test
    void footprintToDefinitionConvenienceMatchesMapper() {
        BuildingFootprint footprint = sampleFootprint();
        assertNotNull(footprint.toDefinition());
        assertEquals(
            BuildingDefinitionMapper.fromFootprint(footprint).massing().floors(),
            footprint.toDefinition().massing().floors()
        );
    }

    @Test
    void windowPatternDisabledWhenSpacingZero() {
        WindowPatternSpec pattern = new WindowPatternSpec(0, 1, 2, 1);
        assertTrue(!pattern.enabled());
    }

    private static BuildingFootprint sampleFootprint() {
        BuildingFootprint footprint = new BuildingFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 8),
            new Vec2d(0, 8)
        ), true);
        footprint.setName("Spec Tower");
        footprint.setFloors(3);
        footprint.setFloorHeight(4);
        footprint.setWallThickness(2);
        footprint.setWallMaterial("minecraft:stone_bricks");
        footprint.setFloorMaterial("minecraft:oak_planks");
        footprint.setRoofMaterial("minecraft:dark_oak_planks");
        footprint.setFoundationFillMaterial("minecraft:cobblestone");
        footprint.setRoofType(BuildingFootprint.RoofType.GABLE);
        footprint.setRoofPitchRatio(2);
        footprint.setManualBaseElevation(72);
        footprint.setWindowSpacing(5);
        footprint.setWindowWidth(2);
        footprint.setWindowHeight(3);
        footprint.setWindowSillHeight(1);
        footprint.addDoor(new BuildingFootprint.DoorOpening(1, 0.5, 0, 2, 3));
        return footprint;
    }
}
