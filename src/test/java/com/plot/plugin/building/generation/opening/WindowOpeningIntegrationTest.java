package com.plot.plugin.building.generation.opening;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.PlacementReadiness;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationPipeline;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.stage.OpeningGenerationStage;
import com.plot.plugin.building.generation.stage.WallGenerationStage;
import com.plot.plugin.building.golden.GoldenBuildingCaseFactory;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowOpeningIntegrationTest {

    @Test
    void patternWindowsStayOnBuildingPerimeter() {
        BuildingFootprint footprint = GoldenBuildingCaseFactory.rectangle(8, 6, 1, 3, 1);
        footprint.setWindowSpacing(4);
        footprint.setWindowWidth(2);

        BuildingGenerationResult result = generateWallsAndOpenings(footprint);
        Polygon outer = BuildingGeometryUtils.toPolygon(footprint.getOuterPoints());
        String windowId = BuildingGeometryUtils.resolveBlockId(footprint.getWindowMaterial());

        Set<BlockPos> windowBlocks = result.placementRecords.entrySet().stream()
            .filter(e -> windowId.equals(e.getValue().newBlockId))
            .map(e -> e.getKey())
            .collect(java.util.stream.Collectors.toSet());

        assertTrue(windowBlocks.size() > 0, "expected carved windows");
        for (BlockPos pos : windowBlocks) {
            Vec2d canvas = new Vec2d(pos.getX() + 0.5, pos.getZ() + 0.5);
            double dist = Math.abs(outer.getSignedDistance(canvas));
            assertTrue(dist < 1.25, "window at " + pos + " extends outside footprint, dist=" + dist);
        }
    }

    @Test
    void shortWallFaceDoesNotPlaceWindowsOutsideFootprint() {
        BuildingFootprint footprint = new BuildingFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(3, 0),
            new Vec2d(3, 8),
            new Vec2d(0, 8)
        ), false);
        footprint.setWindowSpacing(4);
        footprint.setWindowWidth(2);
        footprint.setWindowHeight(2);
        footprint.setWindowSillHeight(1);

        BuildingGenerationResult result = generateWallsAndOpenings(footprint);
        Polygon outer = BuildingGeometryUtils.toPolygon(footprint.getOuterPoints());
        String windowId = BuildingGeometryUtils.resolveBlockId(footprint.getWindowMaterial());

        for (var entry : result.placementRecords.entrySet()) {
            if (!windowId.equals(entry.getValue().newBlockId)) {
                continue;
            }
            BlockPos pos = entry.getKey();
            Vec2d canvas = new Vec2d(pos.getX() + 0.5, pos.getZ() + 0.5);
            double dist = Math.abs(outer.getSignedDistance(canvas));
            assertTrue(dist < 1.25, "window at " + pos + " outside footprint");
        }
    }

    private static BuildingGenerationResult generateWallsAndOpenings(BuildingFootprint footprint) {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContext.forTesting(
            footprint, IdentityCoordinateService.INSTANCE, noopProjection(), result);
        new BuildingGenerationPipeline(List.of(
            new WallGenerationStage(),
            new OpeningGenerationStage()
        )).generate(context);
        return result;
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
