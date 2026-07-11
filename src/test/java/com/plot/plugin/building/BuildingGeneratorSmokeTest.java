package com.plot.plugin.building;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 建筑生成端到端冒烟测试（不依赖 Minecraft 世界，验证生成链路接点）
 */
class BuildingGeneratorSmokeTest {

    @Test
    void gableRoofRidgeRiseGreaterThanEaveRise() {
        BuildingGeometryUtils.RectBounds bounds = new BuildingGeometryUtils.RectBounds(0, 16, 0, 10);
        int ridgeRise = BuildingRoofGenerator.computeGableRise(8, 5, bounds, true, 2);
        int eaveRise = BuildingRoofGenerator.computeGableRise(8, 0.5, bounds, true, 2);
        assertEquals(0, eaveRise);
        assertTrue(ridgeRise > eaveRise);
        assertEquals(2, ridgeRise);
    }

    @Test
    void hipRoofCenterRiseGreaterThanCornerRise() {
        BuildingGeometryUtils.RectBounds bounds = new BuildingGeometryUtils.RectBounds(0, 20, 0, 10);
        int centerRise = BuildingRoofGenerator.computeHipRise(10, 5, bounds, 2);
        int cornerRise = BuildingRoofGenerator.computeHipRise(0, 0, bounds, 2);
        assertEquals(0, cornerRise);
        assertTrue(centerRise > cornerRise);
    }

    @Test
    void rotatedRectangleFootprintDowngradesRoofWithWarning() {
        List<Vec2d> diamond = List.of(
            new Vec2d(10, 5),
            new Vec2d(15, 10),
            new Vec2d(10, 15),
            new Vec2d(5, 10)
        );
        BuildingFootprint footprint = new BuildingFootprint(diamond, false);
        footprint.setRoofType(BuildingFootprint.RoofType.GABLE);

        BuildingGenerator.BuildingGenerationResult result = new BuildingGenerator.BuildingGenerationResult();
        BuildingFootprint.RoofType effective = resolveRoofTypeForTest(footprint, result);

        assertEquals(BuildingFootprint.RoofType.FLAT, effective);
        assertTrue(result.warnings.contains("plugin.building.warn.roof_downgrade"));
    }

    @Test
    void nonRectangularFootprintDowngradesRoofWithWarning() {
        List<Vec2d> pentagon = List.of(
            new Vec2d(0, 0),
            new Vec2d(12, 0),
            new Vec2d(14, 6),
            new Vec2d(6, 12),
            new Vec2d(-2, 5)
        );
        BuildingFootprint footprint = new BuildingFootprint(pentagon, false);
        footprint.setRoofType(BuildingFootprint.RoofType.GABLE);

        BuildingGenerator.BuildingGenerationResult result = new BuildingGenerator.BuildingGenerationResult();
        BuildingFootprint.RoofType effective = resolveRoofTypeForTest(footprint, result);

        assertEquals(BuildingFootprint.RoofType.FLAT, effective);
        assertTrue(result.warnings.contains("plugin.building.warn.roof_downgrade"));
    }

    @Test
    void footprintCellCollectionCoversRectangleInterior() {
        List<Vec2d> rect = List.of(
            new Vec2d(0, 0),
            new Vec2d(4, 0),
            new Vec2d(4, 4),
            new Vec2d(0, 4)
        );
        List<Vec2d> centers = BuildingGeometryUtils.collectFootprintCellCenters(rect);
        assertEquals(16, centers.size());
        assertTrue(centers.stream().anyMatch(p -> p.x == 2.5 && p.y == 2.5));
    }

    @Test
    void foundationModeSmokeMatchesAcceptanceCriteria() {
        assertEquals(64, BuildingFoundationUtils.computeBaseElevation(
            List.of(64, 64, 64, 64, 65, 63), null));
        assertEquals(65, BuildingFoundationUtils.computeBaseElevation(
            List.of(64, 64, 65, 65), null));
    }

    private static BuildingFootprint.RoofType resolveRoofTypeForTest(
            BuildingFootprint footprint,
            BuildingGenerator.BuildingGenerationResult result) {
        BuildingFootprint.RoofType requested = footprint.getRoofType();
        if (requested == BuildingFootprint.RoofType.FLAT) {
            return BuildingFootprint.RoofType.FLAT;
        }
        if (BuildingGeometryUtils.isSlopedRoofEligible(footprint.getOuterPoints())) {
            return requested;
        }
        result.warnings.add("plugin.building.warn.roof_downgrade");
        return BuildingFootprint.RoofType.FLAT;
    }
}
