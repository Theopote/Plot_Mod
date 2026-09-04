package com.plot.plugin.building;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.stage.RoofGenerationStage;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void rotatedRectangleSupportsHipRoofWithSkeleton() {
        List<Vec2d> diamond = List.of(
            new Vec2d(10, 5),
            new Vec2d(15, 10),
            new Vec2d(10, 15),
            new Vec2d(5, 10)
        );
        BuildingFootprint footprint = new BuildingFootprint(diamond, false);
        footprint.setRoofType(BuildingFootprint.RoofType.HIP);

        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingFootprint.RoofType effective = RoofGenerationStage.resolveRoofType(
            BuildingDefinition.fromFootprint(footprint), footprint.getOuterPoints(), result);

        assertEquals(BuildingFootprint.RoofType.HIP, effective);
        assertFalse(result.warnings.contains("plugin.building.warn.roof_downgrade"));
    }

    @Test
    void concaveFootprintSupportsHipRoofWhenSkeletonHasHeight() {
        List<Vec2d> lShape = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 4),
            new Vec2d(4, 4),
            new Vec2d(4, 10),
            new Vec2d(0, 10)
        );
        BuildingFootprint footprint = new BuildingFootprint(lShape, false);
        footprint.setRoofType(BuildingFootprint.RoofType.HIP);

        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingFootprint.RoofType effective = RoofGenerationStage.resolveRoofType(
            BuildingDefinition.fromFootprint(footprint), footprint.getOuterPoints(), result);

        assertEquals(BuildingFootprint.RoofType.HIP, effective);
        assertFalse(result.warnings.contains("plugin.building.warn.roof_downgrade"));
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
}
