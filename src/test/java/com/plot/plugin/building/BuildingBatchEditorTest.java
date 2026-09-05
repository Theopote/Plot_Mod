package com.plot.plugin.building;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.building.model.BuildingFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class BuildingBatchEditorTest {

    private static BuildingFootprint building(String id) {
        BuildingFootprint footprint = new BuildingFootprint(id, List.of(
            new Vec2d(0, 0),
            new Vec2d(8, 0),
            new Vec2d(8, 6),
            new Vec2d(0, 6)
        ), true);
        footprint.setName(id);
        footprint.setFloors(2);
        footprint.setFloorHeight(3);
        footprint.setWallThickness(1);
        footprint.setRoofType(BuildingFootprint.RoofType.FLAT);
        footprint.setWindowSpacing(0);
        return footprint;
    }

    @Test
    void applyCopiesEnabledMassingFields() {
        BuildingFootprint source = building("source");
        source.setFloors(6);
        source.setFloorHeight(4);
        source.setWallThickness(2);
        source.setWallMaterial(MaterialMix.single("minecraft:bricks"));
        source.setFloorMaterial(MaterialMix.single("minecraft:oak_planks"));
        source.setRoofMaterial("minecraft:dark_oak_planks");
        source.setFoundationFillMaterial("minecraft:dirt");
        source.setRoofType(BuildingFootprint.RoofType.GABLE);
        source.setRoofPitchRatio(3);
        source.setWindowSpacing(4);
        source.setWindowWidth(2);
        source.setWindowHeight(2);
        source.setWindowSillHeight(1);

        BuildingFootprint a = building("a");
        BuildingFootprint b = building("b");
        a.setFloors(1);
        b.setFloors(1);

        BuildingBatchEditor.FieldMask mask = BuildingBatchEditor.FieldMask.allMassing();
        BuildingBatchEditor.ApplyResult result = BuildingBatchEditor.apply(source, List.of(a, b), mask);

        assertEquals(2, result.updated());
        assertEquals(0, result.skipped());
        for (BuildingFootprint target : List.of(a, b)) {
            assertEquals(6, target.getFloors());
            assertEquals(4, target.getFloorHeight());
            assertEquals(2, target.getWallThickness());
            assertEquals("minecraft:bricks", target.getWallMaterial().getPrimaryMaterial());
            assertEquals("minecraft:dark_oak_planks", target.getRoofMaterial());
            assertEquals(BuildingFootprint.RoofType.GABLE, target.getRoofType());
            assertEquals(3, target.getRoofPitchRatio());
            assertEquals(4, target.getWindowSpacing());
            assertNotSame(source.getWallMaterial(), target.getWallMaterial());
        }
    }

    @Test
    void fieldMaskSkipsDisabledGroups() {
        BuildingFootprint source = building("source");
        source.setFloors(8);
        source.setFloorHeight(5);
        source.setRoofType(BuildingFootprint.RoofType.HIP);

        BuildingFootprint target = building("target");
        target.setFloors(2);
        target.setFloorHeight(3);
        target.setRoofType(BuildingFootprint.RoofType.FLAT);

        BuildingBatchEditor.FieldMask mask = new BuildingBatchEditor.FieldMask();
        mask.floors = true;
        mask.floorHeight = false;
        mask.wallThickness = false;
        mask.materials = false;
        mask.roof = false;
        mask.windows = false;

        BuildingBatchEditor.apply(source, List.of(target), mask);

        assertEquals(8, target.getFloors());
        assertEquals(3, target.getFloorHeight());
        assertEquals(BuildingFootprint.RoofType.FLAT, target.getRoofType());
    }

    @Test
    void applyPresetUpdatesAllTargets() {
        BuildingFootprint a = building("a");
        BuildingFootprint b = building("b");
        a.setFloors(1);
        b.setFloors(1);

        BuildingBatchEditor.ApplyResult result =
            BuildingBatchEditor.applyPreset("warehouse", List.of(a, b));

        assertEquals(2, result.updated());
        assertEquals("warehouse", a.getPresetId());
        assertEquals("warehouse", b.getPresetId());
        assertEquals(a.getFloors(), b.getFloors());
        assertEquals(a.getFloorHeight(), b.getFloorHeight());
    }

    @Test
    void emptyMaskDoesNothing() {
        BuildingFootprint source = building("source");
        source.setFloors(9);
        BuildingFootprint target = building("target");
        target.setFloors(2);

        BuildingBatchEditor.FieldMask mask = new BuildingBatchEditor.FieldMask();
        mask.floors = false;
        mask.floorHeight = false;
        mask.wallThickness = false;
        mask.materials = false;
        mask.roof = false;
        mask.windows = false;

        BuildingBatchEditor.ApplyResult result =
            BuildingBatchEditor.apply(source, List.of(target), mask);
        assertEquals(0, result.updated());
        assertEquals(1, result.skipped());
        assertEquals(2, target.getFloors());
    }
}
