package com.plot.plugin.building.generation;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.PlacementReadiness;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.generation.resolve.BuildingGenerationContextFactory;
import com.plot.plugin.building.generation.stage.WallGenerationStage;
import com.plot.plugin.building.golden.GoldenBuildingCaseFactory;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 各外形在画布空间的最大墙环深度不应明显超过配置墙厚（格网半格容差）。
 */
class WallThicknessShapeTest {

    static Stream<GoldenBuildingCaseFactory.Case> cases() {
        return GoldenBuildingCaseFactory.all().stream()
            .filter(c -> c.footprint().getWallThickness() == 1 && !"B07".equals(c.id()));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void wallRingDepthDoesNotExceedConfiguredThickness(GoldenBuildingCaseFactory.Case caseDef) {
        BuildingFootprint footprint = caseDef.footprint();
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContextFactory.forTesting(
            footprint, IdentityCoordinateService.INSTANCE, noopProjection(), result);
        new BuildingGenerationPipeline(List.of(new WallGenerationStage())).generate(context);

        List<Vec2d> outer = footprint.getOuterPoints();
        Polygon outerPolygon = BuildingGeometryUtils.toPolygon(outer);
        int wallThickness = footprint.getWallThickness();
        double maxDepth = 0.0;
        for (BlockPos pos : result.placementRecords.keySet()) {
            Vec2d canvas = new Vec2d(pos.getX() + 0.5, pos.getZ() + 0.5);
            double depth = -outerPolygon.getSignedDistance(canvas);
            maxDepth = Math.max(maxDepth, depth);
        }
        assertTrue(maxDepth <= wallThickness + 0.55,
            caseDef.id() + " max wall depth " + maxDepth + " exceeds thickness " + wallThickness);
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
