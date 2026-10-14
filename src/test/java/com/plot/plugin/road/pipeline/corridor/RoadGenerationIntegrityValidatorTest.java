package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.solid.RoadSolidModel;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadGenerationIntegrityValidatorTest {

    @Test
    void cleanPlanAndSolidsPassValidation() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        Vec2d point = new Vec2d(0, 0);
        plan.surfaceMask().put(
            new ColumnKey(0, 0),
            new SurfaceCell(point, 64, RoadSolidLayer.ROAD, "minecraft:stone"));

        RoadSolidModel solids = new RoadSolidModel();
        solids.add(point, 64, RoadSolidLayer.ROAD, "minecraft:stone");

        RoadGenerationIntegrityValidator.IntegrityReport report =
            RoadGenerationIntegrityValidator.validate(plan, solids, new FlatTerrainSampler(64));

        assertTrue(report.isClean());
        assertEquals(0, report.missingRoadSurfaceCount());
        assertEquals(0, report.unclearedObstructionCount());
    }

    @Test
    void missingSurfaceIsDetected() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        Vec2d point = new Vec2d(0, 0);
        plan.surfaceMask().put(
            new ColumnKey(0, 0),
            new SurfaceCell(point, 64, RoadSolidLayer.ROAD, "minecraft:stone"));

        RoadGenerationIntegrityValidator.IntegrityReport report =
            RoadGenerationIntegrityValidator.validate(plan, new RoadSolidModel(), new FlatTerrainSampler(64));

        assertEquals(1, report.missingRoadSurfaceCount());
    }

    @Test
    void wrongSurfaceMaterialIsDetected() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        Vec2d point = new Vec2d(0, 0);
        plan.surfaceMask().put(
            new ColumnKey(0, 0),
            new SurfaceCell(point, 64, RoadSolidLayer.ROAD, "minecraft:stone"));

        RoadSolidModel solids = new RoadSolidModel();
        solids.add(point, 64, RoadSolidLayer.ROAD, "minecraft:sand");

        RoadGenerationIntegrityValidator.IntegrityReport report =
            RoadGenerationIntegrityValidator.validate(plan, solids, new FlatTerrainSampler(64));

        assertEquals(1, report.missingRoadSurfaceCount());
    }

    @Test
    void unclearedObstructionIsDetected() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        Vec2d point = new Vec2d(0, 0);
        plan.putClearance(
            new ColumnKey(0, 0),
            new ClearanceCell(point, 64, 66, RoadCorridorConstructionMode.OPEN, null));

        TerrainSampler terrain = elevatedColumn(66);

        RoadGenerationIntegrityValidator.IntegrityReport report =
            RoadGenerationIntegrityValidator.validate(plan, new RoadSolidModel(), terrain);

        assertEquals(2, report.unclearedObstructionCount());
    }

    @Test
    void clearedObstructionPassesValidation() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        Vec2d point = new Vec2d(0, 0);
        plan.putClearance(
            new ColumnKey(0, 0),
            new ClearanceCell(point, 64, 66, RoadCorridorConstructionMode.OPEN, null));

        TerrainSampler terrain = elevatedColumn(66);

        RoadSolidModel solids = new RoadSolidModel();
        solids.add(point, 65, RoadSolidLayer.TUNNEL, "minecraft:air", RoadSolidMaterialPriority.AIR);
        solids.add(point, 66, RoadSolidLayer.TUNNEL, "minecraft:air", RoadSolidMaterialPriority.AIR);

        RoadGenerationIntegrityValidator.IntegrityReport report =
            RoadGenerationIntegrityValidator.validate(plan, solids, terrain);

        assertEquals(0, report.unclearedObstructionCount());
    }

    @Test
    void placementStoneBlockDoesNotCountAsClearedAir() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        Vec2d point = new Vec2d(0, 0);
        plan.putClearance(
            new ColumnKey(0, 0),
            new ClearanceCell(point, 64, 66, RoadCorridorConstructionMode.OPEN, null));

        RoadGenerationResult result = new RoadGenerationResult(1.0);
        result.placementRecords.put(
            new BlockPos(0, 65, 0),
            new BlockRecord(new BlockPos(0, 65, 0), "minecraft:sand", "minecraft:stone"));
        result.placementLayers.put(new BlockPos(0, 65, 0), RoadSolidLayer.ROAD);

        RoadGenerationIntegrityValidator.IntegrityReport report =
            RoadGenerationIntegrityValidator.validatePlacements(plan, result, elevatedColumn(66));

        assertEquals(2, report.unclearedObstructionCount());
    }

    @Test
    void placementAirCountsAsClearedObstruction() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        Vec2d point = new Vec2d(0, 0);
        plan.putClearance(
            new ColumnKey(0, 0),
            new ClearanceCell(point, 64, 66, RoadCorridorConstructionMode.OPEN, null));

        RoadGenerationResult result = new RoadGenerationResult(1.0);
        result.placementRecords.put(
            new BlockPos(0, 65, 0),
            new BlockRecord(new BlockPos(0, 65, 0), "minecraft:sand", "minecraft:air"));
        result.placementRecords.put(
            new BlockPos(0, 66, 0),
            new BlockRecord(new BlockPos(0, 66, 0), "minecraft:sand", "minecraft:air"));

        RoadGenerationIntegrityValidator.IntegrityReport report =
            RoadGenerationIntegrityValidator.validatePlacements(plan, result, elevatedColumn(66));

        assertEquals(0, report.unclearedObstructionCount());
    }

    @Test
    void tunnelSurfaceMissingIsDetectedInPlacements() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        Vec2d point = new Vec2d(0, 0);
        plan.surfaceMask().put(
            new ColumnKey(0, 0),
            new SurfaceCell(point, 64, RoadSolidLayer.ROAD, "minecraft:stone"));
        plan.putClearance(
            new ColumnKey(0, 0),
            new ClearanceCell(point, 64, 70, RoadCorridorConstructionMode.TUNNEL, null));

        RoadGenerationResult result = new RoadGenerationResult(1.0);

        RoadGenerationIntegrityValidator.IntegrityReport report =
            RoadGenerationIntegrityValidator.validatePlacements(plan, result, new FlatTerrainSampler(64));

        assertEquals(1, report.missingRoadSurfaceCount());
    }

    @Test
    void blockPosKeysDoNotCollideAcrossDifferentYAndZ() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        Vec2d point = new Vec2d(0, 0);
        plan.putClearance(
            new ColumnKey(0, 0),
            new ClearanceCell(point, 64, 65, RoadCorridorConstructionMode.OPEN, null));

        RoadGenerationResult result = new RoadGenerationResult(1.0);
        BlockPos unclearedPos = new BlockPos(0, 65, 0);
        BlockPos unrelatedPos = new BlockPos(0, 1, 65536);
        result.placementRecords.put(
            unclearedPos,
            new BlockRecord(unclearedPos, "minecraft:sand", "minecraft:stone"));
        result.placementRecords.put(
            unrelatedPos,
            new BlockRecord(unrelatedPos, "minecraft:stone", "minecraft:stone"));

        RoadGenerationIntegrityValidator.IntegrityReport report =
            RoadGenerationIntegrityValidator.validatePlacements(plan, result, elevatedColumn(66));

        // y=65 has non-air stone (not structural clearance); only clearTop range is checked
        assertEquals(1, report.unclearedObstructionCount());
    }

    @Test
    void structureInteriorSlopeCellIsCounted() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        Vec2d point = new Vec2d(15, 0);
        ColumnKey key = new ColumnKey(15, 0);
        plan.putClearance(key, new ClearanceCell(
            point, 64, 80, RoadCorridorConstructionMode.BRIDGE, RoadConstructionType.BRIDGE));
        plan.slopeCellOrCreate(key, point).mergeFill(60, "minecraft:dirt");

        RoadGenerationIntegrityValidator.IntegrityReport report =
            RoadGenerationIntegrityValidator.validate(plan, new RoadSolidModel(), new FlatTerrainSampler(50));

        assertEquals(1, report.structureInteriorSlopeCellCount());
    }

    @Test
    void slopeLateralGapIsDetected() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        Vec2d point = new Vec2d(0, 0);
        plan.slopeCellOrCreate(new ColumnKey(10, 5), point).mergeCut(64, "minecraft:stone");
        plan.slopeCellOrCreate(new ColumnKey(11, 5), point).mergeCut(64, "minecraft:stone");
        plan.slopeCellOrCreate(new ColumnKey(13, 5), point).mergeCut(64, "minecraft:stone");
        plan.slopeCellOrCreate(new ColumnKey(14, 5), point).mergeCut(64, "minecraft:stone");

        RoadGenerationIntegrityValidator.IntegrityReport report =
            RoadGenerationIntegrityValidator.validate(plan, new RoadSolidModel(), new FlatTerrainSampler(64));

        assertTrue(report.brokenSlopeSurfaceCount() > 0);
        assertTrue(report.hasUnresolvedSlopeConstruction());
    }

    private static TerrainSampler elevatedColumn(int topY) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return topY;
            }

            @Override
            public int sampleColumnTopY(Vec2d planPoint) {
                return topY;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return y <= topY;
            }
        };
    }
}
