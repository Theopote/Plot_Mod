package com.plot.plugin.building.preset;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.BuildingProject;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingPresetApplierTest {

    @Test
    void warehousePresetDisablesWindows() {
        BuildingFootprint footprint = sampleFootprint();
        BuildingPresetApplier.apply("warehouse", footprint);

        assertEquals(0, footprint.getWindowSpacing());
        assertEquals(1, footprint.getFloors());
        assertEquals(7, footprint.getFloorHeight());
        assertEquals("warehouse", footprint.getPresetId());
    }

    @Test
    void apartmentPresetAddsBalconiesAndFloors() {
        BuildingFootprint footprint = sampleFootprint();
        BuildingPresetApplier.apply("apartment", footprint);

        assertEquals(6, footprint.getFloors());
        assertEquals(2, footprint.getBalconies().size());
        assertEquals("apartment", footprint.getPresetId());
    }

    @Test
    void officePresetEnablesParapet() {
        BuildingFootprint footprint = sampleFootprint();
        BuildingPresetApplier.apply("office", footprint);

        assertTrue(footprint.isParapetEnabled());
        assertEquals(8, footprint.getFloors());
    }

    @Test
    void presetPreservesFootprintGeometry() {
        BuildingFootprint footprint = sampleFootprint();
        List<Vec2d> before = footprint.getOuterPoints();
        String id = footprint.getId();

        BuildingPresetApplier.apply("villa", footprint);

        assertEquals(id, footprint.getId());
        assertEquals(before.size(), footprint.getOuterPoints().size());
        assertEquals(BuildingFootprint.RoofType.GABLE, footprint.getRoofType());
    }

    @Test
    void presetJsonRoundTrip() {
        BuildingFootprint footprint = sampleFootprint();
        BuildingPresetApplier.apply("school", footprint);

        BuildingProject project = new BuildingProject();
        project.addBuilding(footprint);
        BuildingProject loaded = BuildingProject.fromJson(project.toJson());

        BuildingFootprint roundTrip = loaded.getBuilding(footprint.getId());
        assertEquals("school", roundTrip.getPresetId());
        assertEquals(3, roundTrip.getFloors());
        assertEquals(1, roundTrip.getCanopies().size());
    }

    @Test
    void exporterProducesJsonSummary() {
        BuildingFootprint footprint = sampleFootprint();
        BuildingDefinition definition = BuildingPresetApplier.previewDefinition("tower", footprint);
        String json = BuildingPresetExporter.toJsonSummary(definition);

        assertTrue(json.contains("\"floors\": 12"));
        assertTrue(json.contains("\"parapet\": true"));
    }

    @Test
    void unknownPresetThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> BuildingPresetApplier.apply("unknown_type", sampleFootprint()));
    }

    @Test
    void catalogListsBuiltInPresets() {
        assertTrue(BuildingPresetCatalog.all().size() >= 9);
        assertTrue(BuildingPresetCatalog.find("commercial") != null);
    }

    @Test
    void builtinPresetCountStaysWithinFreezeCap() {
        int count = BuildingPresetCatalog.all().size();
        assertTrue(count <= BuildingPresetCatalog.MAX_BUILTIN_PRESETS,
            "preset count " + count + " exceeds freeze cap "
                + BuildingPresetCatalog.MAX_BUILTIN_PRESETS
                + "; do not add hospital/mall/hotel/etc until BuildingSpec is stable");
        assertEquals(9, count, "freeze set is exactly the current 9 architecture-validation presets");
    }

    private static BuildingFootprint sampleFootprint() {
        BuildingFootprint footprint = new BuildingFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(12, 0),
            new Vec2d(12, 10),
            new Vec2d(0, 10)
        ), true);
        footprint.setFloors(2);
        footprint.setFloorHeight(3);
        footprint.setWindowSpacing(4);
        return footprint;
    }
}
