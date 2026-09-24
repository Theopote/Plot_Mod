package com.plot.plugin.building.generation;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.SnapshotCoordinateService;
import com.plot.api.world.WorldProjectionSnapshot;
import com.plot.api.world.WorldViewBounds;
import com.plot.plugin.building.generation.massing.FloorPlateGeometryResolver.ResolvedFloorPlate;
import com.plot.plugin.building.generation.massing.InnerOffsetDegradation;
import com.plot.plugin.building.generation.resolve.BuildingGenerationContextFactory;
import com.plot.plugin.building.generation.stage.WallGenerationStage;
import com.plot.plugin.building.golden.GoldenBuildingCaseFactory;
import com.plot.plugin.building.model.BuildingFootprint;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NonUniformProjectionContinuityTest {

    @Test
    void rectangleWallsStayContinuousUnderNonUniformProjection() {
        SnapshotCoordinateService coords = anisotropicProjection(1.0, 4.0);
        BuildingFootprint footprint = new BuildingFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(20, 0),
            new Vec2d(20, 20),
            new Vec2d(0, 20)
        ), true);
        GoldenBuildingCaseFactory.applyDefaults(footprint, 2, 3, 1);

        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContextFactory.forTesting(
            footprint, coords, NOOP, result);
        new BuildingGenerationPipeline(List.of(new WallGenerationStage())).generate(context);

        String wallId = GoldenBuildingCaseFactory.GOLDEN_WALL;
        int floorY = context.getBaseElevation();
        Set<Long> wallColumns = new HashSet<>();
        for (var record : result.placementRecords.values()) {
            if (record.pos.getY() == floorY && wallId.equals(record.newBlockId)) {
                wallColumns.add(pack(record.pos.getX(), record.pos.getZ()));
            }
        }

        ResolvedFloorPlate plate = context.resolvedFloorPlate(0);
        for (var cell : plate.outerCells()) {
            if (!InnerOffsetDegradation.isWallMassCell(
                    plate.outerPolygon(), plate.innerPolygon(), cell.center())) {
                continue;
            }
            BlockPos col = context.canvasToColumn(cell.center());
            assertTrue(wallColumns.contains(pack(col.getX(), col.getZ())),
                "missing wall at " + col + " for canvas " + cell.center());
        }

        assertAdjacentGridNeighborsMapToAdjacentWorldBlocks(context, plate);
    }

    private static void assertAdjacentGridNeighborsMapToAdjacentWorldBlocks(
            BuildingGenerationContext context,
            ResolvedFloorPlate plate) {
        Vec2d steps = BuildingGridAlignment.blockCellSteps(context.getCanvasScale(), plate.outerPoints());
        Map<Long, Vec2d> canvasByWorld = new HashMap<>();
        for (var cell : plate.outerCells()) {
            if (!InnerOffsetDegradation.isWallMassCell(
                    plate.outerPolygon(), plate.innerPolygon(), cell.center())) {
                continue;
            }
            BlockPos col = context.canvasToColumn(cell.center());
            canvasByWorld.put(pack(col.getX(), col.getZ()), cell.center());
        }

        for (var entry : canvasByWorld.entrySet()) {
            Vec2d center = entry.getValue();
            checkNeighbor(context, canvasByWorld, center, new Vec2d(center.x + steps.x, center.y), steps);
            checkNeighbor(context, canvasByWorld, center, new Vec2d(center.x, center.y + steps.y), steps);
        }
    }

    private static void checkNeighbor(
            BuildingGenerationContext context,
            Map<Long, Vec2d> canvasByWorld,
            Vec2d from,
            Vec2d neighborCanvas,
            Vec2d steps) {
        if (!context.getOuterPolygon().contains(neighborCanvas)) {
            return;
        }
        Vec2d aligned = BuildingGridAlignment.snapToBlockCellCenter(
            neighborCanvas, context.getCanvasScale(), context.getOuterPoints());
        if (aligned.distance(neighborCanvas) > Math.max(steps.x, steps.y) * 0.01) {
            return;
        }
        BlockPos fromCol = context.canvasToColumn(from);
        BlockPos toCol = context.canvasToColumn(aligned);
        if (!canvasByWorld.containsKey(pack(toCol.getX(), toCol.getZ()))) {
            return;
        }
        int dx = Math.abs(toCol.getX() - fromCol.getX());
        int dz = Math.abs(toCol.getZ() - fromCol.getZ());
        assertTrue(dx + dz == 1,
            "world gap between neighbors " + fromCol + " and " + toCol
                + " canvas " + from + " -> " + aligned);
    }

    private static SnapshotCoordinateService anisotropicProjection(double xBlocksPerCanvas, double zBlocksPerCanvas) {
        float canvasWidth = 800f;
        float canvasHeight = 600f;
        WorldViewBounds bounds = new WorldViewBounds(
            0.0,
            canvasWidth * xBlocksPerCanvas,
            0.0,
            canvasHeight * zBlocksPerCanvas);
        return new SnapshotCoordinateService(new WorldProjectionSnapshot(
            bounds,
            256f,
            1f,
            canvasWidth,
            canvasHeight));
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private static final IBlockProjectionService NOOP = new IBlockProjectionService() {
        @Override
        public PlacementReadiness checkWorldModificationReadiness() {
            return PlacementReadiness.ok();
        }

        @Override
        public String getBlockIdAt(BlockPos pos) {
            return "minecraft:air";
        }

        @Override
        public boolean setBlockAt(BlockPos pos, String blockId) {
            return false;
        }
    };
}
