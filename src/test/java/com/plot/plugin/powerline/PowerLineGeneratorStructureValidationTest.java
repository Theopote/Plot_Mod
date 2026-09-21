package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.design.ConductorAttachmentPresets;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.parametric.TowerParametricEditor;
import com.plot.plugin.powerline.design.parametric.TowerParameterSet;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.placement.PlacementCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineGeneratorStructureValidationTest {

    private static final String INVALID_MANUAL_ID = "test/invalid_manual_tower";

    @Test
    void invalidManualTowerProducesInvalidPolePlacement() {
        PowerLineFootprint line = singlePoleLine();
        PowerLineGenerationResult result = generate(line, invalidManualTower());

        assertFalse(result.polePlacements.isEmpty());
        PolePlacement placement = result.polePlacements.getFirst();
        assertFalse(placement.isValid());
        assertTrue(placement.attachments().isEmpty());
        assertFalse(placement.usesAttachmentConductors());
    }

    @Test
    void invalidManualTowerDoesNotGenerateAdjacentSpan() {
        PowerLineFootprint line = twoPoleLine();
        PowerLineGenerationResult result = generate(line, invalidManualTower());

        assertTrue(result.polePlacements.size() >= 2);
        assertFalse(PowerLineGenerator.isSpanGenerable(result.polePlacements, 0, 1));
        assertTrue(result.conductorSpans.isEmpty());
    }

    @Test
    void invalidManualTowerPlacesNoStructureOrEquipment() {
        PowerLineFootprint line = singlePoleLine();
        PowerLineGenerationResult result = generate(line, invalidManualTower());

        assertTrue(result.placementRecords.isEmpty());
        assertTrue(result.placementCategories.isEmpty());
        assertFalse(result.placementCategories.containsValue(PlacementCategory.FOUNDATION));
        assertFalse(result.placementCategories.containsValue(PlacementCategory.LEG));
        assertFalse(result.placementCategories.containsValue(PlacementCategory.INSULATOR));
        assertFalse(result.placementCategories.containsValue(PlacementCategory.WIRE));
    }

    @Test
    void hasBlockingStructureErrorsDetectsSingleStationManualTower() {
        PowerLineGenerationResult result = new PowerLineGenerationResult(singlePoleLine());
        assertTrue(PowerLineGenerator.hasBlockingStructureErrors(invalidManualTower(), result));
        assertFalse(result.warnings.isEmpty());
    }

    private static PowerLineFootprint singlePoleLine() {
        return new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
    }

    private static PowerLineFootprint twoPoleLine() {
        return new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));
    }

    private static PoleDesign invalidManualTower() {
        PoleDesign design = new PoleDesign(INVALID_MANUAL_ID, "Invalid Manual");
        TowerParametricEditor.enableParametricClassic(design, TowerParameterSet.classicDefaults());
        TowerParametricEditor.convertToManual(design);
        TowerStructureDesign structure = new TowerStructureDesign();
        structure.addStation(new TowerStation("s0", 12, 2, 2));
        design.setTowerStructure(structure);
        design.setAttachments(ConductorAttachmentPresets.threePhaseHorizontal(12));
        return design;
    }

    private static PowerLineGenerationResult generate(PowerLineFootprint line, PoleDesign design) {
        line.setPoleDesignId(design.getId());
        PowerLineDesignProject project = new PowerLineDesignProject();
        project.addDesign(design);
        PoleDesignResolver resolver = new PoleDesignResolver(project);
        return PowerLineGeneratorWireTest.createGenerator().generate(
            line,
            flatTerrain(64),
            resolver);
    }

    private static com.plot.core.terrain.TerrainSampler flatTerrain(int y) {
        return new com.plot.core.terrain.TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return y;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return false;
            }
        };
    }
}
