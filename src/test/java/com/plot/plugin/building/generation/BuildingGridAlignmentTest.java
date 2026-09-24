package com.plot.plugin.building.generation;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.SnapshotCoordinateService;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.generation.resolve.BuildingGenerationContextFactory;
import com.plot.plugin.building.generation.stage.FloorGenerationStage;
import com.plot.plugin.building.generation.stage.OpeningGenerationStage;
import com.plot.plugin.building.generation.stage.RoofGenerationStage;
import com.plot.plugin.building.generation.stage.WallGenerationStage;
import com.plot.plugin.building.golden.GoldenBuildingCaseFactory;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingGridAlignmentTest {

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

    @Test
    void windowsShareColumnsWithWallsAtIdentityScale() {
        assertAlignedColumns(
            GoldenBuildingCaseFactory.rectangle(8, 6, 1, 3, 1),
            IdentityCoordinateService.INSTANCE);
    }

    @Test
    void anisotropicProjectionKeepsAdjacentGridCellsOnAdjacentWorldBlocks() {
        BuildingFootprint footprint = GoldenBuildingCaseFactory.rectangle(12, 10, 1, 3, 1);
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContextFactory.forTesting(
            footprint, anisotropicProjection(1.0, 4.0), NOOP, result);

        new BuildingGenerationPipeline(List.of(
            new WallGenerationStage()
        )).generate(context);

        var plate = context.resolvedFloorPlate(0);
        Vec2d steps = BuildingGridAlignment.blockCellSteps(context.getCanvasScale(), plate.outerPoints());
        java.util.Map<Long, com.plot.api.geometry.Vec2d> canvasByWorld = new java.util.HashMap<>();
        for (var cell : plate.outerCells()) {
            if (!com.plot.plugin.building.generation.massing.InnerOffsetDegradation.isWallMassCell(
                    plate.outerPolygon(), plate.innerPolygon(), cell.center())) {
                continue;
            }
            net.minecraft.util.math.BlockPos col = context.canvasToColumn(cell.center());
            canvasByWorld.put(packColumn(col.getX(), col.getZ()), cell.center());
        }

        for (var entry : canvasByWorld.entrySet()) {
            com.plot.api.geometry.Vec2d center = entry.getValue();
            checkNeighborWorldStep(context, canvasByWorld, center,
                new com.plot.api.geometry.Vec2d(center.x + steps.x, center.y));
            checkNeighborWorldStep(context, canvasByWorld, center,
                new com.plot.api.geometry.Vec2d(center.x, center.y + steps.y));
        }
    }

    @Test
    void windowsShareColumnsWithWallsAtScaledProjection() {
        BuildingFootprint footprint = GoldenBuildingCaseFactory.rectangle(8, 6, 1, 3, 1);
        footprint.setWindowsEnabled(true);
        footprint.setWindowWidth(1);
        footprint.setWindowPierWidth(3);
        assertAlignedColumns(footprint, SnapshotCoordinateService.uniformScale(4.0));
    }

    @Test
    void roofColumnsMatchFloorSlabGridAtScaledProjection() {
        BuildingFootprint footprint = GoldenBuildingCaseFactory.rectangle(8, 6, 1, 3, 1);
        footprint.setRoofType(BuildingFootprint.RoofType.GABLE);
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContextFactory.forTesting(
            footprint, SnapshotCoordinateService.uniformScale(4.0), NOOP, result);

        new BuildingGenerationPipeline(List.of(
            new WallGenerationStage(),
            new FloorGenerationStage(),
            new RoofGenerationStage()
        )).generate(context);

        String floorId = GoldenBuildingCaseFactory.GOLDEN_FLOOR;
        String roofId = context.getRoofBlockId();
        Set<Long> floorColumns = columnsAtMaterial(result, floorId);
        Set<Long> roofColumns = columnsAtMaterial(result, roofId);
        assertFalse(floorColumns.isEmpty());
        assertFalse(roofColumns.isEmpty());
        assertTrue(roofColumns.containsAll(floorColumns),
            "pitched roof columns should align with interior floor slab columns");
    }

    private static void assertAlignedColumns(BuildingFootprint footprint, com.plot.api.world.ICoordinateService coords) {
        footprint.setWindowsEnabled(true);
        footprint.setWindowWidth(1);
        footprint.setWindowPierWidth(3);
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContextFactory.forTesting(
            footprint, coords, NOOP, result);

        new BuildingGenerationPipeline(List.of(
            new WallGenerationStage(),
            new OpeningGenerationStage()
        )).generate(context);

        String wallId = GoldenBuildingCaseFactory.GOLDEN_WALL;
        String windowId = BuildingGeometryUtils.resolveBlockId(footprint.getWindowMaterial());
        Set<Long> wallColumns = columnsAtMaterial(result, wallId);
        Set<Long> windowColumns = columnsAtMaterial(result, windowId);

        assertFalse(windowColumns.isEmpty());
        for (long packed : windowColumns) {
            assertTrue(wallColumns.contains(packed),
                "window column must overlap a wall column, missing=" + unpack(packed));
        }
    }

    private static Set<Long> columnsAtMaterial(BuildingGenerationResult result, String materialId) {
        Set<Long> columns = new HashSet<>();
        result.placementRecords.forEach((pos, record) -> {
            if (materialId.equals(record.newBlockId)) {
                columns.add(packColumn(pos.getX(), pos.getZ()));
            }
        });
        return columns;
    }

    private static long packColumn(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private static String unpack(long packed) {
        int x = (int) (packed >> 32);
        int z = (int) packed;
        return x + "," + z;
    }

    private static void checkNeighborWorldStep(
            BuildingGenerationContext context,
            java.util.Map<Long, com.plot.api.geometry.Vec2d> canvasByWorld,
            com.plot.api.geometry.Vec2d from,
            com.plot.api.geometry.Vec2d neighborCanvas) {
        if (!context.getOuterPolygon().contains(neighborCanvas)) {
            return;
        }
        com.plot.api.geometry.Vec2d aligned = BuildingGridAlignment.snapToBlockCellCenter(
            neighborCanvas, context.getCanvasScale(), context.getOuterPoints());
        net.minecraft.util.math.BlockPos fromCol = context.canvasToColumn(from);
        net.minecraft.util.math.BlockPos toCol = context.canvasToColumn(aligned);
        if (!canvasByWorld.containsKey(packColumn(toCol.getX(), toCol.getZ()))) {
            return;
        }
        int dx = Math.abs(toCol.getX() - fromCol.getX());
        int dz = Math.abs(toCol.getZ() - fromCol.getZ());
        assertTrue(dx + dz == 1,
            "world gap between neighbors " + fromCol + " and " + toCol);
    }

    private static com.plot.api.world.SnapshotCoordinateService anisotropicProjection(
            double xBlocksPerCanvas,
            double zBlocksPerCanvas) {
        float canvasWidth = 800f;
        float canvasHeight = 600f;
        com.plot.api.world.WorldViewBounds bounds = new com.plot.api.world.WorldViewBounds(
            0.0,
            canvasWidth * xBlocksPerCanvas,
            0.0,
            canvasHeight * zBlocksPerCanvas);
        return new com.plot.api.world.SnapshotCoordinateService(
            new com.plot.api.world.WorldProjectionSnapshot(
                bounds,
                256f,
                1f,
                canvasWidth,
                canvasHeight));
    }
}
