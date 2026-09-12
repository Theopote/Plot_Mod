package com.plot.plugin.powerline.engineering.validation;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.powerline.PoleFrame;
import com.plot.plugin.powerline.TerrainTestFixtures;
import com.plot.plugin.powerline.PolePlacement;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.structure.TowerStation;
import com.plot.plugin.powerline.design.structure.TowerStructureDesign;
import com.plot.plugin.powerline.engineering.EngineeringRuleIds;
import com.plot.plugin.powerline.engineering.PowerLineIssueSeverity;
import com.plot.plugin.powerline.geometry.PowerLineGeometryModel;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerStructureValidationCheckTest {

    @Test
    void invalidStructureAddsBlockingError() {
        PoleDesign design = brokenDesign();
        PowerLineGeometryModel geometry = geometryWithDesign(design);
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));

        PowerLineValidationReport report = PowerLineValidator.validate(
            geometry,
            flatTerrain(),
            line);

        assertFalse(report.passes());
        assertTrue(report.getIssues().stream()
            .anyMatch(issue -> EngineeringRuleIds.TOWER_STRUCTURE_INVALID.equals(issue.ruleId())
                && issue.severity() == PowerLineIssueSeverity.ERROR));
    }

    private static PoleDesign brokenDesign() {
        PoleDesign design = new PoleDesign("broken", "Broken");
        TowerStructureDesign structure = new TowerStructureDesign();
        structure.addStation(new TowerStation(null, 10.0, 2.0, 2.0));
        design.setTowerStructure(structure);
        return design;
    }

    private static PowerLineGeometryModel geometryWithDesign(PoleDesign design) {
        PowerLineGeometryModel geometry = new PowerLineGeometryModel();
        geometry.setPlacements(List.of(new PolePlacement(
            new Vec2d(0, 0),
            PoleFrame.fromPole(new Vec2d(0, 0), new Vec2d(1, 0), 64),
            design,
            List.of(),
            74,
            false)));
        return geometry;
    }

    private static TerrainSampler flatTerrain() {
        return TerrainTestFixtures.flatTerrain(64);
    }
}
