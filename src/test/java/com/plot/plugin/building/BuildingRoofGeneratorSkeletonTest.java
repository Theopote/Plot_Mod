package com.plot.plugin.building;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.polygon.StraightSkeleton;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationPipeline;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.stage.RoofGenerationStage;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.EnvelopeSpec;
import com.plot.plugin.building.model.spec.FacadeSpec;
import com.plot.plugin.building.model.spec.FootprintSpec;
import com.plot.plugin.building.model.spec.FoundationSpec;
import com.plot.plugin.building.model.spec.MassingSpec;
import com.plot.plugin.building.model.spec.RoofSpec;
import com.plot.plugin.building.model.spec.WindowPatternSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingRoofGeneratorSkeletonTest {

    private static final List<Vec2d> L_SHAPE = List.of(
        new Vec2d(0, 0),
        new Vec2d(10, 0),
        new Vec2d(10, 4),
        new Vec2d(4, 4),
        new Vec2d(4, 10),
        new Vec2d(0, 10)
    );

    @Test
    void hipRoofOnLShapeDoesNotDowngrade() {
        BuildingFootprint footprint = new BuildingFootprint(L_SHAPE, false);
        footprint.setRoofType(BuildingFootprint.RoofType.HIP);

        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingFootprint.RoofType effective = RoofGenerationStage.resolveRoofType(
            BuildingDefinition.fromFootprint(footprint), footprint.getOuterPoints(), result);

        assertEquals(BuildingFootprint.RoofType.HIP, effective);
        assertFalse(result.warnings.contains("plugin.building.warn.roof_downgrade"));
    }

    @Test
    void hipRoofOnLShapeGeneratesBlocksAboveEaves() {
        int blocks = countRoofBlocks(L_SHAPE, BuildingFootprint.RoofType.HIP, 2);
        assertTrue(blocks > 0);
    }

    @Test
    void rectangleHipMatchesLegacyHeightField() {
        List<Vec2d> rect = List.of(
            new Vec2d(0, 0),
            new Vec2d(20, 0),
            new Vec2d(20, 10),
            new Vec2d(0, 10)
        );
        StraightSkeleton.Result skeleton = StraightSkeleton.compute(rect);
        BuildingGeometryUtils.RectBounds bounds = BuildingGeometryUtils.normalizedRectBounds(rect);
        Vec2d center = new Vec2d(10.5, 5.5);
        int legacy = BuildingRoofGenerator.computeHipRise(center.x, center.y, bounds, 2);
        int skeletonRise = BuildingRoofGenerator.computeHipRise(center, skeleton, bounds, 2);
        assertEquals(legacy, skeletonRise);
    }

    @Test
    void narrowCorridorDowngradesSlopedRoof() {
        List<Vec2d> corridor = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 1),
            new Vec2d(0, 1)
        );
        BuildingFootprint footprint = new BuildingFootprint(corridor, false);
        footprint.setRoofType(BuildingFootprint.RoofType.HIP);

        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingFootprint.RoofType effective = RoofGenerationStage.resolveRoofType(
            BuildingDefinition.fromFootprint(footprint), footprint.getOuterPoints(), result);

        assertEquals(BuildingFootprint.RoofType.FLAT, effective);
        assertTrue(result.warnings.contains("plugin.building.warn.roof_downgrade"));
    }

    private static int countRoofBlocks(
            List<Vec2d> footprint,
            BuildingFootprint.RoofType roofType,
            int pitch) {
        BuildingDefinition definition = new BuildingDefinition(
            new FootprintSpec("test", "Test", footprint, false),
            MassingSpec.create(1, 3, footprint, List.of()),
            new EnvelopeSpec(1, null, null),
            new FacadeSpec(new WindowPatternSpec(0, 1, 2, 1), List.of(), List.of()),
            new RoofSpec(roofType, pitch, "minecraft:stone_bricks"),
            new FoundationSpec(null, 64)
        );
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContext.forTesting(
            definition, stubCoordinates(), stubProjection(), result);
        new BuildingGenerationPipeline(List.of(new RoofGenerationStage())).generate(context);
        return result.placementRecords.size();
    }

    private static com.plot.api.world.ICoordinateService stubCoordinates() {
        return new com.plot.api.world.ICoordinateService() {
            @Override
            public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
                return canvasPos;
            }

            @Override
            public com.plot.api.world.WorldViewBounds getMinecraftWorldViewBounds() {
                return new com.plot.api.world.WorldViewBounds(-512, 512, -512, 512);
            }
        };
    }

    private static com.plot.api.world.IBlockProjectionService stubProjection() {
        return new com.plot.api.world.IBlockProjectionService() {
            @Override
            public com.plot.api.world.PlacementReadiness checkWorldModificationReadiness() {
                return com.plot.api.world.PlacementReadiness.ok();
            }

            @Override
            public String getBlockIdAt(net.minecraft.util.math.BlockPos pos) {
                return "minecraft:air";
            }

            @Override
            public boolean setBlockAt(net.minecraft.util.math.BlockPos pos, String blockId) {
                return false;
            }
        };
    }
}
