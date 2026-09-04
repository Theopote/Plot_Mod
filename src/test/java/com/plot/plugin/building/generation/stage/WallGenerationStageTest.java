package com.plot.plugin.building.generation.stage;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.WorldViewBounds;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationPipeline;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.stage.FloorGenerationStage;
import com.plot.plugin.building.golden.GoldenBuildingCaseFactory;
import com.plot.plugin.building.model.BuildingFootprint;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WallGenerationStageTest {

    @Test
    void innerOffsetFailureStillGeneratesSolidWallMass() {
        BuildingFootprint footprint = GoldenBuildingCaseFactory.b07NarrowCorridor().footprint();
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContext.forTesting(
            footprint,
            stubCoordinates(),
            stubProjection(),
            result);

        new BuildingGenerationPipeline(List.of(new WallGenerationStage())).generate(context);

        assertTrue(result.warnings.contains("plugin.building.warn.inner_offset_failed"));
        assertTrue(result.placementRecords.size() > 0, "expected wall blocks when inner offset fails");
    }

    @Test
    void innerOffsetFailureSkipsInteriorFloorOnly() {
        BuildingFootprint footprint = GoldenBuildingCaseFactory.b07NarrowCorridor().footprint();
        BuildingGenerationResult wallResult = new BuildingGenerationResult();
        BuildingGenerationContext wallContext = BuildingGenerationContext.forTesting(
            footprint, stubCoordinates(), stubProjection(), wallResult);
        new BuildingGenerationPipeline(List.of(new WallGenerationStage())).generate(wallContext);

        BuildingGenerationResult floorResult = new BuildingGenerationResult();
        BuildingGenerationContext floorContext = BuildingGenerationContext.forTesting(
            footprint, stubCoordinates(), stubProjection(), floorResult);
        new BuildingGenerationPipeline(List.of(new FloorGenerationStage()))
            .generate(floorContext);

        assertTrue(wallResult.placementRecords.size() > 0, "walls should still generate");
        assertEquals(0, floorResult.placementRecords.size(), "interior floor should be skipped");
    }

    private static ICoordinateService stubCoordinates() {
        return new ICoordinateService() {
            @Override
            public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
                return canvasPos;
            }

            @Override
            public WorldViewBounds getMinecraftWorldViewBounds() {
                return new WorldViewBounds(-512, 512, -512, 512);
            }
        };
    }

    private static IBlockProjectionService stubProjection() {
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
