package com.plot.plugin.building.generation;

import com.plot.api.world.ICoordinateService;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.PlacementReadiness;
import com.plot.plugin.building.generation.resolve.BuildingGenerationContextFactory;
import com.plot.plugin.building.generation.stage.WallGenerationStage;
import com.plot.plugin.building.golden.GoldenBuildingCaseFactory;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.test.scale.ScaleInvarianceProjections;
import com.plot.test.world.CoordinateTestFixtures;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 世界方块空间墙厚测量：沿南缘向内计数，避免侧墙列干扰。
 */
class WallThicknessMeasurementTest {

    @Test
    void axisAlignedRectangleHonorsWallThicknessOne() {
        BuildingFootprint footprint = GoldenBuildingCaseFactory.rectangle(10, 6, 1, 3, 1);
        int measured = southFaceWallThickness(generateWalls(footprint, IdentityCoordinateService.INSTANCE));
        assertEquals(1, measured, "axis-aligned rectangle should be exactly 1 block thick");
    }

    @Test
    void zoomedInProjectionHonorsWallThicknessOne() {
        // 1 canvas unit = 0.25 world blocks → uniformBlocksToCanvas(1) = 4.0
        ICoordinateService zoomedIn = CoordinateTestFixtures.uniformScale(0.25);
        BuildingFootprint footprint = GoldenBuildingCaseFactory.rectangle(10, 6, 1, 3, 1);
        int measured = southFaceWallThickness(generateWalls(footprint, zoomedIn));
        assertEquals(1, measured, "zoomed-in projection must still place 1-block-thick walls");
    }

    @Test
    void farProjectionHonorsWallThicknessOne() {
        BuildingFootprint footprint = GoldenBuildingCaseFactory.rectangle(10, 6, 1, 3, 1);
        int measured = southFaceWallThickness(generateWalls(footprint, ScaleInvarianceProjections.FAR));
        assertEquals(1, measured, "far projection must still place 1-block-thick walls");
    }

    @Test
    void wallThicknessTwoProjectsToTwoBlocks() {
        BuildingFootprint footprint = GoldenBuildingCaseFactory.rectangle(10, 6, 1, 3, 2);
        int measured = southFaceWallThickness(generateWalls(footprint, IdentityCoordinateService.INSTANCE));
        assertEquals(2, measured);
    }

    private static BuildingGenerationResult generateWalls(
            BuildingFootprint footprint,
            ICoordinateService coordinates) {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContextFactory.forTesting(
            footprint, coordinates, noopProjection(), result);
        new BuildingGenerationPipeline(List.of(new WallGenerationStage())).generate(context);
        return result;
    }

    /** 沿 +Z 从南缘（min Z）在一条南墙 X 上量连续墙列数。 */
    static int southFaceWallThickness(BuildingGenerationResult result) {
        Set<BlockPos> walls = result.placementRecords.keySet();
        if (walls.isEmpty()) {
            return 0;
        }
        int baseY = walls.stream().mapToInt(BlockPos::getY).min().orElse(0);
        int southZ = walls.stream().mapToInt(BlockPos::getZ).min().orElse(0);
        List<Integer> southXs = walls.stream()
            .filter(pos -> pos.getY() == baseY && pos.getZ() == southZ)
            .map(BlockPos::getX)
            .sorted()
            .toList();
        if (southXs.isEmpty()) {
            return 0;
        }
        int midX = southXs.get(southXs.size() / 2);
        int thickness = 0;
        for (int z = southZ; walls.contains(new BlockPos(midX, baseY, z)); z++) {
            thickness++;
        }
        return thickness;
    }

    private static IBlockProjectionService noopProjection() {
        return new IBlockProjectionService() {
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
}
