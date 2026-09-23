package com.plot.plugin.building.generation;

import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.SnapshotCoordinateService;
import com.plot.plugin.building.generation.massing.InnerOffsetDegradation;
import com.plot.plugin.building.generation.resolve.BuildingGenerationContextFactory;
import com.plot.plugin.building.generation.stage.FloorGenerationStage;
import com.plot.plugin.building.generation.stage.RoofGenerationStage;
import com.plot.plugin.building.generation.stage.WallGenerationStage;
import com.plot.plugin.building.golden.GoldenBuildingCaseFactory;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RoofWallCoverageTest {

    @Test
    void flatRoofCoversExteriorWallColumns() {
        assertRoofCoversWalls(
            GoldenBuildingCaseFactory.rectangle(8, 6, 1, 3, 1),
            BuildingFootprint.RoofType.FLAT,
            IdentityCoordinateService.INSTANCE);
    }

    @Test
    void gableRoofCoversExteriorWallColumnsAtScaledProjection() {
        BuildingFootprint footprint = GoldenBuildingCaseFactory.rectangle(8, 6, 1, 3, 1);
        footprint.setRoofType(BuildingFootprint.RoofType.GABLE);
        footprint.setRoofPitchRatio(2);
        assertRoofCoversWalls(footprint, BuildingFootprint.RoofType.GABLE,
            SnapshotCoordinateService.uniformScale(4.0));
    }

    private static void assertRoofCoversWalls(
            BuildingFootprint footprint,
            BuildingFootprint.RoofType roofType,
            com.plot.api.world.ICoordinateService coords) {
        footprint.setRoofType(roofType);

        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContextFactory.forTesting(
            footprint, coords, NOOP, result);

        new BuildingGenerationPipeline(List.of(
            new WallGenerationStage(),
            new FloorGenerationStage(),
            new RoofGenerationStage()
        )).generate(context);

        String wallId = GoldenBuildingCaseFactory.GOLDEN_WALL;
        String roofId = context.getRoofBlockId();
        int topRoofY = context.getTopFloorY();

        var plate = context.resolvedFloorPlate(0);

        for (var cell : plate.outerCells()) {
            if (!InnerOffsetDegradation.isWallMassCell(
                    plate.outerPolygon(), plate.innerPolygon(), cell.center())) {
                continue;
            }
            BlockPos wallColumn = context.canvasToColumn(cell.center());
            int wallTopY = context.getBaseElevation() + footprint.getFloorHeight() - 1;
            BlockPos wallPos = new BlockPos(wallColumn.getX(), wallTopY, wallColumn.getZ());
            var wallRecord = result.placementRecords.get(wallPos);
            if (wallRecord == null || !wallId.equals(wallRecord.newBlockId)) {
                continue;
            }
            BlockPos roofPos = new BlockPos(wallColumn.getX(), topRoofY, wallColumn.getZ());
            var roofRecord = result.placementRecords.get(roofPos);
            assertTrue(roofRecord != null && roofId.equals(roofRecord.newBlockId),
                "missing roof above exterior wall at " + roofPos);
        }
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
