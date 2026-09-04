package com.plot.plugin.building.generation;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.WorldViewBounds;
import com.plot.plugin.building.generation.massing.FloorPlateGeometryResolver;
import com.plot.plugin.building.generation.stage.FloorGenerationStage;
import com.plot.plugin.building.generation.stage.OpeningGenerationStage;
import com.plot.plugin.building.generation.stage.WallGenerationStage;
import com.plot.plugin.building.golden.GoldenBuildingCaseFactory;
import com.plot.plugin.building.golden.GoldenBuildingHarness;
import com.plot.plugin.building.golden.GoldenBuildingMetrics;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.spec.AccessorySpec;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.EnvelopeSpec;
import com.plot.plugin.building.model.spec.FacadeSpec;
import com.plot.plugin.building.model.spec.FloorPlateSpec;
import com.plot.plugin.building.model.spec.FoundationSpec;
import com.plot.plugin.building.model.spec.FootprintSpec;
import com.plot.plugin.building.model.spec.MassingSpec;
import com.plot.plugin.building.model.spec.RoofSpec;
import com.plot.plugin.building.model.spec.WindowPatternSpec;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * inner offset 降级策略的跨阶段集成测试。
 */
class InnerOffsetDegradationIntegrationTest {

    private static final List<Vec2d> BASE = List.of(
        new Vec2d(0, 0),
        new Vec2d(8, 0),
        new Vec2d(8, 8),
        new Vec2d(0, 8)
    );

    @Test
    void b07FullPipelineProducesSolidWallsWithoutInteriorFloor() {
        GoldenBuildingMetrics metrics = GoldenBuildingHarness.generate(
            GoldenBuildingCaseFactory.b07NarrowCorridor().footprint());

        assertTrue(metrics.wallBlocks() > 0);
        assertEquals(0, metrics.floorBlocks());
        assertTrue(metrics.warnings().contains("plugin.building.warn.inner_offset_failed"));
    }

    @Test
    void perPlateInnerOffsetFailureStillGeneratesUpperFloorWallsAndWarning() {
        List<Vec2d> tinyUpper = List.of(
            new Vec2d(3, 3),
            new Vec2d(5, 3),
            new Vec2d(5, 5),
            new Vec2d(3, 5)
        );
        BuildingDefinition definition = new BuildingDefinition(
            new FootprintSpec("test", "Test", BASE, true),
            MassingSpec.create(4, 3, BASE, List.of(
                FloorPlateSpec.of(0, 1, BASE),
                FloorPlateSpec.of(2, 3, tinyUpper))),
            new EnvelopeSpec(3, null, null),
            new FacadeSpec(new WindowPatternSpec(0, 1, 2, 1), List.of(), List.of()),
            new RoofSpec(BuildingFootprint.RoofType.FLAT, 1, null),
            new FoundationSpec(null, 64),
            AccessorySpec.none());

        assertTrue(FloorPlateGeometryResolver.resolve(
            definition.massing().plateForFloor(0), 3).hasInteriorSpace());
        assertFalse(FloorPlateGeometryResolver.resolve(
            definition.massing().plateForFloor(2), 3).hasInteriorSpace());

        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContext.forTesting(
            definition, stubCoordinates(), stubProjection(), result);

        new BuildingGenerationPipeline(List.of(
            new WallGenerationStage(),
            new FloorGenerationStage()
        )).generate(context);

        assertTrue(result.warnings.contains("plugin.building.warn.inner_offset_failed"));
        assertTrue(result.placementRecords.size() > 0, "upper floors with failed inner offset must still have walls");
    }

    @Test
    void openingStageCarvesPatternWindowsOnSolidWallMass() {
        BuildingFootprint footprint = GoldenBuildingCaseFactory.b07NarrowCorridor().footprint();

        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContext.forTesting(
            footprint, stubCoordinates(), stubProjection(), result);

        new BuildingGenerationPipeline(List.of(
            new WallGenerationStage(),
            new OpeningGenerationStage()
        )).generate(context);

        long airBlocks = result.placementRecords.values().stream()
            .filter(record -> "minecraft:air".equals(record.newBlockId))
            .count();
        assertTrue(airBlocks > 0, "pattern windows should carve solid wall mass");
        assertTrue(result.placementRecords.size() > airBlocks, "walls must exist before openings");
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
